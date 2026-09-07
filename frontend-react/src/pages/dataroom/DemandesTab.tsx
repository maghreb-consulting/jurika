import { useCallback, useEffect, useMemo, useState } from 'react';
import { MessageSquare, Plus, Send, TicketPlus } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Drawer } from '../../components/ui/Drawer';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { dataroomService } from '../../services/dataroom.service';
import { ticketService } from '../../services/ticket.service';
import { formatDateTime } from '../../lib/date';
import { extractError } from '../../lib/api';
import { requiredMsg } from '../../lib/formValidation';
import type {
  DemandeStatut,
  DemandeSummary,
} from '../../types/dataroom';
import { DEMANDE_STATUT_LABELS } from '../../types/dataroom';
import type { TicketType, TicketPriorite } from '../../types/ticket';
import { TICKET_TYPE_LABELS, TYPES_AUTO_DOSSIER } from '../../types/ticket';
import type { Role } from '../../types/auth';

// Types de ticket creables depuis une demande : on exclut CREATION/IMPORT
// (qui exigent la creation auto d'un dossier -> companyInfo), car la demande
// porte deja sur un dossier existant.
const TICKET_TYPE_OPTIONS = (Object.keys(TICKET_TYPE_LABELS) as TicketType[])
  .filter((t) => !TYPES_AUTO_DOSSIER.includes(t))
  .map((t) => ({ value: t, label: TICKET_TYPE_LABELS[t] }));

const PRIORITE_OPTIONS: { value: TicketPriorite; label: string }[] = [
  { value: 'BASSE', label: 'Basse' },
  { value: 'NORMALE', label: 'Normale' },
  { value: 'HAUTE', label: 'Haute' },
  { value: 'URGENTE', label: 'Urgente' },
];

interface Props {
  dossierId: string;
  role: Role | null;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : plus
   * aucune NOUVELLE demande ni requete. Les echanges deja ouverts restent
   * consultables et cloturables (le backend n'interdit que la creation).
   */
  readOnly?: boolean;
  readOnlyStatut?: string | null;
}

const STATUT_VARIANTS: Record<DemandeStatut, 'info' | 'warning' | 'success'> = {
  NON_TRAITEE: 'info',
  EN_COURS: 'warning',
  TRAITEE: 'success',
};

const STATUT_OPTIONS = (
  Object.entries(DEMANDE_STATUT_LABELS) as [DemandeStatut, string][]
).map(([value, label]) => ({ value, label }));

export function DemandesTab({
  dossierId,
  role,
  readOnly = false,
}: Props) {
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newOpen, setNewOpen] = useState(false);

  // L'employe n'envoie PAS de « demande » au client : il envoie des REQUETES
  // (section « Mes requetes au client »). Seul le CLIENT cree des demandes
  // (client -> employe). L'employe garde la gestion (canEdit) des demandes recues.
  // §A — societe archivee : plus de nouvelle demande (le backend renvoie 400).
  const canCreate = role === 'CLIENT' && !readOnly;
  const canEdit = role === 'EMPLOYE';

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await dataroomService.listDemandesByDossier(dossierId);
      setDemandes(data);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    load();
  }, [load]);

  const counts = useMemo(() => {
    return demandes.reduce(
      (acc, d) => {
        acc[d.statut] = (acc[d.statut] ?? 0) + 1;
        return acc;
      },
      { NON_TRAITEE: 0, EN_COURS: 0, TRAITEE: 0 } as Record<DemandeStatut, number>,
    );
  }, [demandes]);

  return (
    <div className="space-y-4">
      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      <Card className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-accent/10 p-2 text-accent">
            <MessageSquare className="h-4 w-4" />
          </div>
          <div className="flex items-center gap-2 text-sm text-fg-muted">
            <Badge variant="info">{counts.NON_TRAITEE} non traitee{counts.NON_TRAITEE > 1 ? 's' : ''}</Badge>
            <Badge variant="warning">{counts.EN_COURS} en cours</Badge>
            <Badge variant="success">{counts.TRAITEE} traitee{counts.TRAITEE > 1 ? 's' : ''}</Badge>
          </div>
        </div>
        {canCreate && (
          <Button size="sm" onClick={() => setNewOpen(true)}>
            <Plus className="mr-1 h-4 w-4" />
            Nouvelle demande
          </Button>
        )}
      </Card>

      <Card>
        <ul className="divide-y divide-border">
          {loading && demandes.length === 0 && (
            <li className="flex justify-center py-6">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-border border-t-indigo-600" />
            </li>
          )}
          {!loading && demandes.length === 0 && (
            <li className="px-5 py-8 text-center text-sm text-fg-subtle">
              Aucune demande pour ce dossier.
            </li>
          )}
          {demandes.map((demande) => (
            <DemandeRow
              key={demande.id}
              demande={demande}
              editable={canEdit}
              onChanged={load}
            />
          ))}
        </ul>
      </Card>

      {/* Lot 1 (2026-09-04) — les requetes de l'employe AU client ont leur propre
          section dans la Data Room : elles ne sont plus imbriquees ici. */}

      <NewDemandeDrawer
        open={newOpen}
        onClose={() => setNewOpen(false)}
        dossierId={dossierId}
        onCreated={async () => {
          setNewOpen(false);
          await load();
        }}
      />
    </div>
  );
}

interface DemandeRowProps {
  demande: DemandeSummary;
  editable: boolean;
  onChanged: () => Promise<void> | void;
}

function DemandeRow({ demande, editable, onChanged }: DemandeRowProps) {
  const [statut, setStatut] = useState<DemandeStatut>(demande.statut);
  const [noteInterne, setNoteInterne] = useState(demande.noteInterne ?? '');
  const [ticketId, setTicketId] = useState(demande.ticketId ?? '');
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [ticketDrawerOpen, setTicketDrawerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setSaving(true);
    setError(null);
    try {
      await dataroomService.updateDemande(demande.id, {
        statut,
        noteInterne: noteInterne || undefined,
        ticketId: ticketId.trim() || undefined,
      });
      setEditing(false);
      await onChanged();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <li className="px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-fg">{demande.sujet}</p>
            <Badge variant={STATUT_VARIANTS[demande.statut]}>
              {DEMANDE_STATUT_LABELS[demande.statut]}
            </Badge>
            {demande.ticketId && (
              <Badge variant="neutral" className="text-[10px]">
                Ticket lie
              </Badge>
            )}
          </div>
          {demande.description && (
            <p className="mt-1 whitespace-pre-wrap text-sm text-fg-muted">
              {demande.description}
            </p>
          )}
          <p className="mt-1 text-xs text-fg-subtle">
            Soumise le {formatDateTime(demande.createdAt)}
            {demande.traiteAt ? ` • Traitee le ${formatDateTime(demande.traiteAt)}` : ''}
          </p>
          {demande.noteInterne && !editing && (
            <div className="mt-2 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-xs text-fg-muted">
              <span className="font-semibold">Note interne :</span> {demande.noteInterne}
            </div>
          )}
        </div>
        {editable && !editing && (
          <div className="flex shrink-0 items-center gap-2">
            {!demande.ticketId && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setTicketDrawerOpen(true)}
                title="Créer un ticket à partir de cette demande (optionnel)"
              >
                <TicketPlus className="mr-1 h-4 w-4" />
                Créer un ticket
              </Button>
            )}
            <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
              Modifier
            </Button>
          </div>
        )}
      </div>

      {editable && editing && (
        <div className="mt-3 grid gap-3 rounded-lg border border-border bg-bg-overlay p-3 md:grid-cols-2">
          <Select
            label="Statut"
            value={statut}
            onChange={(e) => setStatut(e.target.value as DemandeStatut)}
            options={STATUT_OPTIONS}
          />
          <TextField
            label="Ticket lie (optionnel)"
            value={ticketId}
            onChange={(e) => setTicketId(e.target.value)}
            placeholder="UUID du ticket"
          />
          <div className="flex flex-col gap-1 md:col-span-2">
            <label className="text-sm font-medium text-fg-muted">
              Note interne
            </label>
            <textarea
              rows={3}
              value={noteInterne}
              onChange={(e) => setNoteInterne(e.target.value)}
              className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            />
          </div>
          {error && (
            <div className="md:col-span-2 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
              {error}
            </div>
          )}
          <div className="flex justify-end gap-2 md:col-span-2">
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => {
                setEditing(false);
                setStatut(demande.statut);
                setNoteInterne(demande.noteInterne ?? '');
                setTicketId(demande.ticketId ?? '');
              }}
            >
              Annuler
            </Button>
            <Button type="button" size="sm" loading={saving} onClick={save}>
              Enregistrer
            </Button>
          </div>
        </div>
      )}

      <CreateTicketDrawer
        open={ticketDrawerOpen}
        onClose={() => setTicketDrawerOpen(false)}
        demande={demande}
        onCreated={async (newTicketId) => {
          // Lie le ticket a la demande + passe en "En cours" si elle etait non traitee.
          await dataroomService.updateDemande(demande.id, {
            ticketId: newTicketId,
            statut: demande.statut === 'NON_TRAITEE' ? 'EN_COURS' : demande.statut,
          });
          setTicketDrawerOpen(false);
          await onChanged();
        }}
      />
    </li>
  );
}

interface CreateTicketDrawerProps {
  open: boolean;
  onClose: () => void;
  demande: DemandeSummary;
  onCreated: (ticketId: string) => Promise<void> | void;
}

/**
 * 2026-07-04 — Création OPTIONNELLE d'un ticket à partir d'une demande client.
 * Pré-remplit titre/description depuis la demande ; l'employé choisit le type de
 * workflow (Modification, Dissolution, ...) et la priorité. Le dossier est celui
 * de la demande (existant), donc pas de companyInfo. À la création, le ticket est
 * lié à la demande (ticketId) via le onCreated du parent.
 */
function CreateTicketDrawer({ open, onClose, demande, onCreated }: CreateTicketDrawerProps) {
  const [titre, setTitre] = useState('');
  const [type, setType] = useState<TicketType>('MODIFICATION');
  const [priorite, setPriorite] = useState<TicketPriorite>('NORMALE');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setTitre(demande.sujet ?? '');
      setDescription(demande.description ?? '');
      setType('MODIFICATION');
      setPriorite('NORMALE');
      setError(null);
    }
  }, [open, demande]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const titreErr = requiredMsg(titre, 'Le titre est requis.');
    if (titreErr) {
      setError(titreErr);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const ticket = await ticketService.create({
        titre: titre.trim(),
        type,
        priorite,
        dossierId: demande.dossierId,
        description: description.trim() || undefined,
      });
      await onCreated(ticket.id);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Créer un ticket"
      subtitle="À partir de cette demande client (optionnel)"
      width="md"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField
          label="Titre"
          value={titre}
          onChange={(e) => setTitre(e.target.value)}
          required
        />
        <div className="grid gap-3 md:grid-cols-2">
          <Select
            label="Type de ticket"
            value={type}
            onChange={(e) => setType(e.target.value as TicketType)}
            options={TICKET_TYPE_OPTIONS}
          />
          <Select
            label="Priorité"
            value={priorite}
            onChange={(e) => setPriorite(e.target.value as TicketPriorite)}
            options={PRIORITE_OPTIONS}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-fg-muted">Description</label>
          <textarea
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
          />
        </div>
        {error && (
          <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={loading} disabled={!titre.trim()}>
            <TicketPlus className="mr-1 h-4 w-4" />
            Créer et lier
          </Button>
        </div>
      </form>
    </Drawer>
  );
}

interface NewDemandeDrawerProps {
  open: boolean;
  onClose: () => void;
  dossierId: string;
  onCreated: () => Promise<void> | void;
}

function NewDemandeDrawer({
  open,
  onClose,
  dossierId,
  onCreated,
}: NewDemandeDrawerProps) {
  const [sujet, setSujet] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setSujet('');
      setDescription('');
      setError(null);
    }
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const sujetErr = requiredMsg(sujet, 'Le sujet est requis.');
    if (sujetErr) {
      setError(sujetErr);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await dataroomService.createDemande({
        sujet: sujet.trim(),
        description: description.trim() || undefined,
        dossierId,
      });
      await onCreated();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Nouvelle demande"
      subtitle="Votre demande sera transmise au cabinet"
      width="md"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField
          label="Sujet"
          value={sujet}
          onChange={(e) => setSujet(e.target.value)}
          placeholder="Ex : Modification de l'objet social"
          required
        />
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-fg-muted">
            Description
          </label>
          <textarea
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Decrivez votre besoin..."
            className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
          />
        </div>
        {error && (
          <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
            {error}
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={loading} disabled={!sujet.trim()}>
            <Send className="mr-1 h-3.5 w-3.5" />
            Envoyer
          </Button>
        </div>
      </form>
    </Drawer>
  );
}

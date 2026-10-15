import { useCallback, useEffect, useMemo, useState } from 'react';
import { MessageSquare, Plus, Send, TicketPlus } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Drawer } from '../../components/ui/Drawer';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
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
import type { Ticket, TicketType, TicketPriorite } from '../../types/ticket';
import { TICKET_TYPE_LABELS, TYPES_AUTO_DOSSIER } from '../../types/ticket';
import type { Role } from '../../types/auth';
import { InfoBulle } from '../../components/ui/Aide';

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(load);
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
      {error && <Message ton="erreur">{error}</Message>}

      <Card padding={false} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
        <div className="flex flex-wrap items-center gap-2 text-sm text-fg-muted">
          <Badge variant="info">{counts.NON_TRAITEE} non traitée{counts.NON_TRAITEE > 1 ? 's' : ''}</Badge>
          <Badge variant="warning">{counts.EN_COURS} en cours</Badge>
          <Badge variant="success">{counts.TRAITEE} traitée{counts.TRAITEE > 1 ? 's' : ''}</Badge>
          <InfoBulle
            libelle="Aide sur les demandes du client"
            texte={
              role === 'CLIENT'
                ? 'Vos demandes au cabinet sont suivies par trois états : « Non traitée », « En cours » (prise en charge par votre conseiller), « Traitée ». Vous êtes averti par courriel quand votre demande est traitée.'
                : 'Chaque demande du client est « Non traitée » jusqu’à sa prise en charge, puis « En cours », puis « Traitée ». Vous êtes notifié dans la cloche d’une nouvelle demande ; quand vous la passez à « Traitée », le client en est averti par courriel.'
            }
          />
        </div>
        {canCreate && (
          <Button type="button" size="sm" icon={<Plus />} onClick={() => setNewOpen(true)}>
            Nouvelle demande
          </Button>
        )}
      </Card>

      <Card padding={false}>
        {loading && demandes.length === 0 ? (
          <div className="space-y-3 px-5 py-4" aria-busy="true" aria-label="Chargement des demandes">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : !loading && demandes.length === 0 ? (
          <EmptyState
            compact
            icon={<MessageSquare />}
            title={role === 'CLIENT' ? 'Aucune demande pour ce dossier' : 'Aucune demande du client'}
            description={
              role === 'CLIENT'
                ? 'Cliquez sur « Nouvelle demande » pour écrire au cabinet.'
                : 'Les demandes que le client adresse au cabinet depuis son espace apparaîtront ici. Vous serez notifié dans la cloche.'
            }
          />
        ) : (
          <ul className="divide-y divide-border">
            {demandes.map((demande) => (
              <DemandeRow
                key={demande.id}
                demande={demande}
                editable={canEdit}
                onChanged={load}
              />
            ))}
          </ul>
        )}
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

const AUCUN_TICKET = '';

function DemandeRow({ demande, editable, onChanged }: DemandeRowProps) {
  const [statut, setStatut] = useState<DemandeStatut>(demande.statut);
  const [noteInterne, setNoteInterne] = useState(demande.noteInterne ?? '');
  const [ticketId, setTicketId] = useState(demande.ticketId ?? AUCUN_TICKET);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [ticketDrawerOpen, setTicketDrawerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Backlog L6 : le ticket lie se CHOISIT parmi les tickets du dossier (GET /tickets?dossierId=),
  // lu a l'ouverture de la modification ; plus de saisie d'identifiant a la main.
  const [ticketsDossier, setTicketsDossier] = useState<Ticket[] | null>(null);
  const [ticketsErreur, setTicketsErreur] = useState(false);

  function ouvrirModification() {
    setEditing(true);
    if (ticketsDossier !== null) return;
    ticketService
      .list({ dossierId: demande.dossierId, limit: 100 })
      .then((page) => setTicketsDossier(page.items))
      .catch(() => {
        setTicketsDossier([]);
        setTicketsErreur(true);
      });
  }

  const optionsTickets = [
    { value: AUCUN_TICKET, label: 'Aucun ticket lié' },
    ...(ticketsDossier ?? []).map((t) => ({ value: t.id, label: `${t.reference} — ${t.titre}` })),
  ];
  if (ticketId && !(ticketsDossier ?? []).some((t) => t.id === ticketId)) {
    optionsTickets.push({ value: ticketId, label: 'Ticket lié actuel (hors des tickets du dossier)' });
  }

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
            {demande.ticketId && <Badge variant="neutral">Ticket lié</Badge>}
          </div>
          {demande.description && (
            <p className="mt-1 whitespace-pre-wrap text-sm text-fg-muted">
              {demande.description}
            </p>
          )}
          <p className="mt-1 text-xs text-fg-muted">
            Soumise le {formatDateTime(demande.createdAt)}
            {demande.traiteAt ? ` · Traitée le ${formatDateTime(demande.traiteAt)}` : ''}
          </p>
          {demande.noteInterne && !editing && (
            <div className="mt-2 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-xs text-fg-muted">
              <span className="font-semibold text-fg">Note interne :</span> {demande.noteInterne}
            </div>
          )}
        </div>
        {editable && !editing && (
          <div className="flex shrink-0 items-center gap-2">
            {!demande.ticketId && (
              <Button
                type="button"
                size="sm"
                variant="secondary"
                icon={<TicketPlus />}
                onClick={() => setTicketDrawerOpen(true)}
                title="Ouvrir un ticket pour l’opération demandée ; il sera lié à la demande"
              >
                Créer un ticket
              </Button>
            )}
            <Button type="button" size="sm" variant="secondary" onClick={ouvrirModification}>
              Modifier
            </Button>
          </div>
        )}
      </div>

      {editable && editing && (
        <div className="mt-3 grid gap-3 rounded-lg border border-border bg-bg-overlay p-4 md:grid-cols-2">
          <Select
            label="Statut"
            value={statut}
            onChange={(e) => setStatut(e.target.value as DemandeStatut)}
            options={STATUT_OPTIONS}
          />
          <Select
            label="Ticket lié (facultatif)"
            value={ticketId}
            onChange={(e) => setTicketId(e.target.value)}
            options={optionsTickets}
            disabled={ticketsDossier === null}
            hint={
              ticketsDossier === null
                ? 'Chargement des tickets du dossier…'
                : ticketsErreur
                  ? 'Les tickets du dossier n’ont pas pu être lus ; réessayez plus tard.'
                  : 'Un ticket du dossier. Pour une opération nouvelle, « Créer un ticket » l’ouvre et le lie à la demande.'
            }
          />
          <ZoneTexte
            label="Note interne"
            rows={3}
            value={noteInterne}
            onChange={(e) => setNoteInterne(e.target.value)}
            hint="Visible du cabinet seulement, jamais du client."
            containerClassName="md:col-span-2"
          />
          {error && (
            <div className="md:col-span-2">
              <Message ton="erreur">{error}</Message>
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
                setTicketId(demande.ticketId ?? AUCUN_TICKET);
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

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertureVue, setOuvertureVue] = useState<{ open: boolean; demande: DemandeSummary } | null>(null);
  if (!ouvertureVue || ouvertureVue.open !== open || ouvertureVue.demande !== demande) {
    setOuvertureVue({ open, demande });
    if (open) {
      setTitre(demande.sujet ?? '');
      setDescription(demande.description ?? '');
      setType('MODIFICATION');
      setPriorite('NORMALE');
      setError(null);
    }
  }

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
      subtitle="Le ticket ouvert sera lié à cette demande du client."
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
        <ZoneTexte
          label="Description"
          rows={4}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        {error && <Message ton="erreur">{error}</Message>}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" icon={<TicketPlus />} loading={loading} disabled={!titre.trim()}>
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

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertVu, setOuvertVu] = useState<boolean | null>(null);
  if (ouvertVu !== open) {
    setOuvertVu(open);
    if (open) {
      setSujet('');
      setDescription('');
      setError(null);
    }
  }

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
      subtitle="Votre demande sera transmise au cabinet."
      width="md"
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <TextField
          label="Sujet"
          value={sujet}
          onChange={(e) => setSujet(e.target.value)}
          placeholder="Exemple : modification de l’objet social"
          required
        />
        <ZoneTexte
          label="Description"
          rows={4}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Décrivez votre besoin…"
        />
        {error && <Message ton="erreur">{error}</Message>}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" icon={<Send />} loading={loading} disabled={!sujet.trim()}>
            Envoyer
          </Button>
        </div>
      </form>
    </Drawer>
  );
}

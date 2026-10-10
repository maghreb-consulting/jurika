import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Play, Plus, Trash2 } from 'lucide-react';
import { Drawer } from '../../components/ui/Drawer';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ticketService } from '../../services/ticket.service';
import { useCurrentUser } from '../../store/authStore';
import { extractError } from '../../lib/api';
import {
  firstError,
  focusFirstError,
  hasErrors,
  requiredMsg,
  type FieldErrors,
} from '../../lib/formValidation';
import {
  CATEGORIE_LABELS,
  STATUT_LABELS,
  TICKET_TYPE_LABELS,
} from '../../types/ticket';
import type { Debours, DeboursCategorie, Ticket } from '../../types/ticket';
import { CancelTicketDialog } from './CancelTicketDialog';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';
import { DeadlinesPanel } from '../../components/tickets/DeadlinesPanel';
import { DemarchesPanel } from './DemarchesPanel';
import { RecapitulatifPanel } from './RecapitulatifPanel';
import { NoteTicketPanel } from './NoteTicketPanel';
import { EntityActivityPanel } from '../../components/tracabilite/EntityActivityPanel';

interface Props {
  ticketId: string;
  onClose: () => void;
  onChanged: () => Promise<void>;
}

export function TicketDetailDrawer({ ticketId, onClose, onChanged }: Props) {
  const navigate = useNavigate();
  const user = useCurrentUser();
  // SUPERVISEUR / SUPER_ADMIN = oversight only : ils consultent le ticket mais
  // ne le manipulent pas. Seul l'EMPLOYE agit (prise en charge, workflow,
  // cloture, annulation, transfert). Le backend renvoie 403 de toute facon.
  const canAct = user?.role === 'EMPLOYE';
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [debours, setDebours] = useState<Debours[]>([]);
  const [totalDebours, setTotalDebours] = useState(0);
  const [showCancel, setShowCancel] = useState(false);
  // Transition sensible depuis ANNULE : reprise (-> EN_COURS) ou cloture (-> CLOTURE).
  const [sensitive, setSensitive] = useState<null | 'REPRENDRE' | 'CLOTURER_ANNULE'>(null);
  const [showAddDebours, setShowAddDebours] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Suppression d'un debours : confirmation via ConfirmDialog (remplace le
  // confirm() natif). On memorise le debours cible + l'etat de chargement.
  const [deboursToDelete, setDeboursToDelete] = useState<Debours | null>(null);
  const [deletingDebours, setDeletingDebours] = useState(false);

  const load = useCallback(async () => {
    try {
      const [t, dList] = await Promise.all([
        ticketService.get(ticketId),
        ticketService.listDebours(ticketId),
      ]);
      setTicket(t);
      setDebours(dList.items);
      setTotalDebours(dList.total);
    } catch (err) {
      setError(extractError(err).message);
    }
  }, [ticketId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleTransition(target: Ticket['statut'], comment?: string) {
    try {
      await ticketService.transition(ticketId, { target, comment });
      await load();
      await onChanged();
    } catch (err) {
      setError(extractError(err).message);
    }
  }

  async function handleDeleteDebours() {
    if (!deboursToDelete) return;
    setDeletingDebours(true);
    try {
      await ticketService.deleteDebours(ticketId, deboursToDelete.id);
      setDeboursToDelete(null);
      await load();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setDeletingDebours(false);
    }
  }

  if (!ticket) {
    return (
      <Drawer open onClose={onClose} title="Chargement..." width="lg">
        <div className="flex justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
        </div>
      </Drawer>
    );
  }

  return (
    <>
      <Drawer
        open
        onClose={onClose}
        title={ticket.titre}
        subtitle={`${ticket.reference} — ${TICKET_TYPE_LABELS[ticket.type]}`}
        width="xl"
        footer={
          canAct ? (
            <div className="flex flex-wrap justify-end gap-2">
              {/* 2026-07-04 — Le transfert ne se fait QUE sur les Data Rooms
                  (page Data Room), pas depuis un ticket. Un ticket n'est pas
                  transferable en soi ; on retire donc le bouton ici. */}
              {ticket.statut === 'CREATION_TICKET' && (
                <Button onClick={() => handleTransition('GENERATION_DOCUMENTS')}>
                  <Play className="mr-1 h-4 w-4" /> Prendre en charge
                </Button>
              )}
              {ticket.statut === 'GENERATION_DOCUMENTS' && (
                <>
                  <Button variant="primary" onClick={() => navigate(`/workflows/${ticket.id}`)}>
                    Reprendre le workflow
                  </Button>
                  {/* Lot 1 — le parcours passe par « Déroulement de la démarche » :
                      on ne clôture plus directement depuis la génération. */}
                  <Button
                    variant="secondary"
                    onClick={() => handleTransition('DEROULEMENT_DEMARCHE')}
                  >
                    Passer aux démarches
                  </Button>
                </>
              )}
              {ticket.statut === 'DEROULEMENT_DEMARCHE' && (
                <>
                  <Button variant="primary" onClick={() => navigate(`/workflows/${ticket.id}`)}>
                    Consulter le workflow
                  </Button>
                  <Button variant="secondary" onClick={() => handleTransition('CLOTURE_DOSSIER')}>
                    Clôturer le dossier
                  </Button>
                </>
              )}
              {/* 2026-08-12 — Ticket CLOTURE : le workflow reste CONSULTABLE en
                  lecture seule (valeurs saisies + étapes + documents générés). */}
              {ticket.statut === 'CLOTURE_DOSSIER' && (
                <Button variant="secondary" onClick={() => navigate(`/workflows/${ticket.id}`)}>
                  Consulter le workflow
                </Button>
              )}
              {/* 2026-06-25 — Un ticket ANNULE peut etre repris ou cloture,
                  chacun via dialog de motif obligatoire.
                  2026-08-12 — + consultation lecture seule du workflow. */}
              {ticket.statut === 'ANNULE' && (
                <>
                  <Button variant="secondary" onClick={() => navigate(`/workflows/${ticket.id}`)}>
                    Consulter le workflow
                  </Button>
                  <Button variant="primary" onClick={() => setSensitive('REPRENDRE')}>
                    <Play className="mr-1 h-4 w-4" /> Reprendre
                  </Button>
                  <Button variant="secondary" onClick={() => setSensitive('CLOTURER_ANNULE')}>
                    Cloturer
                  </Button>
                </>
              )}
              {/* Annulation possible depuis chacun des quatre autres statuts :
                  c'est une sortie latérale, pas une étape du parcours. */}
              {ticket.statut !== 'ANNULE' && (
                <Button variant="danger" onClick={() => setShowCancel(true)}>
                  Annuler le ticket
                </Button>
              )}
            </div>
          ) : (
            <div className="flex justify-end">
              <span className="text-xs text-fg-subtle">
                Consultation seule — actions reservees a l'employe en charge
              </span>
            </div>
          )
        }
      >
        {error && (
          <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            {error}
          </div>
        )}

        <section className="space-y-4">
          <div className="grid grid-cols-2 gap-4 rounded-lg bg-bg-overlay p-4 text-sm md:grid-cols-4">
            <Field label="Statut">
              <StatutBadge s={ticket.statut} />
            </Field>
            <Field label="Priorite">{ticket.priorite}</Field>
            <Field label="Echeance">
              {ticket.deadline ? new Date(ticket.deadline).toLocaleDateString('fr-FR') : '—'}
            </Field>
            <Field label="Cree le">
              {new Date(ticket.createdAt).toLocaleDateString('fr-FR')}
            </Field>
          </div>

          {ticket.description && (
            <div>
              <h3 className="text-sm font-semibold text-fg">Description</h3>
              <p className="mt-1 whitespace-pre-wrap text-sm text-fg-muted">{ticket.description}</p>
            </div>
          )}

          {/* Ticket actuellement ANNULE : motif d'annulation en rouge (pertinent). */}
          {ticket.statut === 'ANNULE' && ticket.annulationMotif && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm">
              <p className="font-semibold text-rose-800">Motif d'annulation</p>
              <p className="mt-1 text-rose-700">{ticket.annulationMotif}</p>
            </div>
          )}

          {/* Ticket repris (n'est plus ANNULE) : motif de reprise en ambre, PAS le
              rouge d'annulation (l'annulation est historique, visible dans l'activite). */}
          {ticket.statut !== 'ANNULE' && ticket.repriseMotif && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
              <p className="font-semibold text-amber-800">Motif de reprise</p>
              <p className="mt-1 text-amber-700">{ticket.repriseMotif}</p>
            </div>
          )}

          <section>
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold text-fg">Etat des debours</h3>
                <p className="text-xs text-fg-subtle">
                  Total : <span className="font-semibold text-fg">{totalDebours.toLocaleString('fr-FR')} MAD</span>
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={debours.length === 0}
                  onClick={async () => {
                    try {
                      await ticketService.downloadDeboursPdf(ticketId, ticket.reference);
                    } catch (err) {
                      console.error('Echec generation PDF debours', err);
                    }
                  }}
                  title={debours.length === 0 ? 'Aucun debours a exporter' : 'Telecharger l etat debours en PDF'}
                >
                  Etat PDF
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setShowAddDebours((v) => !v)}>
                  <Plus className="mr-1 h-3.5 w-3.5" /> Ajouter
                </Button>
              </div>
            </div>

            {showAddDebours && (
              <DeboursForm
                ticketId={ticketId}
                onCreated={() => {
                  setShowAddDebours(false);
                  load();
                }}
              />
            )}

            <ul className="mt-3 divide-y divide-border rounded-lg border border-border">
              {debours.map((d) => (
                <li key={d.id} className="flex items-center justify-between px-3 py-2 text-sm">
                  <div>
                    <p className="font-medium text-fg">{d.libelle}</p>
                    <p className="text-xs text-fg-subtle">
                      {CATEGORIE_LABELS[d.categorie]} — {new Date(d.dateEngagement).toLocaleDateString('fr-FR')}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-semibold text-fg">
                      {d.montantMad.toLocaleString('fr-FR')} MAD
                    </span>
                    <button
                      type="button"
                      onClick={() => setDeboursToDelete(d)}
                      className="text-rose-500 hover:text-rose-700"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </li>
              ))}
              {debours.length === 0 && (
                <li className="px-3 py-6 text-center text-xs text-fg-subtle">Aucun debours</li>
              )}
            </ul>
          </section>

          {/*
            Lot 1 (2026-09-04) — avancement du parcours et cochage des demarches,
            consultables SANS ouvrir le workflow. Le panneau ne rend rien pour les
            workflows sans referentiel charge.

            Lot B (2026-09-11) — EN LECTURE SEULE ICI.

            Le detail d'un ticket est une vue de CONSULTATION, pas un poste de
            travail : le cochage se fait dans le workflow, ou l'employe a sous les
            yeux la condition d'application, les justificatifs attendus et le
            document a deposer. Le meme geste pose depuis un tiroir de detail se
            fait sans ce contexte.

            La regle vaut pour tous les statuts, cloture comprise : un ticket clos
            n'offre donc AUCUNE action.
          */}
          {/* Lot L1 (RG-TKT-07) : note interne du ticket, enregistree automatiquement. */}
          <NoteTicketPanel ticketId={ticketId} statut={ticket.statut} role={user?.role} />

          <DemarchesPanel
            ticketId={ticketId}
            dossierId={ticket.dossierId ?? null}
            canAct={false}
          />

          {/*
            Lot B — LE RECAPITULATIF. Avant de clore, il montre ce que le dossier
            contient et surtout ce qui lui manque ; apres, il est la vue resumee
            du ticket clos. Il n'offre aucune action dans les deux cas.
          */}
          {(ticket.statut === 'DEROULEMENT_DEMARCHE'
            || ticket.statut === 'CLOTURE_DOSSIER'
            || ticket.statut === 'ANNULE') && (
            <RecapitulatifPanel ticketId={ticketId} />
          )}

          <DeadlinesPanel ticketId={ticketId} />

          {/* E2 — Activite (tracabilite) de ce ticket */}
          <EntityActivityPanel entityType="ticket" entityId={ticketId} title="Activite du ticket" />
        </section>
      </Drawer>

      {showCancel && (
        <CancelTicketDialog
          ticket={ticket}
          onClose={() => setShowCancel(false)}
          onConfirm={async (comment) => {
            await handleTransition('ANNULE', comment);
            setShowCancel(false);
          }}
        />
      )}

      {sensitive === 'REPRENDRE' && (
        <SensitiveTransitionDialog
          title="Reprendre le ticket"
          intro="Vous etes sur le point de reprendre (remettre EN COURS) le ticket annule"
          reference={ticket.reference}
          minLength={1}
          confirmLabel="Reprendre le ticket"
          variant="primary"
          onClose={() => setSensitive(null)}
          onConfirm={async (comment) => {
            await handleTransition('GENERATION_DOCUMENTS', comment);
            setSensitive(null);
          }}
        />
      )}

      {sensitive === 'CLOTURER_ANNULE' && (
        <SensitiveTransitionDialog
          title="Cloturer le ticket"
          intro="Vous etes sur le point de cloturer le ticket annule"
          reference={ticket.reference}
          minLength={1}
          confirmLabel="Cloturer le ticket"
          variant="primary"
          onClose={() => setSensitive(null)}
          onConfirm={async (comment) => {
            await handleTransition('CLOTURE_DOSSIER', comment);
            setSensitive(null);
          }}
        />
      )}

      <ConfirmDialog
        open={deboursToDelete !== null}
        onOpenChange={(o) => {
          if (!o) setDeboursToDelete(null);
        }}
        title="Supprimer ce debours ?"
        description={
          deboursToDelete
            ? `« ${deboursToDelete.libelle} » (${deboursToDelete.montantMad.toLocaleString('fr-FR')} MAD) sera definitivement retire.`
            : undefined
        }
        variant="danger"
        confirmLabel="Supprimer"
        loading={deletingDebours}
        onConfirm={handleDeleteDebours}
      />
    </>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wider text-fg-subtle">{label}</p>
      <div className="mt-0.5 text-sm font-medium text-fg">{children}</div>
    </div>
  );
}

function StatutBadge({ s }: { s: Ticket['statut'] }) {
  const variant = {
    CREATION_TICKET: 'info',
    GENERATION_DOCUMENTS: 'warning',
    DEROULEMENT_DEMARCHE: 'warning',
    CLOTURE_DOSSIER: 'success',
    ANNULE: 'danger',
  }[s] as 'info' | 'warning' | 'success' | 'danger';
  return <Badge variant={variant}>{STATUT_LABELS[s]}</Badge>;
}

function DeboursForm({ ticketId, onCreated }: { ticketId: string; onCreated: () => void }) {
  const [libelle, setLibelle] = useState('');
  const [categorie, setCategorie] = useState<DeboursCategorie>('FRAIS_TRIBUNAL');
  const [montant, setMontant] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const nextErrors: FieldErrors = {
      libelle: requiredMsg(libelle, 'Le libelle est requis.') ?? undefined,
      montant:
        firstError(
          () => requiredMsg(montant, 'Le montant est requis.'),
          () => {
            const n = parseFloat(montant);
            return !Number.isFinite(n) || n <= 0
              ? 'Veuillez saisir un montant positif.'
              : null;
          },
        ) ?? undefined,
      date: requiredMsg(date, "La date d'engagement est requise.") ?? undefined,
    };
    if (hasErrors(nextErrors)) {
      setErrors(nextErrors);
      focusFirstError(nextErrors, ['libelle', 'montant', 'date']);
      return;
    }
    setErrors({});
    setLoading(true);
    try {
      await ticketService.createDebours(ticketId, {
        libelle,
        categorie,
        montant: parseFloat(montant),
        dateEngagement: date,
      });
      onCreated();
    } finally {
      setLoading(false);
    }
  }

  const options = Object.entries(CATEGORIE_LABELS).map(([value, label]) => ({ value, label }));

  return (
    <form onSubmit={submit} noValidate className="mt-3 grid gap-3 rounded-lg border border-border bg-bg-overlay p-3 md:grid-cols-2">
      <TextField
        label="Libelle"
        name="libelle"
        value={libelle}
        onChange={(e) => setLibelle(e.target.value)}
        error={errors.libelle}
        required
      />
      <Select
        label="Categorie"
        value={categorie}
        onChange={(e) => setCategorie(e.target.value as DeboursCategorie)}
        options={options}
      />
      <TextField
        label="Montant (MAD)"
        name="montant"
        type="number"
        step="0.01"
        value={montant}
        onChange={(e) => setMontant(e.target.value)}
        error={errors.montant}
        required
      />
      <TextField
        label="Date engagement"
        name="date"
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        error={errors.date}
        required
      />
      <div className="md:col-span-2 flex justify-end">
        <Button type="submit" size="sm" loading={loading}>
          Enregistrer le debours
        </Button>
      </div>
    </form>
  );
}

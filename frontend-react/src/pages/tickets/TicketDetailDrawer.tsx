import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Play } from 'lucide-react';
import { Drawer } from '../../components/ui/Drawer';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ticketService } from '../../services/ticket.service';
import { useCurrentUser } from '../../store/authStore';
import { extractError } from '../../lib/api';
import { STATUT_LABELS, TICKET_TYPE_LABELS } from '../../types/ticket';
import type { Ticket } from '../../types/ticket';
import { CancelTicketDialog } from './CancelTicketDialog';
import { RepriseTicketDialog } from './RepriseTicketDialog';
import { AnnulationDocumentsPanel } from './AnnulationDocumentsPanel';
import { DeadlinesPanel } from '../../components/tickets/DeadlinesPanel';
import { DemarchesPanel } from './DemarchesPanel';
import { DecisionsTicketPanel } from './DecisionsTicketPanel';
import { RecapitulatifPanel } from './RecapitulatifPanel';
import { NoteTicketPanel } from './NoteTicketPanel';
import { DeboursPanel } from './DeboursPanel';
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
  // Lot L4d (E2, RG-DOS-01) : seul l'employe RESPONSABLE agit (le serveur le recontrole a
  // chaque geste) ; le superviseur et les autres employes observent, la raison est affichee.
  // enCharge (et non peutAgir) : sur un ticket clos, le responsable garde la consultation du
  // workflow ; les panneaux excluent eux-memes les gestes des statuts clos et annule.
  const [acces, setAcces] = useState<{ enCharge: boolean; raison: string | null } | null>(null);
  const canAct = user?.role === 'EMPLOYE' && acces?.enCharge === true;
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [showCancel, setShowCancel] = useState(false);
  // Lot L4a (E6, RG-TKT-05) : sortie d'ANNULE par la seule reprise, au statut anterieur.
  const [showReprise, setShowReprise] = useState(false);
  /** Lot L4b : recharge les demarches apres un changement de decisions. */
  const [versionDemarches, setVersionDemarches] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    ticketService.acces(ticketId).then(setAcces).catch(() => setAcces({ enCharge: false, raison: null }));
    try {
      setTicket(await ticketService.get(ticketId));
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
              {/* Lot L4a (E6, RG-TKT-02/05) : un ticket ANNULE ne sort que par la reprise,
                  vers son statut anterieur (la cloture directe n'est pas une transition du
                  cahier des charges). + consultation lecture seule du workflow. */}
              {ticket.statut === 'ANNULE' && (
                <>
                  <Button variant="secondary" onClick={() => navigate(`/workflows/${ticket.id}`)}>
                    Consulter le workflow
                  </Button>
                  <Button variant="primary" onClick={() => setShowReprise(true)}>
                    <Play className="mr-1 h-4 w-4" /> Reprendre
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
              <span className="text-xs text-fg-subtle" data-testid="acces-raison">
                {acces?.raison ?? 'Consultation seule — actions réservées à l’employé responsable du dossier.'}
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

          {/* Lot L5 (E6) : rubrique Debours commune a la page du ticket (saisie, pieces, etat PDF depuis L5b E3). */}
          <DeboursPanel ticketId={ticketId} reference={ticket.reference} dossierId={ticket.dossierId ?? null}
            statut={ticket.statut} canAct={canAct} />

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

            Lot L4b (2026-10-11) : REGLE REVUE. Aucun workflow ne montait ce panneau :
            le cochage n'avait plus d'ecran. Il se fait desormais ici pour l'employe,
            sur un ticket ni clos ni annule ; le ticket clos reste sans action.
          */}
          {/* Lot L4d (E9) : la page du ticket reunit tout, pour tous les parcours. */}
          <button type="button" className="text-sm text-accent underline" data-testid="ouvrir-page-ticket"
            onClick={() => navigate(`/tickets/${ticketId}`)}>
            Ouvrir la page du ticket (passage de statut, données, documents)
          </button>

          {/* Lot L1 (RG-TKT-07) : note interne du ticket, enregistree automatiquement. */}
          <NoteTicketPanel ticketId={ticketId} statut={ticket.statut} role={user?.role} />

          {/*
            Lot L4b (E4, E6) : hors creation, les decisions du ticket choisissent ses
            demarches dans le referentiel du cabinet. Le cochage se fait ici, par
            l'employe, sur un ticket ni clos ni annule (RG-DEM-03) : depuis le lot B,
            aucun ecran ne le permettait (le panneau etait en lecture seule ici et
            n'etait monte nulle part ailleurs).
          */}
          {ticket.type !== 'CREATION' && (
            <DecisionsTicketPanel
              ticketId={ticketId}
              canAct={canAct && ticket.statut !== 'CLOTURE_DOSSIER' && ticket.statut !== 'ANNULE'}
              onChanged={() => setVersionDemarches((v) => v + 1)}
            />
          )}
          <DemarchesPanel
            key={versionDemarches}
            ticketId={ticketId}
            dossierId={ticket.dossierId ?? null}
            canAct={canAct && ticket.statut !== 'CLOTURE_DOSSIER' && ticket.statut !== 'ANNULE'}
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

          {/* Lot L4a (E6, RG-TKT-04) : documents d'annulation, tous parcours. */}
          {ticket.statut === 'ANNULE' && <AnnulationDocumentsPanel ticket={ticket} canAct={canAct} />}

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

      {showReprise && (
        <RepriseTicketDialog
          ticket={ticket}
          onClose={() => setShowReprise(false)}
          onConfirm={async (cible, comment) => {
            await handleTransition(cible, comment);
            setShowReprise(false);
          }}
        />
      )}

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

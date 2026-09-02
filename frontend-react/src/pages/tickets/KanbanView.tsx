import type { DragEvent } from 'react';
import { useState } from 'react';
import { Play } from 'lucide-react';
import { STATUT_LABELS } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';
import { TicketCard } from '../../components/tickets/TicketCard';
import { useToast } from '../../components/ui/Toast';
import { CancelTicketDialog } from './CancelTicketDialog';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';

interface Props {
  byStatus: Record<TicketStatut, Ticket[]>;
  onSelect: (id: string) => void;
  onTransition: (
    id: string,
    target: TicketStatut,
    comment?: string,
  ) => Promise<void>;
  /**
   * SUPERVISEUR / SUPER_ADMIN = oversight only : drag & drop de transition et
   * bouton "Prendre en charge" desactives (lecture seule). Seul l'EMPLOYE agit.
   */
  canAct?: boolean;
}

// Sprint 12.5 T8 -- Colonnes Kanban repeintes : bandeau couleur status sur fond
// bg-overlay navy (au lieu de tints clairs hardcodes). Drag & drop preserve --
// pas de modification des handlers onDragStart/onDragOver/onDrop.
const COLUMNS: { statut: TicketStatut; accent: string }[] = [
  { statut: 'NOUVEAU', accent: 'border-accent' }, // or signature pour "à traiter"
  { statut: 'EN_COURS', accent: 'border-warning' }, // ambre pour activité en cours
  { statut: 'CLOTURE', accent: 'border-success' }, // emeraude pour validé
  { statut: 'ANNULE', accent: 'border-danger' }, // rouge pour irréversible
];

export function KanbanView({ byStatus, onSelect, onTransition, canAct = true }: Props) {
  const toast = useToast();
  const [dragTicket, setDragTicket] = useState<Ticket | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Ticket | null>(null);
  // Sortie d'ANNULE (reprise -> EN_COURS / cloture -> CLOTURE) : transition
  // sensible exigeant un motif -> dialog dedie (via bouton ou drag).
  const [sensitiveTarget, setSensitiveTarget] = useState<
    { ticket: Ticket; target: TicketStatut } | null
  >(null);

  function onDragStart(e: DragEvent<HTMLDivElement>, t: Ticket) {
    setDragTicket(t);
    e.dataTransfer.effectAllowed = 'move';
  }

  function onDragOver(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }

  async function onDrop(e: DragEvent<HTMLDivElement>, target: TicketStatut) {
    e.preventDefault();
    if (!dragTicket || dragTicket.statut === target) {
      setDragTicket(null);
      return;
    }
    if (target === 'ANNULE') {
      setCancelTarget(dragTicket);
      setDragTicket(null);
      return;
    }
    // Sortie d'ANNULE (-> EN_COURS / CLOTURE) : motif obligatoire cote backend
    // -> on passe par le dialog de transition sensible au lieu d'un appel direct.
    if (dragTicket.statut === 'ANNULE') {
      setSensitiveTarget({ ticket: dragTicket, target });
      setDragTicket(null);
      return;
    }
    try {
      await onTransition(dragTicket.id, target);
    } catch (err) {
      console.error(err);
      toast.error((err as Error)?.message ?? 'Transition impossible');
    } finally {
      setDragTicket(null);
    }
  }

  return (
    <>
      <div className="grid gap-4 lg:grid-cols-4">
        {COLUMNS.map((col) => (
          <div
            key={col.statut}
            onDragOver={canAct ? onDragOver : undefined}
            onDrop={canAct ? (e) => onDrop(e, col.statut) : undefined}
            className="flex flex-col gap-3"
          >
            <div
              className={`flex items-center justify-between rounded-r-lg border-l-4 ${col.accent} bg-bg-overlay px-3 py-2`}
            >
              <span className="font-heading text-sm font-semibold text-fg">
                {STATUT_LABELS[col.statut]}
              </span>
              <span className="rounded-full bg-bg-raised px-2 py-0.5 text-xs font-bold text-fg-muted">
                {byStatus[col.statut].length}
              </span>
            </div>
            {/* min-h preserve la zone de drop meme si la colonne est vide.
                Border-dashed border-border-hi/40 = hint discret zone valide. */}
            <div className="flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed border-border-hi/40 p-2 transition-colors">
              {byStatus[col.statut].map((t) => (
                <div key={t.id} className="flex flex-col gap-1.5">
                  <TicketCard
                    ticket={t}
                    onClick={() => onSelect(t.id)}
                    onDragStart={canAct ? (e) => onDragStart(e, t) : undefined}
                  />
                  {/* Prise en charge explicite (dissociee de la creation) :
                      un ticket NOUVEAU reste NOUVEAU tant qu'il n'est pas pris
                      en charge. Bouton clair en complement du drag & drop.
                      Masque pour le superviseur (oversight only). */}
                  {canAct && col.statut === 'NOUVEAU' && (
                    <button
                      type="button"
                      onClick={async (e) => {
                        e.stopPropagation();
                        try {
                          await onTransition(t.id, 'EN_COURS');
                        } catch (err) {
                          console.error(err);
                          toast.error((err as Error)?.message ?? 'Transition impossible');
                        }
                      }}
                      className="inline-flex items-center justify-center gap-1 rounded-lg border border-accent/40 bg-accent/10 px-2 py-1 text-xs font-semibold text-accent transition-colors hover:bg-accent/20"
                    >
                      <Play className="h-3.5 w-3.5" /> Prendre en charge
                    </button>
                  )}
                  {/* Affordance "Reprendre" sur les cartes ANNULE : sortie
                      d'ANNULE via dialog de motif (-> EN_COURS). */}
                  {canAct && col.statut === 'ANNULE' && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSensitiveTarget({ ticket: t, target: 'EN_COURS' });
                      }}
                      className="inline-flex items-center justify-center gap-1 rounded-lg border border-accent/40 bg-accent/10 px-2 py-1 text-xs font-semibold text-accent transition-colors hover:bg-accent/20"
                    >
                      <Play className="h-3.5 w-3.5" /> Reprendre
                    </button>
                  )}
                </div>
              ))}
              {byStatus[col.statut].length === 0 && (
                <div className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-fg-subtle">
                  Aucun ticket
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {cancelTarget && (
        <CancelTicketDialog
          ticket={cancelTarget}
          onClose={() => setCancelTarget(null)}
          onConfirm={async (comment) => {
            await onTransition(cancelTarget.id, 'ANNULE', comment);
            setCancelTarget(null);
          }}
        />
      )}

      {sensitiveTarget && (
        <SensitiveTransitionDialog
          title={sensitiveTarget.target === 'EN_COURS' ? 'Reprendre le ticket' : 'Cloturer le ticket'}
          intro={
            sensitiveTarget.target === 'EN_COURS'
              ? 'Vous etes sur le point de reprendre (remettre EN COURS) le ticket annule'
              : 'Vous etes sur le point de cloturer le ticket annule'
          }
          reference={sensitiveTarget.ticket.reference}
          minLength={1}
          confirmLabel={sensitiveTarget.target === 'EN_COURS' ? 'Reprendre le ticket' : 'Cloturer le ticket'}
          variant="primary"
          onClose={() => setSensitiveTarget(null)}
          onConfirm={async (comment) => {
            await onTransition(sensitiveTarget.ticket.id, sensitiveTarget.target, comment);
            setSensitiveTarget(null);
          }}
        />
      )}
    </>
  );
}

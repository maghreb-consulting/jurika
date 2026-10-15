import type { DragEvent } from 'react';
import { useState } from 'react';
import { Play } from 'lucide-react';
import { STATUT_LABELS } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';
import { TicketCard } from '../../components/tickets/TicketCard';
import { useToast } from '../../components/ui/useToast';
import { CancelTicketDialog } from './CancelTicketDialog';
import { RepriseTicketDialog } from './RepriseTicketDialog';
import type { ColonneTickets } from './useTicketColumns';
import { Button } from '../../components/ui/Button';
import { CardSkeleton } from '../../components/ui/LoadingSkeleton';
import { cn } from '../../lib/utils';
import { COLUMNS, PISTES_KANBAN } from './kanbanColonnes';
import { RangeeDefilante } from '../../components/tickets/RangeeDefilante';

interface Props {
  byStatus: Record<TicketStatut, ColonneTickets>;
  onSelect: (id: string) => void;
  onTransition: (
    id: string,
    target: TicketStatut,
    comment?: string,
  ) => Promise<void>;
  /** Charge la page suivante de la colonne (pagination par statut, lot 2). */
  onLoadMore: (statut: TicketStatut) => void;
  /**
   * SUPERVISEUR / SUPER_ADMIN = oversight only : drag & drop de transition et
   * bouton "Prendre en charge" desactives (lecture seule). Seul l'EMPLOYE agit.
   */
  canAct?: boolean;
}

/**
 * Compteur de tete : le TOTAL EN BASE, et — quand tout n'est pas charge — le
 * nombre rendu par rapport a ce total. « 25 sur 137 » et non « 25 ».
 */
function compteurTexte(col: ColonneTickets): string {
  if (col.loading) return '…';
  if (col.items.length < col.total) return `${col.items.length} / ${col.total}`;
  return String(col.total);
}

function compteurTitre(col: ColonneTickets): string {
  if (col.loading) return 'Chargement…';
  if (col.items.length < col.total) {
    return `${col.items.length} ticket(s) affiché(s) sur ${col.total} au total`;
  }
  return `${col.total} ticket(s)`;
}

function restant(col: ColonneTickets): number {
  return Math.max(0, col.total - col.items.length);
}

export function KanbanView({
  byStatus,
  onSelect,
  onTransition,
  onLoadMore,
  canAct = true,
}: Props) {
  const toast = useToast();
  const [dragTicket, setDragTicket] = useState<Ticket | null>(null);
  const [cancelTarget, setCancelTarget] = useState<Ticket | null>(null);
  // Lot L4a (E6, RG-TKT-05) : sortie d'ANNULE par la seule reprise, vers le statut
  // anterieur que le serveur annonce (quelle que soit la colonne de depot).
  const [repriseTarget, setRepriseTarget] = useState<Ticket | null>(null);

  function onDragStart(e: DragEvent<HTMLDivElement>, t: Ticket) {
    setDragTicket(t);
    e.dataTransfer.effectAllowed = 'move';
  }

  function onDragOver(e: DragEvent<HTMLElement>) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }

  async function onDrop(e: DragEvent<HTMLElement>, target: TicketStatut) {
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
    // Sortie d'ANNULE : reprise au statut anterieur (motif obligatoire), annoncee par
    // le dialogue commun ; la colonne de depot ne choisit pas le statut retrouve.
    if (dragTicket.statut === 'ANNULE') {
      setRepriseTarget(dragTicket);
      setDragTicket(null);
      return;
    }
    try {
      await onTransition(dragTicket.id, target);
    } catch (err) {
      console.error(err);
      toast.error((err as Error)?.message ?? 'Changement de statut impossible.');
    } finally {
      setDragTicket(null);
    }
  }

  return (
    <>
      {/* Lot 2 : la grille se DERIVE de COLUMNS : ajouter un statut ajoute une colonne.
          Style inline : Tailwind ne genere pas les classes construites a la volee.
          L6b (E7) : les cinq colonnes tiennent a 1 366 px ; en dessous, la rangee defile
          horizontalement et l'ecran le dit (RangeeDefilante). */}
      <RangeeDefilante libelle="Colonnes des tickets par statut">
        <div className="grid gap-3" style={{ gridTemplateColumns: PISTES_KANBAN }} data-testid="kanban-grid">
          {COLUMNS.map((col) => {
            const colonne = byStatus[col.statut];
            return (
              <section
                key={col.statut}
                aria-labelledby={`kanban-titre-${col.statut}`}
                onDragOver={canAct ? onDragOver : undefined}
                onDrop={canAct ? (e) => onDrop(e, col.statut) : undefined}
                data-testid={`kanban-col-${col.statut}`}
                className={cn('flex min-w-0 flex-col gap-2', col.sortie && 'ml-2 border-l border-border pl-4')}
              >
                <div
                  className={cn(
                    'flex items-center justify-between gap-2 rounded-r-lg border-l-4 bg-bg-raised px-3 py-2 shadow-card',
                    col.accent,
                  )}
                >
                  <h2
                    id={`kanban-titre-${col.statut}`}
                    className={cn('truncate text-sm font-semibold', col.sortie ? 'text-fg-muted' : 'text-heading')}
                    title={STATUT_LABELS[col.statut]}
                  >
                    {STATUT_LABELS[col.statut]}
                  </h2>
                  <span
                    className="shrink-0 rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-semibold tabular-nums text-fg-muted"
                    title={compteurTitre(colonne)}
                    data-testid={`kanban-count-${col.statut}`}
                  >
                    {compteurTexte(colonne)}
                  </span>
                </div>
                {/* La sortie laterale se dit, elle ne se devine pas a la couleur du filet. */}
                {col.sortie && (
                  <p className="px-1 text-xs leading-snug text-fg-subtle">
                    Sortie du parcours, atteignable depuis les quatre autres statuts —
                    ce n’est pas une cinquième étape.
                  </p>
                )}
                {/* min-h preserve la zone de depot meme si la colonne est vide. */}
                <div
                  className={cn(
                    'flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed p-1.5',
                    col.sortie ? 'border-border bg-bg-overlay/40' : 'border-border-hi',
                  )}
                >
                  {colonne.loading && colonne.items.length === 0 && <CardSkeleton className="p-3" />}
                  {colonne.items.map((t) => (
                    <div key={t.id} className="flex flex-col gap-1.5">
                      <TicketCard
                        ticket={t}
                        onClick={() => onSelect(t.id)}
                        onDragStart={canAct ? (e) => onDragStart(e, t) : undefined}
                      />
                      {/* Prise en charge explicite (dissociee de la creation) : un ticket
                          NOUVEAU le reste tant qu'il n'est pas pris en charge. Masque pour
                          le superviseur (le serveur le refuse). */}
                      {canAct && col.statut === 'CREATION_TICKET' && (
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          fullWidth
                          icon={<Play />}
                          onClick={async (e) => {
                            e.stopPropagation();
                            try {
                              await onTransition(t.id, 'GENERATION_DOCUMENTS');
                            } catch (err) {
                              console.error(err);
                              toast.error((err as Error)?.message ?? 'Changement de statut impossible.');
                            }
                          }}
                        >
                          Prendre en charge
                        </Button>
                      )}
                      {/* Reprise d'un ticket ANNULE au statut anterieur (dialogue commun, RG-TKT-05). */}
                      {canAct && col.statut === 'ANNULE' && (
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          fullWidth
                          icon={<Play />}
                          onClick={(e) => {
                            e.stopPropagation();
                            setRepriseTarget(t);
                          }}
                        >
                          Reprendre
                        </Button>
                      )}
                    </div>
                  ))}
                  {colonne.items.length === 0 && !colonne.loading && (
                    <p className="px-2 py-6 text-center text-xs text-fg-subtle">Aucun ticket</p>
                  )}
                  {/* "Afficher plus" par colonne : la difference entre ce qui est rendu et ce qui existe. */}
                  {colonne.hasMore && (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      fullWidth
                      onClick={() => onLoadMore(col.statut)}
                      disabled={colonne.loadingMore}
                      data-testid={`kanban-more-${col.statut}`}
                    >
                      {colonne.loadingMore ? 'Chargement…' : `Afficher plus (${restant(colonne)} restants)`}
                    </Button>
                  )}
                </div>
              </section>
            );
          })}
        </div>
      </RangeeDefilante>

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

      {repriseTarget && (
        <RepriseTicketDialog
          ticket={repriseTarget}
          onClose={() => setRepriseTarget(null)}
          onConfirm={async (cible, comment) => {
            await onTransition(repriseTarget.id, cible, comment);
            setRepriseTarget(null);
          }}
        />
      )}
    </>
  );
}

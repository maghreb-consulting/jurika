import type { DragEvent } from 'react';
import { useState } from 'react';
import { Play } from 'lucide-react';
import { STATUT_LABELS } from '../../types/ticket';
import type { Ticket, TicketStatut } from '../../types/ticket';
import { TicketCard } from '../../components/tickets/TicketCard';
import { useToast } from '../../components/ui/Toast';
import { CancelTicketDialog } from './CancelTicketDialog';
import { SensitiveTransitionDialog } from './SensitiveTransitionDialog';
import type { ColonneTickets } from './useTicketColumns';

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

// Sprint 12.5 T8 -- Colonnes Kanban repeintes : bandeau couleur status sur fond
// bg-overlay navy (au lieu de tints clairs hardcodes). Drag & drop preserve --
// pas de modification des handlers onDragStart/onDragOver/onDrop.
//
// Lot 2 (2026-09-07) -- `sortie: true` marque ANNULE : ce n'est pas la cinquieme
// etape d'une progression mais une SORTIE LATERALE, atteignable depuis n'importe
// lequel des quatre autres. La colonne reste dans la rangee (decision du cabinet)
// mais se lit differemment : elle est detachee par une gouttiere, grisee, et
// porte la mention explicite.
const COLUMNS: { statut: TicketStatut; accent: string; sortie?: boolean }[] = [
  { statut: 'CREATION_TICKET', accent: 'border-accent' }, // or signature pour "à traiter"
  { statut: 'GENERATION_DOCUMENTS', accent: 'border-warning' }, // ambre : production des actes
  { statut: 'DEROULEMENT_DEMARCHE', accent: 'border-warning' }, // ambre : demarches administratives
  { statut: 'CLOTURE_DOSSIER', accent: 'border-success' }, // emeraude pour validé
  { statut: 'ANNULE', accent: 'border-danger', sortie: true }, // rouge : sortie latérale
];

/**
 * Largeur minimale d'une colonne. En dessous de 5 x 240 px + gouttieres
 * (~1 280 px de contenu), le conteneur defile horizontalement : les cinq
 * colonnes gardent la meme largeur et le meme statut, aucune n'est repliee.
 */
const LARGEUR_MIN_COLONNE = 240;

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
      {/* Lot 2 — la grille se DERIVE de COLUMNS.length : ajouter un statut ajoute
          une colonne, sans toucher a la mise en page. Style inline et non classe
          Tailwind, car Tailwind ne genere pas les classes construites a la volee.
          Le conteneur defile horizontalement quand les cinq colonnes ne tiennent
          plus : aucune n'est repliee ni renvoyee a la ligne. */}
      <div className="-mx-1 overflow-x-auto px-1 pb-2">
        <div
          className="grid gap-4"
          style={{
            gridTemplateColumns: `repeat(${COLUMNS.length}, minmax(${LARGEUR_MIN_COLONNE}px, 1fr))`,
          }}
          data-testid="kanban-grid"
        >
          {COLUMNS.map((col) => (
          <div
            key={col.statut}
            onDragOver={canAct ? onDragOver : undefined}
            onDrop={canAct ? (e) => onDrop(e, col.statut) : undefined}
            data-testid={`kanban-col-${col.statut}`}
            className={`flex flex-col gap-3 ${
              col.sortie ? 'ml-2 border-l border-border pl-4' : ''
            }`}
          >
            <div
              className={`flex items-center justify-between rounded-r-lg border-l-4 ${col.accent} px-3 py-2 ${
                col.sortie ? 'bg-bg-overlay/40' : 'bg-bg-overlay'
              }`}
            >
              <span
                className={`font-heading text-sm font-semibold ${
                  col.sortie ? 'text-fg-muted' : 'text-fg'
                }`}
              >
                {STATUT_LABELS[col.statut]}
              </span>
              <span
                className="rounded-full bg-bg-raised px-2 py-0.5 text-xs font-bold text-fg-muted"
                title={compteurTitre(byStatus[col.statut])}
                data-testid={`kanban-count-${col.statut}`}
              >
                {compteurTexte(byStatus[col.statut])}
              </span>
            </div>
            {/* La sortie laterale se dit, elle ne se devine pas a la couleur du
                liseré : la vue de detail affiche « Statut 2 sur 4 », le tableau
                doit dire la meme chose. */}
            {col.sortie && (
              <p className="-mt-1 px-1 text-[11px] leading-snug text-fg-subtle">
                Sortie du parcours, atteignable depuis les quatre autres statuts —
                ce n'est pas une cinquième étape.
              </p>
            )}
            {/* min-h preserve la zone de drop meme si la colonne est vide.
                Border-dashed border-border-hi/40 = hint discret zone valide. */}
            <div
              className={`flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed p-2 transition-colors ${
                col.sortie ? 'border-border/60 bg-bg-overlay/20' : 'border-border-hi/40'
              }`}
            >
              {byStatus[col.statut].items.map((t) => (
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
                  {canAct && col.statut === 'CREATION_TICKET' && (
                    <button
                      type="button"
                      onClick={async (e) => {
                        e.stopPropagation();
                        try {
                          await onTransition(t.id, 'GENERATION_DOCUMENTS');
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
                        setSensitiveTarget({ ticket: t, target: 'GENERATION_DOCUMENTS' });
                      }}
                      className="inline-flex items-center justify-center gap-1 rounded-lg border border-accent/40 bg-accent/10 px-2 py-1 text-xs font-semibold text-accent transition-colors hover:bg-accent/20"
                    >
                      <Play className="h-3.5 w-3.5" /> Reprendre
                    </button>
                  )}
                </div>
              ))}
              {byStatus[col.statut].items.length === 0 &&
                !byStatus[col.statut].loading && (
                  <div className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-fg-subtle">
                    Aucun ticket
                  </div>
                )}
              {/* « Afficher plus » par colonne : c'est ici que se lit la
                  difference entre ce qui est rendu et ce qui existe. */}
              {byStatus[col.statut].hasMore && (
                <button
                  type="button"
                  onClick={() => onLoadMore(col.statut)}
                  disabled={byStatus[col.statut].loadingMore}
                  data-testid={`kanban-more-${col.statut}`}
                  className="rounded-lg border border-border bg-bg-raised px-2 py-1.5 text-xs font-semibold text-fg-muted transition-colors hover:bg-bg-overlay disabled:opacity-60"
                >
                  {byStatus[col.statut].loadingMore
                    ? 'Chargement…'
                    : `Afficher plus (${restant(byStatus[col.statut])} restants)`}
                </button>
              )}
            </div>
          </div>
          ))}
        </div>
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
          title={sensitiveTarget.target === 'GENERATION_DOCUMENTS' ? 'Reprendre le ticket' : 'Cloturer le ticket'}
          intro={
            sensitiveTarget.target === 'GENERATION_DOCUMENTS'
              ? 'Vous etes sur le point de reprendre (remettre EN COURS) le ticket annule'
              : 'Vous etes sur le point de cloturer le ticket annule'
          }
          reference={sensitiveTarget.ticket.reference}
          minLength={1}
          confirmLabel={sensitiveTarget.target === 'GENERATION_DOCUMENTS' ? 'Reprendre le ticket' : 'Cloturer le ticket'}
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

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useCurrentUser } from '../../store/authStore';
import { ticketService } from '../../services/ticket.service';
import type {
  Ticket,
  TicketPriorite,
  TicketStatut,
  TicketType,
} from '../../types/ticket';
import { STATUT_LABELS } from '../../types/ticket';
import { TicketsHeader } from '../../components/tickets/TicketsHeader';
import { TicketsFilters } from '../../components/tickets/TicketsFilters';
import { KanbanView } from './KanbanView';
import { TableView } from './TableView';
import { NewTicketDrawer } from './NewTicketDrawer';
import { TicketDetailDrawer } from './TicketDetailDrawer';
import { onTicketsChanged } from '../../lib/ticketsRefresh';
import { useTicketColumns, TAILLE_PAGE_COLONNE } from './useTicketColumns';

type View = 'kanban' | 'table';

/** Page de la vue Tableau — meme raison que les colonnes : jamais de plafond muet. */
const TAILLE_PAGE_TABLEAU = 50;

export function TicketsPage() {
  const user = useCurrentUser();
  const [view, setView] = useState<View>('kanban');
  const [query, setQuery] = useState('');
  const [filterType, setFilterType] = useState<TicketType | ''>('');
  const [filterPriorite, setFilterPriorite] = useState<TicketPriorite | ''>('');
  const [filterStatut, setFilterStatut] = useState<TicketStatut | ''>('');
  const [newOpen, setNewOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState<string | null>(null);

  const filtres = useMemo(
    () => ({
      query,
      type: filterType,
      priorite: filterPriorite,
      statut: filterStatut,
    }),
    [query, filterType, filterPriorite, filterStatut],
  );

  // Lot 2 — un chargement PAR COLONNE, avec son propre total en base.
  const { colonnes, totaux, loading, reload, loadMore } = useTicketColumns(filtres);

  // ─── Vue Tableau : sa propre pagination, chargee seulement si elle est
  //     affichee. Elle souffrait du meme plafond de 200 que le Kanban.
  const [tableItems, setTableItems] = useState<Ticket[]>([]);
  const [tableTotal, setTableTotal] = useState(0);
  const [tableLoading, setTableLoading] = useState(false);

  const loadTable = useCallback(
    async (offset: number) => {
      setTableLoading(true);
      try {
        const page = await ticketService.list({
          q: query || undefined,
          types: filterType ? [filterType] : undefined,
          priorites: filterPriorite ? [filterPriorite] : undefined,
          statuts: filterStatut ? [filterStatut] : undefined,
          limit: TAILLE_PAGE_TABLEAU,
          offset,
        });
        setTableTotal(page.total);
        setTableItems((prev) => (offset === 0 ? page.items : [...prev, ...page.items]));
      } finally {
        setTableLoading(false);
      }
    },
    [query, filterType, filterPriorite, filterStatut],
  );

  useEffect(() => {
    if (view !== 'table') return;
    void loadTable(0);
  }, [view, loadTable]);

  const reloadAll = useCallback(async () => {
    await reload();
    if (view === 'table') await loadTable(0);
  }, [reload, loadTable, view]);

  // Rafraichissement apres un transfert de dossier (visibilite #1) :
  //  - signal explicite emis par PendingTransfersPanel a l'acceptation ;
  //  - retour de focus sur l'onglet (cas ou la liste etait deja ouverte).
  // Le montage recharge deja (hook ci-dessus) le cas "j'arrive sur /tickets".
  useEffect(() => {
    const unsubscribe = onTicketsChanged(() => {
      void reloadAll();
    });
    const onFocus = () => void reloadAll();
    window.addEventListener('focus', onFocus);
    return () => {
      unsubscribe();
      window.removeEventListener('focus', onFocus);
    };
  }, [reloadAll]);

  async function handleTransition(
    ticketId: string,
    target: TicketStatut,
    comment?: string,
  ) {
    await ticketService.transition(ticketId, { target, comment });
    await reloadAll();
  }

  const aucunChargement = totaux.total === 0 && !loading;

  return (
    <div className="space-y-5">
      <TicketsHeader
        view={view}
        onViewChange={setView}
        onNewTicket={() => setNewOpen(true)}
        role={user?.role ?? null}
        total={totaux.total}
        enCours={totaux.enCours}
        nouveaux={totaux.nouveaux}
      />

      <TicketsFilters
        query={query}
        onQueryChange={setQuery}
        filterType={filterType}
        onFilterTypeChange={setFilterType}
        filterPriorite={filterPriorite}
        onFilterPrioriteChange={setFilterPriorite}
        filterStatut={filterStatut}
        onFilterStatutChange={setFilterStatut}
      />

      {loading && aucunChargement ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
        </div>
      ) : view === 'kanban' ? (
        <KanbanView
          byStatus={colonnes}
          onSelect={setDetailOpen}
          onTransition={handleTransition}
          onLoadMore={loadMore}
          canAct={user?.role === 'EMPLOYE'}
        />
      ) : (
        <div className="space-y-3">
          <TableView tickets={tableItems} onSelect={setDetailOpen} />
          {tableItems.length < tableTotal && (
            <div className="flex items-center justify-center gap-3 text-xs text-fg-subtle">
              <span>
                {tableItems.length} sur {tableTotal}
              </span>
              <button
                type="button"
                onClick={() => void loadTable(tableItems.length)}
                disabled={tableLoading}
                className="rounded-lg border border-border bg-bg-raised px-3 py-1.5 font-semibold text-fg-muted transition-colors hover:bg-bg-overlay disabled:opacity-60"
              >
                {tableLoading ? 'Chargement…' : 'Afficher plus'}
              </button>
            </div>
          )}
        </div>
      )}

      <NewTicketDrawer
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onCreated={async () => {
          setNewOpen(false);
          await reloadAll();
        }}
      />

      {detailOpen && (
        <TicketDetailDrawer
          ticketId={detailOpen}
          onClose={() => setDetailOpen(null)}
          onChanged={reloadAll}
        />
      )}
    </div>
  );
}

export { STATUT_LABELS, TAILLE_PAGE_COLONNE };

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

type View = 'kanban' | 'table';

export function TicketsPage() {
  const user = useCurrentUser();
  const [view, setView] = useState<View>('kanban');
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [filterType, setFilterType] = useState<TicketType | ''>('');
  const [filterPriorite, setFilterPriorite] = useState<TicketPriorite | ''>('');
  const [filterStatut, setFilterStatut] = useState<TicketStatut | ''>('');
  const [newOpen, setNewOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await ticketService.list({
        q: query || undefined,
        types: filterType ? [filterType] : undefined,
        priorites: filterPriorite ? [filterPriorite] : undefined,
        statuts: filterStatut ? [filterStatut] : undefined,
        limit: 200,
      });
      setTickets(result.items);
    } finally {
      setLoading(false);
    }
  }, [query, filterType, filterPriorite, filterStatut]);

  useEffect(() => {
    load();
  }, [load]);

  // Rafraichissement apres un transfert de dossier (visibilite #1) :
  //  - signal explicite emis par PendingTransfersPanel a l'acceptation ;
  //  - retour de focus sur l'onglet (cas ou la liste etait deja ouverte).
  // Le montage recharge deja (useEffect ci-dessus) le cas "j'arrive sur /tickets".
  useEffect(() => {
    const unsubscribe = onTicketsChanged(() => {
      void load();
    });
    const onFocus = () => void load();
    window.addEventListener('focus', onFocus);
    return () => {
      unsubscribe();
      window.removeEventListener('focus', onFocus);
    };
  }, [load]);

  const byStatus = useMemo(() => {
    const groups: Record<TicketStatut, Ticket[]> = {
      NOUVEAU: [],
      EN_COURS: [],
      CLOTURE: [],
      ANNULE: [],
    };
    for (const t of tickets) {
      groups[t.statut].push(t);
    }
    return groups;
  }, [tickets]);

  async function handleTransition(
    ticketId: string,
    target: TicketStatut,
    comment?: string,
  ) {
    await ticketService.transition(ticketId, { target, comment });
    await load();
  }

  return (
    <div className="space-y-5">
      <TicketsHeader
        view={view}
        onViewChange={setView}
        onNewTicket={() => setNewOpen(true)}
        role={user?.role ?? null}
        total={tickets.length}
        enCours={byStatus.EN_COURS.length}
        nouveaux={byStatus.NOUVEAU.length}
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

      {loading && tickets.length === 0 ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
        </div>
      ) : view === 'kanban' ? (
        <KanbanView
          byStatus={byStatus}
          onSelect={setDetailOpen}
          onTransition={handleTransition}
          canAct={user?.role === 'EMPLOYE'}
        />
      ) : (
        <TableView tickets={tickets} onSelect={setDetailOpen} />
      )}

      <NewTicketDrawer
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onCreated={async () => {
          setNewOpen(false);
          await load();
        }}
      />

      {detailOpen && (
        <TicketDetailDrawer
          ticketId={detailOpen}
          onClose={() => setDetailOpen(null)}
          onChanged={load}
        />
      )}
    </div>
  );
}

export { STATUT_LABELS };

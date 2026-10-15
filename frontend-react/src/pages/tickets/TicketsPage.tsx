import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useCurrentUser } from '../../store/authStore';
import { TexteAide } from '../../components/ui/Aide';
import { ticketService } from '../../services/ticket.service';
import type {
  Ticket,
  TicketPriorite,
  TicketStatut,
  TicketType,
} from '../../types/ticket';
import { STATUT_LABELS } from '../../types/ticket';
import { ChoixVueTickets } from '../../components/tickets/TicketsHeader';
import { resumeTickets, titreTickets } from '../../components/tickets/libellesTickets';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { CardSkeleton, Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Inbox, Plus } from 'lucide-react';
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
  // Lot L4a (E6) : /tickets?ticket=<id> ouvre la fiche du ticket (retour d'une annulation
  // faite depuis un parcours : les documents d'annulation se preparent dans la fiche).
  const [searchParams] = useSearchParams();
  const [detailOpen, setDetailOpen] = useState<string | null>(() => searchParams.get('ticket'));

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(() => loadTable(0));
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
  const estEmploye = user?.role === 'EMPLOYE';
  const supervision = user?.role === 'SUPERVISEUR' || user?.role === 'SUPER_ADMIN';
  const filtreActif = !!(query || filterType || filterPriorite || filterStatut);

  return (
    <Page
      titre={titreTickets(user?.role ?? null)}
      etat={supervision ? <Badge variant="info">Vue globale</Badge> : undefined}
      description={
        <>
          {estEmploye
            ? 'Les opérations juridiques des dossiers dont vous êtes responsable, rangées par statut.'
            : 'Les opérations juridiques de tout le cabinet, rangées par statut, en consultation.'}
          <span className="mt-0.5 block text-xs text-fg-subtle" data-testid="tickets-resume">
            {loading && aucunChargement ? 'Comptage des tickets…' : resumeTickets(totaux.total, totaux.enCours, totaux.nouveaux)}
          </span>
        </>
      }
      actions={<ChoixVueTickets view={view} onViewChange={setView} />}
      action={
        estEmploye ? (
          <Button type="button" icon={<Plus />} onClick={() => setNewOpen(true)}>
            Nouveau ticket
          </Button>
        ) : undefined
      }
      aide={
        <TexteAide cle="liste-tickets" titre="Comment suivre les tickets ?">
          <p>
            Chaque ticket suit une opération en quatre statuts : création, génération des documents,
            déroulement de la démarche, clôture. Cliquez sur un ticket pour voir ce qu’il faut faire et
            ce qui manque ; « Ouvrir la page du ticket » réunit tout.
            {estEmploye
              ? ' Pour une nouvelle opération, cliquez sur « Nouveau ticket ».'
              : ' Seul l’employé responsable du dossier fait avancer un ticket.'}
          </p>
        </TexteAide>
      }
    >
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

      {!loading && aucunChargement && !filtreActif && (
        <Card className="mb-5" padding={false}>
          <EmptyState
            compact
            icon={<Inbox />}
            title="Aucun ticket pour l’instant"
            description={
              <p role="status">
                {estEmploye
                  ? 'Aucun ticket pour l’instant. Cliquez sur « Nouveau ticket » pour ouvrir une opération.'
                  : 'Aucun ticket pour l’instant : les tickets apparaissent ici dès qu’un employé ouvre une opération.'}
              </p>
            }
          />
        </Card>
      )}

      {loading && aucunChargement ? (
        <SqueletteZone libelle="Chargement des tickets…">
          <div className="grid gap-3" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(12rem, 1fr))' }}>
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <Skeleton className="h-9 rounded-lg" />
                <CardSkeleton />
              </div>
            ))}
          </div>
        </SqueletteZone>
      ) : view === 'kanban' ? (
        <KanbanView
          byStatus={colonnes}
          onSelect={setDetailOpen}
          onTransition={handleTransition}
          onLoadMore={loadMore}
          canAct={estEmploye}
        />
      ) : (
        <div className="space-y-3">
          <TableView tickets={tableItems} onSelect={setDetailOpen} chargement={tableLoading && tableItems.length === 0} />
          {tableItems.length < tableTotal && (
            <div className="flex items-center justify-center gap-3 text-sm text-fg-muted">
              <span>
                {tableItems.length} sur {tableTotal}
              </span>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void loadTable(tableItems.length)}
                loading={tableLoading}
              >
                {tableLoading ? 'Chargement…' : 'Afficher plus'}
              </Button>
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
    </Page>
  );
}

export { STATUT_LABELS, TAILLE_PAGE_COLONNE };

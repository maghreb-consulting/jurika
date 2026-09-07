import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KanbanView } from '../KanbanView';
import { ToastProvider } from '../../../components/ui/Toast';
import type { Ticket, TicketStatut } from '../../../types/ticket';
import type { ColonneTickets } from '../useTicketColumns';

/**
 * Lot 2 — la grille du Kanban.
 *
 * <p>Le defaut d'origine : `lg:grid-cols-4` pour cinq colonnes, la cinquieme
 * (« Ticket annulé ») retombant seule en deuxieme rangee. Le correctif derive la
 * grille de `COLUMNS.length` ; ces tests interdisent de refiger un nombre.
 */

function ticket(id: string, statut: TicketStatut): Ticket {
  return {
    id,
    workspaceId: 'ws-1',
    reference: `T-2026-${id}`,
    titre: `Ticket ${id}`,
    type: 'CREATION',
    statut,
    priorite: 'NORMALE',
    dossierId: null,
    assigneId: null,
    creeParId: 'u-0',
    description: null,
    deadline: null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-06-01T10:00:00Z',
    transferred: false,
  } as unknown as Ticket;
}

function colonne(over: Partial<ColonneTickets> = {}): ColonneTickets {
  return {
    items: [],
    total: 0,
    loading: false,
    loadingMore: false,
    hasMore: false,
    ...over,
  };
}

function board(
  over: Partial<Record<TicketStatut, ColonneTickets>> = {},
): Record<TicketStatut, ColonneTickets> {
  return {
    CREATION_TICKET: colonne(),
    GENERATION_DOCUMENTS: colonne(),
    DEROULEMENT_DEMARCHE: colonne(),
    CLOTURE_DOSSIER: colonne(),
    ANNULE: colonne(),
    ...over,
  };
}

function renderKanban(
  byStatus = board(),
  props: Partial<Parameters<typeof KanbanView>[0]> = {},
) {
  const onLoadMore = props.onLoadMore ?? vi.fn();
  render(
    <ToastProvider>
      <KanbanView
        byStatus={byStatus}
        onSelect={props.onSelect ?? vi.fn()}
        onTransition={props.onTransition ?? vi.fn()}
        onLoadMore={onLoadMore}
        canAct={props.canAct ?? true}
      />
    </ToastProvider>,
  );
  return { onLoadMore };
}

const CINQ: TicketStatut[] = [
  'CREATION_TICKET',
  'GENERATION_DOCUMENTS',
  'DEROULEMENT_DEMARCHE',
  'CLOTURE_DOSSIER',
  'ANNULE',
];

describe('KanbanView — les cinq statuts sur une seule rangee', () => {
  it('rend une colonne par statut, sur une grille derivee du nombre de colonnes', () => {
    renderKanban();
    for (const s of CINQ) {
      expect(screen.getByTestId(`kanban-col-${s}`)).toBeInTheDocument();
    }
    const grille = screen.getByTestId('kanban-grid');
    // Cinq pistes, pas quatre : la 5e ne peut pas retomber a la ligne.
    expect(grille.style.gridTemplateColumns).toBe('repeat(5, minmax(240px, 1fr))');
  });

  it('ne figure aucune classe Tailwind de nombre de colonnes', () => {
    renderKanban();
    const grille = screen.getByTestId('kanban-grid');
    // `lg:grid-cols-4` etait le defaut ; une classe dynamique ne serait pas
    // generee par le compilateur, donc ni l'une ni l'autre n'a sa place ici.
    expect(grille.className).not.toMatch(/grid-cols-\d/);
  });

  it('rend le tableau dans un conteneur a defilement horizontal', () => {
    renderKanban();
    const conteneur = screen.getByTestId('kanban-grid').parentElement;
    expect(conteneur?.className).toContain('overflow-x-auto');
  });

  it('affiche le total en base, et « X / total » quand tout n est pas charge', () => {
    renderKanban(
      board({
        ANNULE: colonne({
          items: [ticket('a1', 'ANNULE'), ticket('a2', 'ANNULE')],
          total: 137,
          hasMore: true,
        }),
        CLOTURE_DOSSIER: colonne({ items: [ticket('c1', 'CLOTURE_DOSSIER')], total: 1 }),
      }),
    );
    expect(screen.getByTestId('kanban-count-ANNULE')).toHaveTextContent('2 / 137');
    expect(screen.getByTestId('kanban-count-CLOTURE_DOSSIER')).toHaveTextContent('1');
  });

  it('propose « Afficher plus » avec le restant, et le remonte au parent', async () => {
    const { onLoadMore } = renderKanban(
      board({
        ANNULE: colonne({ items: [ticket('a1', 'ANNULE')], total: 137, hasMore: true }),
      }),
    );
    const bouton = screen.getByTestId('kanban-more-ANNULE');
    expect(bouton).toHaveTextContent('136 restants');
    await userEvent.click(bouton);
    expect(onLoadMore).toHaveBeenCalledWith('ANNULE');
  });

  it('ne propose pas « Afficher plus » sur une colonne entierement chargee', () => {
    renderKanban(
      board({
        GENERATION_DOCUMENTS: colonne({
          items: [ticket('g1', 'GENERATION_DOCUMENTS')],
          total: 1,
        }),
      }),
    );
    expect(screen.queryByTestId('kanban-more-GENERATION_DOCUMENTS')).toBeNull();
  });

  it('dit explicitement que « Ticket annulé » est une sortie, pas une etape', () => {
    renderKanban();
    expect(screen.getByText(/Sortie du parcours/i)).toBeInTheDocument();
    expect(screen.getByText(/pas une cinquième étape/i)).toBeInTheDocument();
    // ... et seule cette colonne porte la mention.
    expect(screen.getAllByText(/Sortie du parcours/i)).toHaveLength(1);
  });
});

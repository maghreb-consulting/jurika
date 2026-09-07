import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmployeeChartsPanel } from '../EmployeeChartsPanel';
import type { Ticket } from '../../../types/ticket';

// On neutralise les graphiques Recharts (SVG non mesurable en jsdom) pour
// tester l'ADAPTATION des donnees (series/etat vide) sans bare-render du SVG.
vi.mock('../charts/DonutChart', () => ({
  DonutChart: ({ data }: { data: unknown[] }) => (
    <div data-testid="donut">{`donut:${data.length}`}</div>
  ),
}));
vi.mock('../DistributionChart', () => ({
  DistributionChart: ({ data }: { data: unknown[] }) => (
    <div data-testid="bars">{`bars:${data.length}`}</div>
  ),
}));

function ticket(partial: Partial<Ticket>): Ticket {
  return {
    id: Math.random().toString(36).slice(2),
    workspaceId: 'w1', reference: 'R', titre: 'T', type: 'CREATION', statut: 'CREATION_TICKET',
    priorite: 'NORMALE', dossierId: null, assigneId: 'u1', creeParId: 'u1',
    description: null, deadline: null, annulationMotif: null, clotureAt: null,
    annuleAt: null, createdAt: '2026-07-01', ...partial,
  };
}

describe('EmployeeChartsPanel', () => {
  it('rend les deux graphiques avec des series derivees de MES tickets', () => {
    render(
      <EmployeeChartsPanel
        tickets={[
          ticket({ statut: 'CREATION_TICKET', type: 'CREATION' }),
          ticket({ statut: 'GENERATION_DOCUMENTS', type: 'MODIFICATION' }),
        ]}
      />,
    );
    // 2 statuts distincts + 2 types distincts.
    expect(screen.getByTestId('donut')).toHaveTextContent('donut:2');
    expect(screen.getByTestId('bars')).toHaveTextContent('bars:2');
  });

  it('affiche l etat vide (Aucune donnee) quand aucun ticket', () => {
    render(<EmployeeChartsPanel tickets={[]} />);
    expect(screen.getAllByText(/Aucune donnée/i)).toHaveLength(2);
    expect(screen.queryByTestId('donut')).not.toBeInTheDocument();
    expect(screen.queryByTestId('bars')).not.toBeInTheDocument();
  });
});

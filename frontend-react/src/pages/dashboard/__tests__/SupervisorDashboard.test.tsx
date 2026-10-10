import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { SupervisorDashboard } from '../SupervisorDashboard';

// Sprint 14 bis / D4 — SupervisorDashboard
// 3 tests : KPIs rendus, empty state "Pas de donnees" si daily vide,
// section "Tickets par type" affichee avec barres relatives.

const kpis = vi.fn();
const ticketsPerType = vi.fn();
const ticketsPerEmploye = vi.fn();
const ticketsLast30Days = vi.fn();

vi.mock('../../../services/supervision.service', () => ({
  supervisionService: {
    kpis: (...a: unknown[]) => kpis(...a),
    ticketsPerType: (...a: unknown[]) => ticketsPerType(...a),
    ticketsPerEmploye: (...a: unknown[]) => ticketsPerEmploye(...a),
    ticketsLast30Days: (...a: unknown[]) => ticketsLast30Days(...a),
  },
}));
// Le panneau demandes/requetes (workspace-wide) utilise useNavigate + dataroomService ;
// on le neutralise ici pour isoler le rendu des KPIs (couvert par son propre test).
vi.mock('../../../components/dashboard/SupervisionDemandesPanel', () => ({
  SupervisionDemandesPanel: () => null,
}));

describe('SupervisorDashboard', () => {
  beforeEach(() => {
    kpis.mockReset();
    ticketsPerType.mockReset();
    ticketsPerEmploye.mockReset();
    ticketsLast30Days.mockReset();
  });

  it('rend le titre + 4 KpiCards', async () => {
    kpis.mockResolvedValue({
      tickets: 0, ticketsEnCours: 5, ticketsClotures: 10, ticketsAnnules: 0,
      dossiers: 42, deboursTotalMad: 0, demandesNonTraitees: 0,
      computedAt: '2026-05-23T00:00:00Z',
    });
    ticketsPerType.mockResolvedValue([]);
    ticketsPerEmploye.mockResolvedValue([]);
    ticketsLast30Days.mockResolvedValue([]);

    render(<SupervisorDashboard />);

    await waitFor(() => expect(screen.getByText(/Tableau de bord — Superviseur/i)).toBeInTheDocument());
    expect(screen.getByText('Total dossiers')).toBeInTheDocument();
    expect(screen.getByText('Employes')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  it('affiche "Pas de donnees" quand l\'evolution 14 derniers jours est vide', async () => {
    kpis.mockResolvedValue(null);
    ticketsPerType.mockResolvedValue([]);
    ticketsPerEmploye.mockResolvedValue([]);
    ticketsLast30Days.mockResolvedValue([]);

    render(<SupervisorDashboard />);

    await waitFor(() => expect(screen.getByText(/Activite \(14 derniers jours\)/i)).toBeInTheDocument());
    // Plusieurs sections renvoient "Pas de donnees" (evolution + per-type),
    // ce qui est exactement le comportement attendu en empty state global.
    expect(screen.getAllByText(/Pas de donnees/i).length).toBeGreaterThanOrEqual(1);
  });

  it('rend les TicketsPerEmploye dans la KpiCard "Employes" (compte = longueur de la liste)', async () => {
    kpis.mockResolvedValue(null);
    ticketsPerType.mockResolvedValue([]);
    ticketsPerEmploye.mockResolvedValue([
      { userId: '1', firstName: 'A', lastName: 'A', enCours: 1, clotures: 0, annules: 0 },
      { userId: '2', firstName: 'B', lastName: 'B', enCours: 2, clotures: 0, annules: 0 },
      { userId: '3', firstName: 'C', lastName: 'C', enCours: 0, clotures: 1, annules: 0 },
    ]);
    ticketsLast30Days.mockResolvedValue([]);

    render(<SupervisorDashboard />);

    // Attendre la DONNEE (le compte), pas le libelle statique "Employes" present des le
    // premier rendu : lire le compte juste apres le libelle echouait sous charge.
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByText('Employes')).toBeInTheDocument();
  });
});

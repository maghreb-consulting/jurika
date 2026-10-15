import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Sprint10SupervisorPanel } from '../Sprint10SupervisorPanel';

// Lot L6b (E9) : le tableau de bord du superviseur plantait a l'ecran ("toFixed" sur undefined)
// quand le serveur rendait un instantane sans certains champs. Une donnee absente s'affiche
// comme absente ("--" ou message), jamais un plantage ni un zero invente.

const getSuperviseur = vi.fn();
vi.mock('../../../services/dashboard.service', () => ({
  dashboardService: { getSuperviseur: (...a: unknown[]) => getSuperviseur(...a) },
}));
// Recharts mesure le conteneur (ResizeObserver absent de jsdom) : graphique neutralise.
vi.mock('recharts', async (orig) => {
  const reel = await orig<typeof import('recharts')>();
  return { ...reel, ResponsiveContainer: () => null };
});

const rendre = () => render(<Sprint10SupervisorPanel />, { wrapper: MemoryRouter });

describe('Sprint10SupervisorPanel', () => {
  beforeEach(() => {
    getSuperviseur.mockReset();
  });

  it('ne plante pas sur un instantane vide et affiche les donnees absentes comme absentes', async () => {
    getSuperviseur.mockResolvedValue({ productiviteIncluse: true });
    rendre();
    expect(await screen.findByText('Temps moyen de clôture')).toBeInTheDocument();
    // Tickets ouverts, clos sur 30 jours, temps moyen, dossiers a risque : aucun chiffre rendu.
    expect(screen.getAllByText('--').length).toBeGreaterThanOrEqual(4);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('ne plante pas quand le temps moyen est nul et rend les autres chiffres', async () => {
    getSuperviseur.mockResolvedValue({
      ticketsOuverts: 7,
      ticketsClos30d: 3,
      tempsMoyenClotureHeures: null,
      dossiersARisque: [{ dossierId: 'd1', raisonSociale: 'ATLAS SARL', motif: 'Échéance dépassée' }],
      productiviteIncluse: true,
    });
    rendre();
    expect(await screen.findByText('7')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('ATLAS SARL')).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
  });

  it('rend le temps moyen quand il est fourni', async () => {
    getSuperviseur.mockResolvedValue({ ticketsOuverts: 1, ticketsClos30d: 2, tempsMoyenClotureHeures: 12.34, productiviteIncluse: true });
    rendre();
    expect(await screen.findByText('12,3 h')).toBeInTheDocument();
  });

  it('dit que les indicateurs n ont pas pu etre charges au lieu de disparaitre', async () => {
    getSuperviseur.mockRejectedValue(new Error('503'));
    rendre();
    expect(await screen.findByText(/indicateurs de suivi n’ont pas pu être chargés/i)).toBeInTheDocument();
  });
});

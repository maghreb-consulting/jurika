import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E8) : changement de forfait et paiement sont refuses a l'employe par le serveur
 * (BillingController, hasRole('SUPERVISEUR')). Atteints par leur adresse, ces ecrans l'expliquent
 * et ne proposent aucune action ; le superviseur garde la grille et le choix du mode.
 */
const etat = vi.hoisted(() => ({ role: 'EMPLOYE' }));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u1', role: etat.role }),
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) => sel({ user: { role: etat.role } }),
}));

const billing = vi.hoisted(() => ({
  getAbonnement: vi.fn(),
  getPricing: vi.fn(),
  apercuChangement: vi.fn(),
  preparerPaiement: vi.fn(),
}));
vi.mock('../../../services/billing.service', () => ({ billingService: billing }));

import { ChangePlanPage } from '../ChangePlanPage';
import { PaymentPage } from '../PaymentPage';

const ABONNEMENT = {
  statut: 'ACTIF', forfait: 'essentiel', forfaitLibelle: 'Essentiel', rythme: 'yearly',
  prochaineEcheance: null, changementAVenir: null, impaye: null, paiementsEnAttente: 0, fonctions: [],
  carteDisponible: false, carteIndisponibleMotif: 'Paiement par carte non encore disponible.', archiveMotif: null,
};

function rendre(ui: React.ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe('Ecrans de facturation reserves au superviseur', () => {
  beforeEach(() => {
    Object.values(billing).forEach((f) => f.mockReset());
    billing.getAbonnement.mockResolvedValue(ABONNEMENT);
    billing.getPricing.mockRejectedValue(new Error('hors ligne'));
  });

  it('employe : changer de forfait est explique, sans grille ni apercu', async () => {
    etat.role = 'EMPLOYE';
    rendre(<ChangePlanPage />);
    expect(await screen.findByText('Réservé au superviseur du cabinet')).toBeInTheDocument();
    expect(screen.queryByTestId('change-plan-picker')).toBeNull();
    expect(billing.apercuChangement).not.toHaveBeenCalled();
  });

  it('employe : payer l’echeance est explique, sans choix du mode', async () => {
    etat.role = 'EMPLOYE';
    rendre(<PaymentPage />);
    expect(await screen.findByText('Réservé au superviseur du cabinet')).toBeInTheDocument();
    expect(screen.queryByTestId('choix-mode-paiement')).toBeNull();
    expect(screen.queryByTestId('payment-submit')).toBeNull();
  });

  it('superviseur : la grille des forfaits et le choix du mode restent proposes', async () => {
    etat.role = 'SUPERVISEUR';
    rendre(<ChangePlanPage />);
    expect(await screen.findByTestId('change-plan-picker')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Changer de forfait ou de rythme' })).toBeInTheDocument();
  });
});

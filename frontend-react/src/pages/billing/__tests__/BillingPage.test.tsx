import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E8) : page Facturation dans la structure unique <Page>. Les operations financieres
 * (payer, changer de forfait, paiements, factures) sont reservees au superviseur par le serveur
 * (BillingController, hasRole('SUPERVISEUR')) : l'employe ne voit ni leurs boutons ni leurs listes,
 * et l'ecran ne les lui demande pas.
 */
const etat = vi.hoisted(() => ({ role: 'EMPLOYE' }));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u1', role: etat.role }),
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) => sel({ user: { role: etat.role } }),
}));

const billing = vi.hoisted(() => ({
  getAbonnement: vi.fn(),
  getUsage: vi.fn(),
  listerPaiements: vi.fn(),
  listerFactures: vi.fn(),
  annulerChangement: vi.fn(),
  telechargerFacture: vi.fn(),
}));
vi.mock('../../../services/billing.service', () => ({ billingService: billing, enregistrerPdf: vi.fn() }));
vi.mock('../../../services/analytics.service', () => ({ emitBusinessEvent: vi.fn() }));

import { BillingPage } from '../BillingPage';

const ABONNEMENT = {
  statut: 'ACTIF',
  forfait: 'professionnel',
  forfaitLibelle: 'Professionnel',
  rythme: 'yearly',
  engagementDebut: '2026-01-01',
  engagementFin: '2027-01-01',
  periodeDebut: '2026-01-01',
  periodeFin: '2027-01-01',
  prochaineEcheance: null,
  changementAVenir: null,
  impaye: {
    echeance: '2026-10-01',
    suspensionLe: '2026-10-16',
    suspendu: false,
    rappelEnvoyeLe: null,
    arretLe: null,
  },
  paiementsEnAttente: 0,
  fonctions: [],
  carteDisponible: false,
  carteIndisponibleMotif: null,
  archiveMotif: null,
};

function rendre() {
  return render(
    <MemoryRouter>
      <BillingPage />
    </MemoryRouter>,
  );
}

describe('BillingPage (L6b)', () => {
  beforeEach(() => {
    Object.values(billing).forEach((f) => f.mockReset());
    billing.getAbonnement.mockResolvedValue(ABONNEMENT);
    billing.getUsage.mockResolvedValue(null);
    billing.listerPaiements.mockResolvedValue([]);
    billing.listerFactures.mockResolvedValue([]);
  });

  it('employe : abonnement et impaye en lecture, sans payer ni changer de forfait', async () => {
    etat.role = 'EMPLOYE';
    rendre();
    expect(await screen.findByRole('heading', { level: 1, name: 'Abonnement et facturation' })).toBeInTheDocument();
    expect(screen.getByTestId('billing-abonnement')).toHaveTextContent('Professionnel');
    expect(screen.getByTestId('billing-impaye')).toHaveTextContent('Seul le superviseur du cabinet peut régler l’échéance');
    expect(screen.queryByTestId('billing-payer-echeance')).toBeNull();
    expect(screen.queryByTestId('billing-changer-forfait')).toBeNull();
    expect(screen.queryByTestId('billing-paiements')).toBeNull();
    expect(screen.queryByTestId('billing-factures')).toBeNull();
    expect(billing.listerPaiements).not.toHaveBeenCalled();
    expect(billing.listerFactures).not.toHaveBeenCalled();
  });

  it('superviseur : payer l’echeance (action principale), changer de forfait, paiements et factures', async () => {
    etat.role = 'SUPERVISEUR';
    rendre();
    const payer = await screen.findByTestId('billing-payer-echeance');
    expect(payer).toHaveAttribute('href', '/app/billing/payment?objet=ECHEANCE');
    expect(screen.getByTestId('billing-changer-forfait')).toHaveAttribute('href', '/app/billing/change-plan');
    expect(screen.getByTestId('billing-impaye')).toHaveTextContent('Payer l’échéance');
    expect(screen.getByTestId('billing-paiements')).toHaveTextContent('Aucun paiement pour l’instant');
    expect(screen.getByTestId('billing-factures')).toHaveTextContent('Aucune facture');
    expect(billing.listerPaiements).toHaveBeenCalledTimes(1);
    expect(billing.listerFactures).toHaveBeenCalledTimes(1);
  });
});

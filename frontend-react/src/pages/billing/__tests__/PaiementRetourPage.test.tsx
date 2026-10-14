import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { PaiementVue } from '../../../types/billing';

vi.mock('../../../services/billing.service', () => ({
  billingService: { getPaiement: vi.fn() },
}));

import { PaiementRetourPage } from '../PaiementRetourPage';
import { billingService } from '../../../services/billing.service';

function paiement(statut: PaiementVue['statut']): PaiementVue {
  return {
    id: 7, workspaceId: 'ws', objet: 'SOUSCRIPTION', forfait: 'professionnel', forfaitLibelle: 'Professionnel',
    rythme: 'monthly', mode: 'CARD', statut,
    montants: { htCentimes: 75000, tauxTva: 20, tvaCentimes: 15000, ttcCentimes: 90000 },
    periodeDebut: null, periodeFin: null, reference: null, recuLe: null, creeLe: '2026-10-14T10:00:00Z',
    acquisLe: null, factureId: null, factureNumero: statut === 'COMPLETED' ? 'F2026-000001' : null,
    factureRefus: null, activationPropagee: true, notes: null,
  };
}

function afficher(issue: string) {
  return render(
    <MemoryRouter initialEntries={[`/app/billing/paiement/retour?issue=${issue}&paiement=7`]}>
      <PaiementRetourPage />
    </MemoryRouter>,
  );
}

/** Lot L7 (E5), RG-PAY-05 : le retour du navigateur ne confirme jamais le paiement. */
describe('PaiementRetourPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('retour "ok" du navigateur avec un paiement encore en attente : pas acquis', async () => {
    vi.mocked(billingService.getPaiement).mockResolvedValue(paiement('PENDING'));
    afficher('ok');
    expect(await screen.findByTestId('paiement-retour-attente')).toHaveTextContent('En attente de la confirmation du CMI');
    expect(screen.queryByTestId('paiement-retour-acquis')).not.toBeInTheDocument();
    expect(billingService.getPaiement).toHaveBeenCalledWith(7);
  });

  it('acquis seulement quand le serveur dit COMPLETED', async () => {
    vi.mocked(billingService.getPaiement).mockResolvedValue(paiement('COMPLETED'));
    afficher('echec');
    expect(await screen.findByTestId('paiement-retour-acquis')).toHaveTextContent('Facture F2026-000001');
  });

  it('refus du CMI', async () => {
    vi.mocked(billingService.getPaiement).mockResolvedValue(paiement('FAILED'));
    afficher('echec');
    expect(await screen.findByTestId('paiement-retour-refuse')).toBeInTheDocument();
  });
});

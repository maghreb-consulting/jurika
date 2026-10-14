import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ImpayeVue } from '../../../types/billing';

/** Lot L7 (E8), article 4.4 du contrat : bandeau de l'échéance impayée (date de suspension, comment payer). */

const getAbonnement = vi.fn();
vi.mock('../../../services/billing.service', () => ({
  billingService: { getAbonnement: () => getAbonnement() },
}));
let role = 'SUPERVISEUR';
vi.mock('../../../store/authStore', () => ({
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) => sel({ user: { role } }),
}));

import { BandeauImpaye } from '../BandeauImpaye';
import { messageImpaye } from '../../../lib/impaye';

const RETARD: ImpayeVue = { echeance: '2026-10-01', suspensionLe: '2026-10-16', suspendu: false, suspenduLe: null,
  rappelEnvoyeLe: '2026-10-01T00:00:00Z', arretLe: null, delaiJours: 15 };

describe('BandeauImpaye', () => {
  beforeEach(() => { getAbonnement.mockReset(); role = 'SUPERVISEUR'; });

  it('dans le délai : date de suspension, dernier jour et lien pour payer (superviseur)', async () => {
    getAbonnement.mockResolvedValue({ impaye: RETARD });
    render(<MemoryRouter><BandeauImpaye /></MemoryRouter>);
    const b = await screen.findByTestId('bandeau-impaye');
    expect(b).toHaveTextContent('Échéance du 1er octobre 2026 non réglée : l’accès sera suspendu le 16 octobre 2026 sans paiement.');
    expect(b).toHaveTextContent('le 15 octobre 2026');
    expect(b).toHaveTextContent('carte bancaire en ligne, virement, chèque ou espèces');
    expect(screen.getByTestId('bandeau-impaye-payer')).toHaveAttribute('href', '/app/billing/payment?objet=ECHEANCE');
  });

  it('employé : prévenir le superviseur, sans lien de paiement', async () => {
    role = 'EMPLOYE';
    getAbonnement.mockResolvedValue({ impaye: RETARD });
    render(<MemoryRouter><BandeauImpaye /></MemoryRouter>);
    expect(await screen.findByTestId('bandeau-impaye')).toHaveTextContent('Seul le superviseur du cabinet peut régler l’échéance');
    expect(screen.queryByTestId('bandeau-impaye-payer')).not.toBeInTheDocument();
  });

  it('sans impayé : aucun bandeau', async () => {
    getAbonnement.mockResolvedValue({ impaye: null });
    render(<MemoryRouter><BandeauImpaye /></MemoryRouter>);
    await Promise.resolve();
    expect(screen.queryByTestId('bandeau-impaye')).not.toBeInTheDocument();
  });

  it('suspendu : accès rétabli dès le paiement constaté, rien de supprimé', () => {
    const m = messageImpaye({ ...RETARD, suspendu: true, suspenduLe: '2026-10-16T00:00:00Z' }, true);
    expect(m?.titre).toBe('Accès au cabinet suspendu : l’échéance du 1er octobre 2026 n’est pas réglée.');
    expect(m?.detail).toContain('rétabli dès le paiement constaté');
    expect(m?.detail).toContain('Rien n’a été supprimé.');
  });
});

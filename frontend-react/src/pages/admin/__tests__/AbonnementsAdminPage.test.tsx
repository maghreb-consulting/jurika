import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { AbonnementAdminVue } from '../../../types/billing';

/** Lot L7 (E8) : SUSPENDU (impayé, accès rétabli au paiement) et ARCHIVE (forfait arrêté) distingués. */

const abonnements = vi.fn();
vi.mock('../../../services/billing.service', () => ({
  billingAdminService: { abonnements: () => abonnements() },
}));
vi.mock('../../../services/admin.service', () => ({
  adminService: { listWorkspaces: () => Promise.resolve([]) },
}));

import { AbonnementsAdminPage } from '../AbonnementsAdminPage';

function ligne(p: Partial<AbonnementAdminVue>): AbonnementAdminVue {
  return {
    workspaceId: 'ws-1', abonnementId: 1, statut: 'ACTIF', forfait: 'essentiel', forfaitLibelle: 'Essentiel',
    rythme: 'monthly', engagementDebut: '2026-09-01', engagementFin: '2027-09-01', periodeDebut: '2026-09-01',
    periodeFin: '2026-10-01', prochaineEcheance: null, changementAVenir: null, archiveMotif: null, surDevis: null,
    impaye: null, journalImpaye: [], ...p,
  };
}

describe('AbonnementsAdminPage', () => {
  beforeEach(() => abonnements.mockReset());

  it('cabinet suspendu pour impayé : date, rappel, journal et voie de la résiliation (article 12.2)', async () => {
    abonnements.mockResolvedValue([ligne({
      statut: 'SUSPENDU',
      impaye: { echeance: '2026-10-01', suspensionLe: '2026-10-16', suspendu: true, suspenduLe: '2026-10-16T00:00:00Z',
        rappelEnvoyeLe: '2026-10-01T00:00:00Z', arretLe: null, delaiJours: 15 },
      journalImpaye: [{ id: 2, echeance: '2026-10-01', evenement: 'SUSPENSION', destinataire: null, detail: null, creeLe: '2026-10-16T00:00:00Z' },
        { id: 1, echeance: '2026-10-01', evenement: 'RAPPEL_ENVOYE', destinataire: 'contact@atlas.invalid', detail: null, creeLe: '2026-10-01T00:00:00Z' }],
    })]);
    render(<MemoryRouter><AbonnementsAdminPage /></MemoryRouter>);
    expect(await screen.findByTestId('abonnement-statut')).toHaveTextContent('Suspendu (impayé)');
    expect(screen.getByText(/Accès suspendu depuis le 16 octobre 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Rappel envoyé le 1er octobre 2026/)).toBeInTheDocument();
    expect(screen.getByTestId('abonnement-journal-impaye')).toHaveTextContent('Rappel envoyé (contact@atlas.invalid)');
    expect(screen.getByRole('link', { name: 'Contrats' })).toHaveAttribute('href', '/admin/contrats');
  });

  it('rappel non envoyé : le super-admin lit que l’accès ne sera pas suspendu', async () => {
    abonnements.mockResolvedValue([ligne({
      impaye: { echeance: '2026-10-01', suspensionLe: '2026-10-16', suspendu: false, suspenduLe: null,
        rappelEnvoyeLe: null, arretLe: null, delaiJours: 15 },
    })]);
    render(<MemoryRouter><AbonnementsAdminPage /></MemoryRouter>);
    expect(await screen.findByTestId('abonnement-statut')).toHaveTextContent('Actif, échéance impayée');
    expect(screen.getByText(/sans rappel, l’accès n’est pas suspendu/)).toBeInTheDocument();
  });

  it('cabinet archivé : motif de l’arrêt du forfait, sans encadré d’impayé', async () => {
    abonnements.mockResolvedValue([ligne({ statut: 'ARCHIVE', archiveMotif: 'RESILIATION' })]);
    render(<MemoryRouter><AbonnementsAdminPage /></MemoryRouter>);
    expect(await screen.findByTestId('abonnement-statut')).toHaveTextContent('Archivé (résiliation)');
    expect(screen.queryByTestId('abonnement-impaye')).not.toBeInTheDocument();
  });
});

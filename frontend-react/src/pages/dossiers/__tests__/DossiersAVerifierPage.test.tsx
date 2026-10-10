import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/** Lot L1 (D1) : le superviseur verifie les dossiers dont le responsable a ete designe d'office. */

const svc = { listRattrapages: vi.fn(), verifierRattrapage: vi.fn(), reaffecter: vi.fn() };
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listRattrapages: (...a: unknown[]) => svc.listRattrapages(...a),
    verifierRattrapage: (...a: unknown[]) => svc.verifierRattrapage(...a),
    reaffecter: (...a: unknown[]) => svc.reaffecter(...a),
  },
}));
vi.mock('../../../services/auth.service', () => ({
  authService: {
    listWorkspaceUsers: async () => [
      { userId: 'e1', firstName: 'Karim', lastName: 'Bennani', role: 'EMPLOYE', status: 'ACTIVE' },
      { userId: 'e3', firstName: 'Youssef', lastName: 'Alami', role: 'EMPLOYE', status: 'ACTIVE' },
      { userId: 'e4', firstName: 'Ancien', lastName: 'Compte', role: 'EMPLOYE', status: 'INACTIVE' },
    ],
  },
}));

import { DossiersAVerifierPage } from '../DossiersAVerifierPage';

const rattrapage = (p: Record<string, unknown> = {}) => ({
  id: 'r1', dossierId: 'd1', raisonSociale: 'ACME SARL', ancienResponsableId: null, ancienResponsableNom: null,
  nouveauResponsableId: 'e1', nouveauResponsableNom: 'Karim Bennani', nature: 'RATTRAPAGE', auteurId: null,
  auteurNom: null, motif: 'Migration V28', createdAt: '2026-10-09T10:00:00Z', responsableActuelId: 'e1',
  responsableActuelNom: 'Karim Bennani', verifiePar: null, verifieParNom: null, verifieLe: null, ...p,
});

beforeEach(() => {
  window.localStorage.clear();
  Object.values(svc).forEach((f) => f.mockReset());
});

const rendre = () => render(<MemoryRouter><DossiersAVerifierPage /></MemoryRouter>);

describe('Dossiers a verifier', () => {
  it('liste les dossiers, leur responsable et l etat de verification, avec ses aides', async () => {
    svc.listRattrapages.mockResolvedValue([
      rattrapage(),
      rattrapage({ id: 'r2', dossierId: 'd2', raisonSociale: 'BETA SARL', verifiePar: 's1', verifieParNom: 'Nadia Superviseure', verifieLe: '2026-10-10T09:00:00Z' }),
    ]);
    rendre();
    expect(await screen.findByRole('link', { name: 'ACME SARL' })).toHaveAttribute('href', '/data-rooms?dossier=d1');
    expect(screen.getByText('1 dossier(s) à vérifier.')).toBeInTheDocument();
    expect(screen.getByText(/Vérifié par Nadia Superviseure/)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Confirmer' })).toHaveLength(1);
    expect(screen.getByRole('note', { name: 'Comment vérifier un dossier ?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pourquoi vérifier ces dossiers ?' })).toBeInTheDocument();
  });

  it('confirmer marque le dossier verifie', async () => {
    svc.listRattrapages.mockResolvedValueOnce([rattrapage()]).mockResolvedValueOnce([
      rattrapage({ verifiePar: 's1', verifieParNom: 'Nadia', verifieLe: '2026-10-10T09:00:00Z' }),
    ]);
    svc.verifierRattrapage.mockResolvedValue({});
    rendre();
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmer' }));
    await waitFor(() => expect(svc.verifierRattrapage).toHaveBeenCalledWith('r1'));
    expect(await screen.findByText('Tous les dossiers ont été vérifiés.')).toBeInTheDocument();
  });

  it('reaffecter exige un employe actif et un motif, puis vaut verification', async () => {
    svc.listRattrapages.mockResolvedValue([rattrapage()]);
    svc.reaffecter.mockResolvedValue(undefined);
    svc.verifierRattrapage.mockResolvedValue({});
    rendre();
    fireEvent.click(await screen.findByRole('button', { name: 'Réaffecter' }));
    const valider = screen.getByRole('button', { name: 'Réaffecter le dossier' });
    expect(valider).toBeDisabled();
    const choix = screen.getByLabelText('Nouvel employé responsable');
    // Ni le responsable actuel, ni un compte inactif.
    expect(screen.queryByRole('option', { name: 'Karim Bennani' })).toBeNull();
    expect(screen.queryByRole('option', { name: 'Ancien Compte' })).toBeNull();
    fireEvent.change(choix, { target: { value: 'e3' } });
    fireEvent.change(screen.getByLabelText('Motif (obligatoire)'), { target: { value: 'Suivi par Youssef depuis 2025' } });
    fireEvent.click(valider);
    await waitFor(() => expect(svc.reaffecter).toHaveBeenCalledWith('d1', 'e3', 'Suivi par Youssef depuis 2025'));
    await waitFor(() => expect(svc.verifierRattrapage).toHaveBeenCalledWith('r1'));
  });
});

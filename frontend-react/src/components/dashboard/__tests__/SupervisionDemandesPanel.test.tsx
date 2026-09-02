import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { SupervisionDemandesPanel } from '../SupervisionDemandesPanel';
import type { DemandeSupervisionRow } from '../../../types/dataroom';

// Vue SUPERVISEUR workspace-wide : demandes des clients + requetes aux clients,
// chaque ligne montrant le nom du dataroom + l'employe responsable (resolus back).
const listSupervisionDemandes = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listSupervisionDemandes: (...a: unknown[]) => listSupervisionDemandes(...a),
  },
}));

const renderPanel = () => render(<SupervisionDemandesPanel />, { wrapper: MemoryRouter });

const demande: DemandeSupervisionRow = {
  id: 'dem1', dossierId: 'd1', raisonSociale: 'ACME SARL', responsableId: 'u1',
  responsableNom: 'Karim Benani', sujet: 'Besoin K-bis', description: null,
  statut: 'NON_TRAITEE', direction: 'CLIENT_TO_EMPLOYE', typeRequete: null,
  createdAt: '2026-07-20',
};
const requete: DemandeSupervisionRow = {
  id: 'req1', dossierId: 'd2', raisonSociale: 'BETA SAS', responsableId: 'u2',
  responsableNom: 'Sara Alaoui', sujet: 'Fournir bail', description: null,
  statut: 'OUVERTE', direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE',
  createdAt: '2026-07-21',
};

describe('SupervisionDemandesPanel', () => {
  beforeEach(() => {
    listSupervisionDemandes.mockReset();
  });

  it('rend les deux blocs avec dataroom + employe resolus par jointure', async () => {
    listSupervisionDemandes.mockImplementation((direction: string) =>
      Promise.resolve(direction === 'CLIENT_TO_EMPLOYE' ? [demande] : [requete]),
    );

    renderPanel();

    await waitFor(() => expect(screen.getByText('Demandes des clients')).toBeInTheDocument());
    expect(screen.getByText('Requetes aux clients')).toBeInTheDocument();

    // Demande : sujet + dataroom (raison sociale, pas l'UUID) + employe.
    expect(screen.getByText('Besoin K-bis')).toBeInTheDocument();
    expect(screen.getByText('ACME SARL')).toBeInTheDocument();
    expect(screen.getByText('Karim Benani')).toBeInTheDocument();

    // Requete : sujet + dataroom + employe.
    expect(screen.getByText('Fournir bail')).toBeInTheDocument();
    expect(screen.getByText('BETA SAS')).toBeInTheDocument();
    expect(screen.getByText('Sara Alaoui')).toBeInTheDocument();

    // Chaque direction est bien interrogee.
    expect(listSupervisionDemandes).toHaveBeenCalledWith('CLIENT_TO_EMPLOYE');
    expect(listSupervisionDemandes).toHaveBeenCalledWith('EMPLOYE_TO_CLIENT');
  });

  it('affiche un etat vide propre par bloc', async () => {
    listSupervisionDemandes.mockResolvedValue([]);

    renderPanel();

    await waitFor(() =>
      expect(screen.getByText(/Aucune demande client dans le cabinet/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/Aucune requete envoyee aux clients/i)).toBeInTheDocument();
  });
});

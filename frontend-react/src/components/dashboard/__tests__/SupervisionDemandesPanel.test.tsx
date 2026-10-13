import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
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
  createdAt: '2026-07-20', etatSuivi: 'NON_TRAITEE',
};
const requete: DemandeSupervisionRow = {
  id: 'req1', dossierId: 'd2', raisonSociale: 'BETA SAS', responsableId: 'u2',
  responsableNom: 'Sara Alaoui', sujet: 'Fournir bail', description: null,
  statut: 'OUVERTE', direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE',
  createdAt: '2026-07-21', etatSuivi: 'NON_TRAITEE',
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
    expect(screen.getByText('Requêtes du cabinet aux clients')).toBeInTheDocument();

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
    expect(screen.getByText(/Aucune requête envoyée aux clients/i)).toBeInTheDocument();
  });

  // Lot L5 (E9, RG-COM-07) : les requetes sont comptees sur les trois etats de
  // suivi rendus par le serveur ; le statut detaille reste lisible en second.
  it('compte demandes et requetes sur les trois etats de suivi', async () => {
    const repondue: DemandeSupervisionRow = {
      ...requete, id: 'req2', sujet: 'Signer le PV', statut: 'REPONDUE', etatSuivi: 'EN_COURS',
    };
    const completer: DemandeSupervisionRow = {
      ...requete, id: 'req3', sujet: 'Bail signe', statut: 'A_COMPLETER', etatSuivi: 'EN_COURS',
    };
    const close: DemandeSupervisionRow = {
      ...requete, id: 'req4', sujet: 'CIN', statut: 'CLOTUREE', etatSuivi: 'TRAITEE',
    };
    listSupervisionDemandes.mockImplementation((direction: string) =>
      Promise.resolve(direction === 'CLIENT_TO_EMPLOYE' ? [demande] : [requete, repondue, completer, close]),
    );

    renderPanel();

    await waitFor(() => expect(screen.getByText('Signer le PV')).toBeInTheDocument());
    const enCours = screen.getAllByTestId('compteur-etat-EN_COURS');
    expect(enCours).toHaveLength(1);
    expect(enCours[0]).toHaveTextContent('En cours · 2');
    expect(screen.getAllByTestId('compteur-etat-TRAITEE')[0]).toHaveTextContent('Traitée · 1');
    expect(screen.getAllByTestId('compteur-etat-NON_TRAITEE')).toHaveLength(2);
    // Statut detaille de la requete, en second.
    expect(screen.getByText('En attente de validation')).toBeInTheDocument();
    expect(screen.getByText('À compléter')).toBeInTheDocument();
    // Aide de l'ecran.
    expect(screen.getByRole('note', { name: 'Suivi des demandes et des requêtes' })).toBeInTheDocument();
  });

  it('un statut sans etat de suivi est montre comme inconnu, jamais range d office', async () => {
    listSupervisionDemandes.mockImplementation((direction: string) =>
      Promise.resolve(direction === 'CLIENT_TO_EMPLOYE' ? [] : [{ ...requete, statut: 'ETRANGE', etatSuivi: null }]),
    );
    renderPanel();
    await waitFor(() => expect(screen.getByText('Fournir bail')).toBeInTheDocument());
    expect(screen.getByText('État inconnu · 1')).toBeInTheDocument();
    expect(screen.queryByTestId('compteur-etat-NON_TRAITEE')).not.toBeInTheDocument();
  });

  it('une requete s ouvre sur l onglet « Requêtes du cabinet », une demande sur « Demandes du client » (lot L6)', async () => {
    listSupervisionDemandes.mockImplementation((direction: string) =>
      Promise.resolve(direction === 'CLIENT_TO_EMPLOYE' ? [demande] : [requete]),
    );
    function Lieu() {
      const l = useLocation();
      return <p data-testid="lieu">{l.pathname + l.search}</p>;
    }
    const monter = () => render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<SupervisionDemandesPanel />} />
          <Route path="/data-rooms" element={<Lieu />} />
        </Routes>
      </MemoryRouter>,
    );
    const { unmount } = monter();
    fireEvent.click(await screen.findByText('Fournir bail'));
    expect(await screen.findByTestId('lieu')).toHaveTextContent('/data-rooms?dossier=d2&tab=requetes');
    unmount();
    monter();
    fireEvent.click(await screen.findByText('Besoin K-bis'));
    expect(await screen.findByTestId('lieu')).toHaveTextContent('/data-rooms?dossier=d1&tab=demandes');
  });
});

import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E6) : l'espace du client reste simple, sur la coquille commune.
 *  - un seul titre (la societe), " Faire une demande " en action principale ;
 *  - pendant l'essai gratuit : aucun bouton de telechargement (apercu seulement, RG-ESS-05) ;
 *  - hors essai, sans permission de telecharger : pas de bouton refuse par le serveur ;
 *  - les demandes ne montrent aucun identifiant tronque.
 */

const svc = {
  listDossiers: vi.fn(),
  getMyPermissions: vi.fn(),
  getJuridique: vi.fn(),
  listDemandesByDossier: vi.fn(),
  createDemande: vi.fn(),
};
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => {
      const f = (svc as Record<string, (...x: unknown[]) => unknown>)[p];
      return f ? f(...a) : Promise.resolve([]);
    },
  }),
}));
vi.mock('../../../lib/useDataroomSocket', () => ({ useDataroomSocket: () => undefined }));
let enEssai: boolean | null = false;
vi.mock('../../../store/essaiStore', () => ({ useEnEssai: () => enEssai }));

import { useAuthStore } from '../../../store/authStore';
import { DataroomPage } from '../DataroomPage';

const doc = {
  id: 'doc1', dossierId: 'd1', ticketId: 't1', documentType: 'STATUTS', title: 'Statuts de la société', version: 2,
  current: true, filename: 'statuts.pdf', contentType: 'application/pdf', sizeBytes: 10, createdAt: '2026-09-01', replacedAt: null,
};

function afficher() {
  return render(
    <MemoryRouter initialEntries={['/data-rooms']}>
      <DataroomPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  window.localStorage.clear();
  Object.values(svc).forEach((f) => f.mockReset());
  enEssai = false;
  useAuthStore.setState({
    user: { userId: 'c1', workspaceId: 'w1', email: 'client@x.ma', role: 'CLIENT' },
    isAuthenticated: true,
  } as never);
  svc.listDossiers.mockResolvedValue([{ id: 'd1', raisonSociale: 'ATLAS SARL', formeJuridique: 'SARL', ice: null, ville: null, statut: 'ACTIVE' }]);
  svc.getMyPermissions.mockResolvedValue({ permConsultation: true, permDownload: true, permPrint: true, permDepot: true, permDemandes: true });
  svc.getJuridique.mockResolvedValue({ dossierId: 'd1', raisonSociale: 'ATLAS SARL', documentsEnVigueur: [doc], dossiersParTicket: [], historiqueOperations: [] });
  svc.listDemandesByDossier.mockResolvedValue([
    { id: 'dem-1234-5678-9abc', dossierId: 'd1', sujet: 'Transfert du siège', description: null, statut: 'EN_COURS', createdAt: '2026-10-01T10:00:00Z', traiteAt: null },
  ]);
  svc.createDemande.mockResolvedValue(undefined);
});

describe('Espace du client', () => {
  it('titre de la societe, documents en derniere version, telechargement permis', async () => {
    afficher();
    expect(await screen.findByRole('heading', { level: 1, name: 'ATLAS SARL' })).toBeInTheDocument();
    expect(screen.getByText('Statuts de la société')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Télécharger' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Votre espace documentaire' })).toBeInTheDocument();
    expect(screen.getByTestId('lien-requetes-cabinet')).toHaveAttribute('href', '/mes-requetes');
  }, 15000);

  it('pendant l essai : apercu seulement, aucun bouton de telechargement', async () => {
    enEssai = true;
    afficher();
    expect(await screen.findByText('Statuts de la société')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Voir' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Télécharger' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Téléchargement indisponible' })).toBeInTheDocument();
    expect(screen.getByText(/Télécharger \(indisponible pendant l’essai gratuit\)/)).toBeInTheDocument();
  }, 15000);

  it('sans permission de telecharger : pas de bouton que le serveur refuserait', async () => {
    svc.getMyPermissions.mockResolvedValue({ permConsultation: true, permDownload: false, permPrint: false, permDepot: false, permDemandes: true });
    afficher();
    expect(await screen.findByText('Statuts de la société')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Télécharger' })).not.toBeInTheDocument();
    expect(screen.getByText(/Télécharger \(non autorisé\)/)).toBeInTheDocument();
  }, 15000);

  it('« Faire une demande » ouvre le formulaire ; l historique ne montre aucun identifiant', async () => {
    afficher();
    fireEvent.click(await screen.findByRole('button', { name: 'Faire une demande' }));
    const historique = await screen.findByRole('heading', { name: 'Historique des demandes' });
    expect(historique).toBeInTheDocument();
    expect(screen.getByText('Transfert du siège')).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('dem-1234');

    fireEvent.change(screen.getByLabelText('Sujet'), { target: { value: 'Augmentation de capital' } });
    const formulaire = screen.getByLabelText('Sujet').closest('form') as HTMLElement;
    fireEvent.click(within(formulaire).getByRole('button', { name: 'Envoyer' }));
    await waitFor(() => expect(svc.createDemande).toHaveBeenCalledWith(
      expect.objectContaining({ dossierId: 'd1', sujet: 'Augmentation de capital' }),
    ));
    expect(await screen.findByText('Demande envoyée')).toBeInTheDocument();
  }, 15000);
});

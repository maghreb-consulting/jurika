import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E6) : liste et detail de la Data Room sur la coquille commune.
 *  - titre (h1) et fil d'Ariane portes par Page, une seule fois ;
 *  - onglets Actifs / Suspendus avec leurs comptes, etat vide qui dit quoi faire ;
 *  - detail : sections en onglets, action principale "Inviter un client" pour l'employe ;
 *  - backlog L0 : le superviseur ne voit aucun bouton que le serveur lui refuse
 *    (inviter, retirer l'acces, suspendre depuis la liste, transferer).
 */

const svc = {
  listDossiers: vi.fn(),
  getSettings: vi.fn(),
  mesDroits: vi.fn(),
  getJuridique: vi.fn(),
};
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => {
      const f = (svc as Record<string, (...x: unknown[]) => unknown>)[p];
      return f ? f(...a) : Promise.resolve([]);
    },
  }),
}));
const auth = { getDossierClient: vi.fn(), listWorkspaceUsers: vi.fn() };
vi.mock('../../../services/auth.service', () => ({
  authService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => {
      const f = (auth as Record<string, (...x: unknown[]) => unknown>)[p];
      return f ? f(...a) : Promise.resolve([]);
    },
  }),
}));
vi.mock('../../../services/tracabilite.service', () => ({
  tracabiliteService: { entityHistory: async () => ({ items: [], total: 0 }) },
}));
vi.mock('../../../store/essaiStore', () => ({ useEnEssai: () => false }));

import { useAuthStore } from '../../../store/authStore';
import { DataroomPage } from '../DataroomPage';

const dossier = (p: Record<string, unknown> = {}) => ({
  id: 'd1', raisonSociale: 'ATLAS SARL', reference: '2026-0001 — ATLAS SARL', formeJuridique: 'SARL',
  ice: '001122334455667', ville: 'Casablanca', statut: 'ACTIVE', accessStatus: 'ACTIVE', ...p,
});
const reglages = (p: Record<string, unknown> = {}) => ({
  dossierId: 'd1', accessStatus: 'ACTIVE', permDownload: true, permPrint: false, permDepot: false,
  clientLinkToken: 'tok', accessCount: 2, lastAccessedAt: null, permConsultation: true, permDemandes: true, ...p,
});

function connecter(role: 'EMPLOYE' | 'SUPERVISEUR') {
  useAuthStore.setState({
    user: { userId: 'u1', workspaceId: 'w1', email: 'x@x.ma', role },
    isAuthenticated: true,
  } as never);
}

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
  Object.values(auth).forEach((f) => f.mockReset());
  svc.listDossiers.mockResolvedValue([dossier(), dossier({ id: 'd2', raisonSociale: 'BETA SA', reference: null, accessStatus: 'SUSPENDED' })]);
  svc.getSettings.mockImplementation(async (id: string) => reglages({ dossierId: id, accessStatus: id === 'd2' ? 'SUSPENDED' : 'ACTIVE' }));
  svc.mesDroits.mockResolvedValue({ peutSupprimerDocuments: false, motif: null, peutReglerAccesClient: true });
  svc.getJuridique.mockResolvedValue({
    dossierId: 'd1', raisonSociale: 'ATLAS SARL', statut: 'ACTIVE', documentsEnVigueur: [], historiqueOperations: [], dossiersParTicket: [],
  });
  auth.getDossierClient.mockResolvedValue(null);
  auth.listWorkspaceUsers.mockResolvedValue([]);
});

describe('Liste des Data Rooms', () => {
  it('un seul titre, les comptes par onglet, la recherche et l etat vide explicatif', async () => {
    connecter('EMPLOYE');
    afficher();
    expect(screen.getByRole('heading', { level: 1, name: 'Mes Data Rooms' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Ouvrir la Data Room de ATLAS SARL' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /BETA SA/ })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Actifs/ })).toHaveTextContent('1');
    expect(screen.getByRole('tab', { name: /Suspendus/ })).toHaveTextContent('1');
    expect(screen.getByTestId('reference-dossier')).toHaveTextContent('2026-0001 — ATLAS SARL');
    expect(screen.getByRole('note', { name: 'Vos Data Rooms' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /Suspendus/ }));
    expect(await screen.findByRole('button', { name: 'Ouvrir la Data Room de BETA SA' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Rechercher une Data Room'), { target: { value: 'introuvable' } });
    expect(screen.getByText('Aucune Data Room ne correspond à la recherche')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Effacer la recherche' }));
    expect(await screen.findByRole('button', { name: 'Ouvrir la Data Room de BETA SA' })).toBeInTheDocument();
  }, 15000);

  it('employe : suspendre depuis la liste ; superviseur : consultation, aucun bouton de suspension', async () => {
    connecter('EMPLOYE');
    const { unmount } = afficher();
    expect(await screen.findByRole('button', { name: 'Suspendre l’accès du client de ATLAS SARL' })).toBeInTheDocument();
    unmount();

    connecter('SUPERVISEUR');
    afficher();
    expect(screen.getByRole('heading', { level: 1, name: 'Toutes les Data Rooms' })).toBeInTheDocument();
    expect(screen.getByText('Consultation')).toBeInTheDocument();
    await screen.findByRole('button', { name: 'Ouvrir la Data Room de ATLAS SARL' });
    await waitFor(() => expect(svc.getSettings).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: /Suspendre l’accès du client/ })).not.toBeInTheDocument();
  }, 15000);
});

describe('Detail d une Data Room', () => {
  it('employe : fil d Ariane, sections en onglets, inviter un client en action principale', async () => {
    connecter('EMPLOYE');
    afficher();
    fireEvent.click(await screen.findByRole('button', { name: 'Ouvrir la Data Room de ATLAS SARL' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'ATLAS SARL' })).toBeInTheDocument();
    const fil = screen.getByRole('navigation', { name: 'Fil d’Ariane' });
    expect(within(fil).getByRole('link', { name: 'Mes Data Rooms' })).toHaveAttribute('href', '/data-rooms');
    expect(screen.getByTestId('reference-dossier-entete')).toHaveTextContent('Dossier 2026-0001 — ATLAS SARL');
    const sections = screen.getByRole('tablist', { name: 'Sections de la Data Room' });
    expect(within(sections).getAllByRole('tab').map((t) => t.textContent)).toEqual([
      'Dossier juridique', 'Dépôts du client', 'Demandes du client', 'Requêtes du cabinet',
    ]);
    expect(await screen.findByRole('button', { name: 'Inviter un client' })).toBeInTheDocument();
    expect(screen.getByText(/Aucun client invité sur ce dossier/)).toBeInTheDocument();

    fireEvent.click(within(sections).getByRole('tab', { name: 'Demandes du client' }));
    expect(screen.getByTestId('aide-onglet-dataroom')).toHaveTextContent('Les demandes que le client adresse au cabinet');

    fireEvent.click(screen.getByRole('button', { name: 'Retour aux Data Rooms' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Mes Data Rooms' })).toBeInTheDocument();
  }, 15000);

  it('superviseur : ni inviter, ni retirer l acces, ni transferer', async () => {
    connecter('SUPERVISEUR');
    auth.getDossierClient.mockResolvedValue({ userId: 'c1', firstName: 'Karim', lastName: 'Alaoui', email: 'k@x.ma', status: 'ACTIVE' });
    afficher();
    fireEvent.click(await screen.findByRole('button', { name: 'Ouvrir la Data Room de ATLAS SARL' }));
    expect(await screen.findByText('Karim Alaoui')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Inviter un client' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Retirer l’accès/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Transférer/ })).not.toBeInTheDocument();
    // Le superviseur regle l'acces du client (le serveur l'y autorise).
    expect(await screen.findByRole('button', { name: 'Suspendre l’accès' })).toBeInTheDocument();
  }, 15000);
});

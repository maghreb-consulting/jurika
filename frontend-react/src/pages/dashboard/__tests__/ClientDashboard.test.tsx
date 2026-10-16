import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ClientDashboard } from '../ClientDashboard';

/**
 * Tableau de bord CLIENT : l'ACTION attendue du client (requetes du conseiller a traiter),
 * l'avancement de ses operations, puis ses echanges, sur donnees REELLES agregees par dossier.
 *
 * Lot L6b (E17) : les deux anneaux (sans chiffres) sont retires ; "Operations en cours" et la
 * frise des etapes les remplacent. Couvre : compteurs, liste « A faire » en tete avec lien
 * /mes-requetes, avancement, etats vides propres, echec dit.
 */

const listDossiers = vi.fn();
const listDemandesByDossier = vi.fn();
const listRequetesByDossier = vi.fn();
const listDepots = vi.fn();
const getClient = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: (...a: unknown[]) => listDossiers(...a),
    listDemandesByDossier: (...a: unknown[]) => listDemandesByDossier(...a),
    listRequetesByDossier: (...a: unknown[]) => listRequetesByDossier(...a),
    listDepots: (...a: unknown[]) => listDepots(...a),
  },
}));
vi.mock('../../../services/dashboard.service', () => ({
  dashboardService: { getClient: (...a: unknown[]) => getClient(...a) },
}));
vi.mock('../../../store/authStore', () => {
  const user = { userId: 'c1', workspaceId: 'w1', email: 'client@acme.ma', role: 'CLIENT' };
  return {
    useCurrentUser: () => user,
    // Page (fil d'Ariane par defaut) lit le role dans le magasin.
    useAuthStore: (sel: (s: { user: typeof user }) => unknown) => sel({ user }),
  };
});

function demande(id: string, statut: string, sujet: string, createdAt: string) {
  return {
    id, dossierId: 'dos-1', soumisPar: 'c1', sujet, description: null, statut,
    ticketId: null, prisEnChargePar: null, noteInterne: null, traiteAt: null, createdAt,
  };
}

function requete(id: string, statut: string, sujet: string, createdAt: string) {
  return {
    id, dossierId: 'dos-1', soumisPar: 'e1', sujet, description: null, statut,
    ticketId: null, prisEnChargePar: null, noteInterne: null, traiteAt: null, createdAt,
    direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE', reponduAt: null, clotureAt: null,
    noteClient: null,
  };
}

function depot(id: string, title: string, createdAt: string) {
  return {
    id, title, filename: `${title}.pdf`, contentType: 'application/pdf',
    sizeBytes: 1024, uploadedBy: 'c1', createdAt,
  };
}

const carte = (libelle: string) => screen.getByText(libelle, { selector: 'p, p > a' }).closest('p')?.parentElement?.parentElement as HTMLElement;

describe('ClientDashboard', () => {
  beforeEach(() => {
    listDossiers.mockReset();
    listDemandesByDossier.mockReset();
    listRequetesByDossier.mockReset();
    listDepots.mockReset();
    getClient.mockReset();
  });

  it('agrege demandes + requetes + depots par dossier : compteurs, « A faire », avancement', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([
      demande('d1', 'NON_TRAITEE', 'Demande A', '2026-06-01T10:00:00Z'),
      demande('d2', 'EN_COURS', 'Demande B', '2026-06-02T10:00:00Z'),
      demande('d3', 'TRAITEE', 'Demande C', '2026-06-03T10:00:00Z'),
    ]);
    listRequetesByDossier.mockResolvedValue([
      requete('r1', 'OUVERTE', 'Fournir le RIB', '2026-06-05T10:00:00Z'),
      requete('r2', 'A_COMPLETER', 'CIN illisible', '2026-06-06T10:00:00Z'),
      requete('r3', 'REPONDUE', 'Attestation', '2026-06-07T10:00:00Z'),
      requete('r4', 'CLOTUREE', 'Bail', '2026-06-08T10:00:00Z'),
    ]);
    listDepots.mockResolvedValue([
      depot('p1', 'Contrat de bail', '2026-06-09T10:00:00Z'),
      depot('p2', 'RIB', '2026-06-10T10:00:00Z'),
    ]);
    getClient.mockResolvedValue({
      mesDossiers: [],
      mesTicketsEnCours: [
        { id: 't1', reference: 'TK-1', titre: 'Création ACME SARL', statut: 'GENERATION_DOCUMENTS', priorite: 'NORMALE', createdAt: '2026-06-01T00:00:00Z' },
      ],
      mesDocumentsRecents: [
        { id: 'doc-1', title: 'Statuts ACME', filename: 'statuts.pdf', createdAt: '2026-06-10T09:00:00Z' },
      ],
      mesEcheancesAVenir: [], generatedAt: '2026-06-30T00:00:00Z',
    });

    render(
      <MemoryRouter>
        <ClientDashboard />
      </MemoryRouter>,
    );

    // Scope client : chaque source agregee sur SON dossier.
    await waitFor(() => expect(listRequetesByDossier).toHaveBeenCalledWith('dos-1'));
    expect(listDemandesByDossier).toHaveBeenCalledWith('dos-1');
    expect(listDepots).toHaveBeenCalledWith('dos-1');

    // Indicateurs : a faire = OUVERTE + A_COMPLETER = 2 ; demandes en cours = 2 ; operations = 1.
    expect(await screen.findByText('À faire pour mon conseiller', { selector: 'p > a' })).toBeInTheDocument();
    expect(carte('À faire pour mon conseiller')).toHaveTextContent('2');
    expect(carte('Mes demandes en cours')).toHaveTextContent('2');
    expect(carte('Opérations en cours')).toHaveTextContent('1');
    // Retires : anneaux, compteurs "Documents disponibles" et "Depots effectues".
    expect(screen.queryByText('Requêtes du cabinet par statut')).not.toBeInTheDocument();
    expect(screen.queryByText('Mes demandes par statut')).not.toBeInTheDocument();
    expect(screen.queryByText('Documents disponibles')).not.toBeInTheDocument();

    // Liste « A faire » : les 2 requetes actionnables sont listees.
    expect(screen.getByText('Fournir le RIB')).toBeInTheDocument();
    expect(screen.getByText('CIN illisible')).toBeInTheDocument();
    const lien = screen.getByRole('link', { name: /Répondre aux requêtes du cabinet/i });
    expect(lien).toHaveAttribute('href', '/mes-requetes');

    // Avancement : l'etape en cours de l'operation est mise en avant.
    const etapes = screen.getByRole('list', { name: 'Étapes de l’opération' });
    const courante = within(etapes).getAllByRole('listitem').find((li) => li.getAttribute('aria-current') === 'step');
    expect(courante).toHaveTextContent('Préparation des actes');

    // Documents + depots recents.
    expect(screen.getByText('Statuts ACME')).toBeInTheDocument();
    expect(screen.getByText('Contrat de bail')).toBeInTheDocument();
  });

  it('etats vides propres quand le client n a rien', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([]);
    listRequetesByDossier.mockResolvedValue([]);
    listDepots.mockResolvedValue([]);
    getClient.mockResolvedValue({ mesDossiers: [], mesTicketsEnCours: [], mesDocumentsRecents: [], mesEcheancesAVenir: [], generatedAt: '' });

    render(
      <MemoryRouter>
        <ClientDashboard />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText(/Rien à faire pour le moment/i)).toBeInTheDocument());
    expect(screen.getByText(/^Aucune demande envoyée\./)).toBeInTheDocument();
    expect(screen.getByText(/^Aucun document ni dépôt récent\./)).toBeInTheDocument();
    expect(screen.getByText(/^Aucune opération en cours\./)).toBeInTheDocument();
    expect(carte('À faire pour mon conseiller')).toHaveTextContent('0');
  });

  it('service du tableau de bord indisponible : le dit, sans compter zero operation', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([]);
    listRequetesByDossier.mockRejectedValue(new Error('503'));
    listDepots.mockResolvedValue([]);
    getClient.mockRejectedValue(new Error('503'));

    render(
      <MemoryRouter>
        <ClientDashboard />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Certaines informations n’ont pas pu être chargées')).toBeInTheDocument();
    expect(carte('Opérations en cours')).toHaveTextContent('--');
    expect(screen.getByText(/L’avancement de vos opérations n’a pas pu être chargé/)).toBeInTheDocument();
  });
});

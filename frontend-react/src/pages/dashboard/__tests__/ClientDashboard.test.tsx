import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ClientDashboard } from '../ClientDashboard';

/**
 * Dashboard CLIENT enrichi (2026-07-20) — met en avant l'ACTION attendue du
 * client (requetes du conseiller a traiter) + graphiques + depots, sur donnees
 * REELLES agregees par dossier. Couvre : compteurs, donuts, liste « A faire »
 * en tete avec lien /mes-requetes, et etats vides propres.
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
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'c1', workspaceId: 'w1', email: 'client@acme.ma', role: 'CLIENT' }),
}));

// Recharts SVG non mesurable en jsdom : on neutralise le donut pour tester la
// serie derivee (nombre de tranches) sans bare-render du SVG.
vi.mock('../../../components/dashboard/charts/DonutChart', () => ({
  DonutChart: ({ data }: { data: unknown[] }) => (
    <div data-testid="donut">{`donut:${data.length}`}</div>
  ),
}));

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

describe('ClientDashboard', () => {
  beforeEach(() => {
    listDossiers.mockReset();
    listDemandesByDossier.mockReset();
    listRequetesByDossier.mockReset();
    listDepots.mockReset();
    getClient.mockReset();
  });

  it('agrege demandes + requetes + depots par dossier et affiche compteurs, donuts et « A faire »', async () => {
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
      mesDossiers: [], mesTicketsEnCours: [],
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

    // KPIs.
    expect(screen.getByText('Requetes a traiter')).toBeInTheDocument();
    expect(screen.getByText('Mes demandes en cours')).toBeInTheDocument();
    expect(screen.getByText('Documents disponibles')).toBeInTheDocument();
    expect(screen.getByText('Depots effectues')).toBeInTheDocument();

    // Requetes a traiter = OUVERTE + A_COMPLETER = 2 (KPI + badge liste).
    expect(screen.getAllByText('2').length).toBeGreaterThanOrEqual(2);

    // Donuts : requetes = 3 groupes (A_FAIRE/ATTENTE/TERMINE), demandes = 3 statuts.
    const donuts = screen.getAllByTestId('donut');
    expect(donuts).toHaveLength(2);
    expect(donuts[0]).toHaveTextContent('donut:3'); // requetes
    expect(donuts[1]).toHaveTextContent('donut:3'); // demandes

    // Liste « A faire » : les 2 requetes actionnables sont listees.
    expect(screen.getByText('Fournir le RIB')).toBeInTheDocument();
    expect(screen.getByText('CIN illisible')).toBeInTheDocument();

    // Lien vers la page d'action.
    const lien = screen.getByRole('link', { name: /Demandes de mon conseiller/i });
    expect(lien).toHaveAttribute('href', '/mes-requetes');

    // Documents + depots recents.
    expect(screen.getByText('Statuts ACME')).toBeInTheDocument();
    expect(screen.getByText('Contrat de bail')).toBeInTheDocument();
  });

  it('etats vides propres quand le client n a rien (compteurs 0, donuts « Aucune donnee »)', async () => {
    listDossiers.mockResolvedValue([{ id: 'dos-1', raisonSociale: 'ACME SARL' }]);
    listDemandesByDossier.mockResolvedValue([]);
    listRequetesByDossier.mockResolvedValue([]);
    listDepots.mockResolvedValue([]);
    getClient.mockResolvedValue(null);

    render(
      <MemoryRouter>
        <ClientDashboard />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByText(/Rien a faire pour le moment/i)).toBeInTheDocument(),
    );
    expect(screen.getByText('Aucune demande envoyee.')).toBeInTheDocument();
    expect(screen.getByText('Aucun document ni depot recent.')).toBeInTheDocument();

    // Aucun donut rendu : les deux ChartCard affichent « Aucune donnée ».
    expect(screen.queryByTestId('donut')).not.toBeInTheDocument();
    expect(screen.getAllByText(/Aucune donnée/i)).toHaveLength(2);

    // Le KPI « Requetes a traiter » est bien present a 0.
    const kpi = screen.getByText('Requetes a traiter').closest('div');
    expect(kpi && within(kpi).getByText('0')).toBeTruthy();
  });
});

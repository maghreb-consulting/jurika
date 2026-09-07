import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EmployeeDashboard } from '../EmployeeDashboard';

// EmployeeDashboard utilise useNavigate (cartes tickets/demandes) -> il faut un Router.
const renderDash = () => render(<EmployeeDashboard />, { wrapper: MemoryRouter });

// Scoping employe (2026-07-03) — le tableau de bord employe ne doit afficher
// QUE ses donnees. Plus aucun appel a supervisionService.kpis() (agregat
// workspace-wide qui faisait fuiter un "Total 23"). Les 4 KpiCards derivent
// des sources scopees : ticketService.list (assigne=userId), listDossiers
// (responsable_id=userId), listDemandes (mes dossiers).

const list = vi.fn();
const listDossiers = vi.fn();
const listDemandes = vi.fn();
const listMesRequetes = vi.fn();

vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: (...a: unknown[]) => list(...a) },
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: (...a: unknown[]) => listDossiers(...a),
    listDemandes: (...a: unknown[]) => listDemandes(...a),
    listMesRequetes: (...a: unknown[]) => listMesRequetes(...a),
  },
}));
// Sous-composants scopes (appellent dashboard-service / transfer-service) —
// on les neutralise pour isoler le rendu des KpiCards du haut.
vi.mock('../../../components/dashboard/Sprint10EmployePanel', () => ({
  Sprint10EmployePanel: () => null,
}));
// Lot IA-1 — le panneau Copilote (useNavigate + appel dashboard-service) est
// neutralise pour isoler le rendu des KpiCards du haut.
vi.mock('../../../components/dashboard/CopilotePanel', () => ({
  CopilotePanel: () => null,
}));
vi.mock('../../../components/transfer/PendingTransfersPanel', () => ({
  PendingTransfersPanel: () => null,
}));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({
    userId: 'u1',
    workspaceId: 'w1',
    email: 'karim@jurika.ma',
    role: 'EMPLOYE',
  }),
}));

describe('EmployeeDashboard', () => {
  beforeEach(() => {
    list.mockReset();
    listDossiers.mockReset();
    listDemandes.mockReset();
    listMesRequetes.mockReset();
    listMesRequetes.mockResolvedValue([]);
  });

  it('rend le header de bienvenue avec le slug de l\'email', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    await waitFor(() => {
      expect(screen.getByText(/Bonjour, karim/i)).toBeInTheDocument();
    });
  });

  it('derive les 4 KpiCards de MES donnees scopees (aucun total workspace)', async () => {
    // 3 de mes tickets, un par statut du parcours.
    list.mockResolvedValue({
      items: [
        { id: 't1', workspaceId: 'w1', dossierId: 'd1', statut: 'CREATION_TICKET', type: 'CREATION', titre: 'A', createdAt: '2026-07-01' },
        { id: 't2', workspaceId: 'w1', dossierId: 'd1', statut: 'GENERATION_DOCUMENTS', type: 'CREATION', titre: 'B', createdAt: '2026-07-01' },
        { id: 't3', workspaceId: 'w1', dossierId: 'd1', statut: 'CLOTURE_DOSSIER', type: 'CREATION', titre: 'C', createdAt: '2026-07-01' },
      ],
    });
    // 1 seul dossier transfere (actif) — le nouvel employe ne doit PAS voir "23".
    listDossiers.mockResolvedValue([
      { id: 'd1', raisonSociale: 'ACME', formeJuridique: 'SARL', ice: null, ville: 'Casa', statut: 'ACTIF' },
    ]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    await waitFor(() => expect(screen.getByText('Dossiers actifs')).toBeInTheDocument());
    expect(screen.getByText('Tickets en cours')).toBeInTheDocument();
    expect(screen.getByText('Taches urgentes')).toBeInTheDocument();
    expect(screen.getByText('Cloturees')).toBeInTheDocument();

    // Dossiers actifs = 1 (mon unique dossier), PAS un total workspace.
    const dossiersCard = screen.getByText('Dossiers actifs').closest('div')?.parentElement;
    expect(dossiersCard).toHaveTextContent('1');
    expect(dossiersCard).toHaveTextContent('1 dossier a moi');
    // Aucun "23" (ou tout autre total global) ne doit apparaitre.
    expect(screen.queryByText('23')).not.toBeInTheDocument();
  });

  it('affiche le banner alerte quand au moins 1 ticket est en cours', async () => {
    list.mockResolvedValue({
      items: [
        { id: 't1', workspaceId: 'w1', dossierId: 'd1', statut: 'GENERATION_DOCUMENTS', type: 'CREATION', titre: 'X', createdAt: '2026-05-22' },
        { id: 't2', workspaceId: 'w1', dossierId: 'd2', statut: 'GENERATION_DOCUMENTS', type: 'CREATION', titre: 'Y', createdAt: '2026-05-22' },
      ],
    });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    await waitFor(() => {
      expect(screen.getByText(/2 tickets en cours/i)).toBeInTheDocument();
    });
  });

  it('nomme le dataroom sur chaque demande (jointure sur les dossiers charges)', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([
      { id: 'd1', raisonSociale: 'ACME SARL', formeJuridique: 'SARL', ice: null, ville: 'Casa', statut: 'ACTIF' },
    ]);
    listDemandes.mockResolvedValue([
      { id: 'dem1', dossierId: 'd1', soumisPar: null, sujet: 'Besoin K-bis', description: null,
        statut: 'NON_TRAITEE', ticketId: null, prisEnChargePar: null, noteInterne: null,
        traiteAt: null, createdAt: '2026-07-20' },
    ]);

    renderDash();

    await waitFor(() => expect(screen.getByText('Besoin K-bis')).toBeInTheDocument());
    // Le nom du dataroom (raison sociale) est resolu, pas l'UUID.
    expect(screen.getByText(/Dataroom : ACME SARL/i)).toBeInTheDocument();
  });

  it('affiche la section « Mes requetes au client » avec le dataroom et le badge a valider', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([
      { id: 'd1', raisonSociale: 'BETA SAS', formeJuridique: 'SAS', ice: null, ville: 'Rabat', statut: 'ACTIF' },
    ]);
    listDemandes.mockResolvedValue([]);
    listMesRequetes.mockResolvedValue([
      { id: 'r1', dossierId: 'd1', soumisPar: 'u1', sujet: 'Fournir bail', description: null,
        statut: 'REPONDUE', ticketId: null, prisEnChargePar: 'u1', noteInterne: null, traiteAt: null,
        createdAt: '2026-07-21', direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE',
        reponduAt: '2026-07-22', clotureAt: null, noteClient: 'Bail depose.' },
    ]);

    renderDash();

    await waitFor(() => expect(screen.getByText('Mes requetes au client')).toBeInTheDocument());
    expect(screen.getByText('Fournir bail')).toBeInTheDocument();
    // Nom du dataroom present sur la carte requete + badge « a valider » (REPONDUE).
    expect(screen.getByText(/Dataroom : BETA SAS/i)).toBeInTheDocument();
    expect(screen.getByText(/1 a valider/i)).toBeInTheDocument();
  });
});

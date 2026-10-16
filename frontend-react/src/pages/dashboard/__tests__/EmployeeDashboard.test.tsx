import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
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
// L6b (demande du 2026-10-16) : le copilote est retire du tableau de bord (composant et service
// supprimes) ; la carte "Prochaine action" prend sa place.
vi.mock('../../../components/transfer/PendingTransfersPanel', () => ({
  PendingTransfersPanel: () => null,
}));
const getEmploye = vi.fn();
vi.mock('../../../services/dashboard.service', () => ({
  dashboardService: { getEmploye: (...a: unknown[]) => getEmploye(...a) },
}));
const utilisateur = { userId: 'u1', workspaceId: 'w1', email: 'karim@jurika.ma', role: 'EMPLOYE' };
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => utilisateur,
  // Page (fil d'Ariane par defaut) lit le role dans le magasin.
  useAuthStore: (sel: (s: { user: typeof utilisateur }) => unknown) => sel({ user: utilisateur }),
}));

describe('EmployeeDashboard', () => {
  beforeEach(() => {
    list.mockReset();
    listDossiers.mockReset();
    listDemandes.mockReset();
    listMesRequetes.mockReset();
    listMesRequetes.mockResolvedValue([]);
    getEmploye.mockReset();
    getEmploye.mockResolvedValue({ mesEcheancesAssignees: [] });
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

    // ON ATTEND LA DONNEE, PAS L'ETIQUETTE.
    //
    // « Dossiers actifs » est le titre d'une KpiCard : il est rendu au premier
    // paint, avant tout appel resolu. L'attendre ne prouve donc rien, et les
    // assertions qui suivaient couraient apres les promesses — elles gagnaient
    // la course en local et l'ont perdue en CI, sur une machine plus lente.
    // (Constate le 2026-09-11 : le DOM capture montrait encore « — » et
    // « sur 0 de mes tickets ».)
    //
    // On attend donc un texte qui n'existe QUE si les donnees sont arrivees.
    await screen.findByText('1 dossier à vous');
    expect(screen.getByText('Dossiers actifs')).toBeInTheDocument();
    expect(screen.getByText('Tickets en cours')).toBeInTheDocument();
    expect(screen.getByText('Tâches urgentes')).toBeInTheDocument();
    expect(screen.getByText('Clôturés')).toBeInTheDocument();

    // Dossiers actifs = 1 (mon unique dossier), PAS un total workspace.
    const dossiersCard = screen.getByText('Dossiers actifs').closest('div')?.parentElement;
    expect(dossiersCard).toHaveTextContent('1');
    expect(dossiersCard).toHaveTextContent('1 dossier à vous');
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
    expect(screen.getByText(/Data Room : ACME SARL/i)).toBeInTheDocument();
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

    // Meme piege que plus haut : le titre « Mes requetes au client » est rendu
    // au premier paint, la requete elle-meme arrive avec la promesse. On attend
    // donc le SUJET de la requete, qui n'apparait qu'une fois la donnee la.
    await screen.findByText('Fournir bail');
    expect(screen.getByText('Requêtes du cabinet au client')).toBeInTheDocument();
    // Nom du dataroom present sur la carte requete + badge « a valider » (REPONDUE).
    expect(screen.getByText(/Data Room : BETA SAS/i)).toBeInTheDocument();
    expect(screen.getByText(/1 à valider/i)).toBeInTheDocument();
  });

  it('la carte d une requete ouvre l onglet « Requêtes du cabinet » de la Data Room (lot L6)', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([{ id: 'd1', raisonSociale: 'BETA SAS', formeJuridique: 'SAS', ice: null, ville: 'Rabat', statut: 'ACTIF' }]);
    listDemandes.mockResolvedValue([]);
    listMesRequetes.mockResolvedValue([
      { id: 'r1', dossierId: 'd1', soumisPar: 'u1', sujet: 'Fournir bail', description: null,
        statut: 'OUVERTE', ticketId: null, prisEnChargePar: 'u1', noteInterne: null, traiteAt: null,
        createdAt: '2026-07-21', direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE',
        reponduAt: null, clotureAt: null, noteClient: null },
    ]);
    function Lieu() {
      const l = useLocation();
      return <p data-testid="lieu">{l.pathname + l.search}</p>;
    }
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<EmployeeDashboard />} />
          <Route path="/data-rooms" element={<Lieu />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByText('Fournir bail'));
    expect(await screen.findByTestId('lieu')).toHaveTextContent('/data-rooms?dossier=d1&tab=requetes');
  });

  it('carte « Prochaine action » : mes tickets et les echeances des demarches, un seul appel aux indicateurs (L6b E9)', async () => {
    list.mockResolvedValue({
      items: [
        { id: 't1', workspaceId: 'w1', reference: 'TK-9', dossierId: 'd1', assigneId: 'u1', statut: 'DEROULEMENT_DEMARCHE', type: 'CREATION', titre: 'Création ATLAS', createdAt: '2026-07-01', deadline: null },
      ],
    });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);
    getEmploye.mockResolvedValue({
      mesEcheancesAssignees: [{ id: 'e1', dossierId: 'd1', libelle: 'Dépôt au greffe', dateEcheance: '2099-01-10', severite: 'APPROCHE' }],
    });

    renderDash();

    // Deux sources (tickets puis indicateurs) : attente plus longue que le defaut sous charge.
    const lien = await screen.findByRole('link', { name: 'TK-9 · Création ATLAS' }, { timeout: 4000 });
    expect(lien).toHaveAttribute('href', '/tickets/t1');
    expect(screen.getByText('Démarche : Dépôt au greffe')).toBeInTheDocument();
    expect(getEmploye).toHaveBeenCalledTimes(1);
  });

  it("sans copilote : la carte "Prochaine action" prend sa place, apres les requetes et les graphiques (demande du 2026-10-16)", async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    const { container } = renderDash();

    const carte = await screen.findByRole('heading', { name: /Prochaine action/ });
    const requetes = screen.getByRole('heading', { name: /Requêtes du cabinet au client/ });
    expect(container.querySelector('#copilote'), 'aucun panneau du copilote').toBeNull();
    // La carte suit desormais les sections (place de l'ancien copilote), elle n'est plus en tete.
    expect(requetes.compareDocumentPosition(carte) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('tickets indisponibles : la carte le dit et les compteurs de tickets affichent "--"', async () => {
    list.mockRejectedValue(new Error('503'));
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    expect(await screen.findByText('Vos tickets n’ont pas pu être chargés')).toBeInTheDocument();
    expect(screen.getByText('Tickets en cours').closest('div')?.parentElement).toHaveTextContent('--');
  });
});

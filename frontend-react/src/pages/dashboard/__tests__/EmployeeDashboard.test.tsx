import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EmployeeDashboard } from '../EmployeeDashboard';

// EmployeeDashboard utilise useNavigate (cartes tickets/demandes) -> il faut un Router.
const renderDash = () => render(<EmployeeDashboard />, { wrapper: MemoryRouter });

// Scoping employe (2026-07-03) — le tableau de bord employe ne doit afficher
// QUE ses donnees. Plus aucun appel a supervisionService.kpis() (agregat
// workspace-wide qui faisait fuiter un "Total 23"). Les indicateurs derivent
// des sources scopees : ticketService.list (assigne=userId), listDossiers
// (responsable_id=userId), listDemandes (mes dossiers), mes requetes, et les
// delais des demarches de mes dossiers (dashboard-service).

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
// Sous-composant scope (transfer-service) : neutralise pour isoler le rendu du tableau de bord.
// L6b (E17) : la zone "Mes indicateurs" (Sprint10EmployePanel) est retiree (doublons) ;
// L6b (demande du 2026-10-16) : le copilote est retire, la carte "Prochaine action" prend sa place.
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

/** Carte d'un indicateur : le libelle est dans la ligne d'en-tete, la valeur dans la carte. */
const carte = (libelle: string) => screen.getByText(libelle, { selector: 'p, p > a' }).closest('p')?.parentElement?.parentElement as HTMLElement;

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

  it('derive les 4 indicateurs de MES donnees scopees, sans doublon ni total du cabinet (L6b E17)', async () => {
    // 4 de mes tickets : un par statut ouvert, plus un clos qui ne compte nulle part.
    list.mockResolvedValue({
      items: [
        { id: 't1', workspaceId: 'w1', dossierId: 'd1', assigneId: 'u1', statut: 'CREATION_TICKET', type: 'CREATION', titre: 'A', createdAt: '2026-07-01', deadline: null },
        { id: 't2', workspaceId: 'w1', dossierId: 'd1', assigneId: 'u1', statut: 'GENERATION_DOCUMENTS', type: 'CREATION', titre: 'B', createdAt: '2026-07-01', deadline: '2000-01-01' },
        { id: 't3', workspaceId: 'w1', dossierId: 'd1', assigneId: 'u1', statut: 'DEROULEMENT_DEMARCHE', type: 'CREATION', titre: 'C', createdAt: '2026-07-01', deadline: null },
        { id: 't4', workspaceId: 'w1', dossierId: 'd1', assigneId: 'u1', statut: 'CLOTURE_DOSSIER', type: 'CREATION', titre: 'Ticket clos', createdAt: '2026-07-01', deadline: null },
      ],
    });
    listDossiers.mockResolvedValue([{ id: 'd1', raisonSociale: 'ACME', formeJuridique: 'SARL', ice: null, ville: 'Casa', statut: 'ACTIF' }]);
    listDemandes.mockResolvedValue([
      { id: 'dem1', dossierId: 'd1', soumisPar: null, sujet: 'Kbis', description: null, statut: 'NON_TRAITEE', ticketId: null, prisEnChargePar: null, noteInterne: null, traiteAt: null, createdAt: '2026-07-20' },
    ]);
    listMesRequetes.mockResolvedValue([
      { id: 'r1', dossierId: 'd1', soumisPar: 'u1', sujet: 'Bail', description: null, statut: 'REPONDUE', ticketId: null, prisEnChargePar: 'u1', noteInterne: null, traiteAt: null, createdAt: '2026-07-21', direction: 'EMPLOYE_TO_CLIENT', typeRequete: 'PIECE', reponduAt: null, clotureAt: null, noteClient: null },
    ]);
    // Une demarche du dossier dont le delai tombe dans 3 jours (rattachee au ticket en demarches).
    const dans3 = new Date(Date.now() + 3 * 86_400_000);
    const iso = `${dans3.getFullYear()}-${String(dans3.getMonth() + 1).padStart(2, '0')}-${String(dans3.getDate()).padStart(2, '0')}`;
    getEmploye.mockResolvedValue({ mesEcheancesAssignees: [{ id: 'e1', dossierId: 'd1', libelle: 'Dépôt', dateEcheance: iso, severite: 'CRITIQUE' }] });

    renderDash();

    // On attend la DONNEE, pas l'etiquette (rendue des le premier paint).
    await screen.findByText('dont 1 en création');
    expect(carte('Tickets en cours')).toHaveTextContent('3');
    await waitFor(() => expect(carte('En retard')).toHaveTextContent('1'));
    expect(carte('Délai sous 7 jours')).toHaveTextContent('1');
    expect(carte('Échanges à traiter')).toHaveTextContent('2');
    expect(carte('Échanges à traiter')).toHaveTextContent('1 demande non traitée · 1 réponse à valider');
    // Aucun "23" (ou tout autre total global) ne doit apparaitre.
    expect(carte('Tickets en cours')).not.toHaveTextContent('23');
    // Retires : doublon des tickets en cours, compteur des clotures, bandeau sans valeur, "Mes indicateurs".
    expect(screen.queryByText('Tâches urgentes')).not.toBeInTheDocument();
    expect(screen.queryByText('Clôturés', { selector: 'p, p > a' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Pensez à vérifier les délais/)).not.toBeInTheDocument();
    expect(screen.queryByText('Mes indicateurs')).not.toBeInTheDocument();
    expect(screen.queryByText(/Répartition de mes tickets/)).not.toBeInTheDocument();
  });

  it('le tableau par etape ne montre que les tickets ouverts (aucune colonne Cloture)', async () => {
    list.mockResolvedValue({
      items: [
        { id: 't1', workspaceId: 'w1', dossierId: 'd1', statut: 'GENERATION_DOCUMENTS', type: 'CREATION', titre: 'Ouvert', createdAt: '2026-07-01' },
        { id: 't2', workspaceId: 'w1', dossierId: 'd1', statut: 'CLOTURE_DOSSIER', type: 'CREATION', titre: 'Fini', createdAt: '2026-07-01' },
      ],
    });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    // Le ticket ouvert figure au tableau par etape (et dans la carte "Prochaine action").
    const liens = await screen.findAllByRole('link', { name: 'Ouvert' });
    expect(liens.map((l) => l.getAttribute('href'))).toContain('/workflows/t1');
    expect(screen.queryByRole('link', { name: 'Fini' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Génération des documents' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Clôture' })).not.toBeInTheDocument();
  });

  it('aucun vocabulaire d echeance : on parle de delais (le mot est reserve a la facturation, L6b E17)', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    const { container } = renderDash();

    await screen.findByText('Aucune action en attente');
    expect(container.textContent).not.toMatch(/[ée]ch[ée]ance/i);
  });

  it('demandes indisponibles : le dit et le compteur affiche "--", jamais zero', async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockRejectedValue(new Error('503'));

    renderDash();

    expect(await screen.findByText('Les demandes de vos clients n’ont pas pu être chargées')).toBeInTheDocument();
    expect(carte('Échanges à traiter')).toHaveTextContent('--');
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

    // On attend le SUJET de la requete, qui n'apparait qu'une fois la donnee la.
    await screen.findByText('Fournir bail');
    expect(screen.getByText('Requêtes du cabinet au client')).toBeInTheDocument();
    // Nom du dataroom present sur la carte requete + badge « a valider » (REPONDUE).
    expect(screen.getByText(/Data Room : BETA SAS/i)).toBeInTheDocument();
    expect(screen.getByText(/^1 à valider$/i)).toBeInTheDocument();
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

  it('carte « Prochaine action » : mes tickets et les delais des demarches, un seul appel aux indicateurs (L6b E9)', async () => {
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
    expect(screen.getByText('Délai légal : Dépôt au greffe')).toBeInTheDocument();
    expect(getEmploye).toHaveBeenCalledTimes(1);
  });

  it("sans copilote : la carte Prochaine action prend sa place, apres les sections de travail (demande du 2026-10-16)", async () => {
    list.mockResolvedValue({ items: [] });
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    const { container } = renderDash();

    const carteAction = await screen.findByRole('heading', { name: /Prochaine action/ });
    const requetes = screen.getByRole('heading', { name: /Requêtes du cabinet au client/ });
    expect(container.querySelector('#copilote'), 'aucun panneau du copilote').toBeNull();
    // La carte suit les sections de travail (place de l'ancien copilote), elle n'est pas en tete.
    expect(requetes.compareDocumentPosition(carteAction) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('tickets indisponibles : la carte le dit et les compteurs de tickets affichent "--"', async () => {
    list.mockRejectedValue(new Error('503'));
    listDossiers.mockResolvedValue([]);
    listDemandes.mockResolvedValue([]);

    renderDash();

    expect((await screen.findAllByText('Vos tickets n’ont pas pu être chargés')).length).toBeGreaterThanOrEqual(1);
    expect(carte('Tickets en cours')).toHaveTextContent('--');
    expect(carte('En retard')).toHaveTextContent('--');
  });
});

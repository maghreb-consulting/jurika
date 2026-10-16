import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { SupervisorDashboard } from '../SupervisorDashboard';

// Lot L6b (E17) : tableau de bord du superviseur refait (demande du 2026-10-16). Quatre
// indicateurs sans doublon, les delais a surveiller, l'equipe par nom, l'activite en barres
// etiquetees et en colonnes (comptages entiers), aucun code brut, aucun zero invente.

const kpis = vi.fn();
const ticketsPerEmploye = vi.fn();
const getSuperviseur = vi.fn();
const listDossiers = vi.fn();

vi.mock('../../../services/supervision.service', () => ({
  supervisionService: {
    kpis: (...a: unknown[]) => kpis(...a),
    ticketsPerEmploye: (...a: unknown[]) => ticketsPerEmploye(...a),
  },
}));
vi.mock('../../../services/dashboard.service', () => ({
  dashboardService: { getSuperviseur: (...a: unknown[]) => getSuperviseur(...a) },
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: { listDossiers: (...a: unknown[]) => listDossiers(...a) },
}));
// Le panneau demandes/requetes (workspace-wide) a son propre test.
vi.mock('../../../components/dashboard/SupervisionDemandesPanel', () => ({
  SupervisionDemandesPanel: () => null,
}));
// Recharts mesure le conteneur (ResizeObserver absent de jsdom) : graphique neutralise.
vi.mock('recharts', async (orig) => {
  const reel = await orig<typeof import('recharts')>();
  return { ...reel, ResponsiveContainer: () => null };
});
vi.mock('../../../store/authStore', () => {
  const user = { userId: 's1', workspaceId: 'w1', email: 'chef@acme.ma', role: 'SUPERVISEUR' };
  return { useCurrentUser: () => user, useAuthStore: (sel: (s: { user: typeof user }) => unknown) => sel({ user }) };
});

const rendre = () => render(<SupervisorDashboard />, { wrapper: MemoryRouter });
const carte = (libelle: string) => screen.getByText(libelle, { selector: 'p, p > a' }).closest('p')?.parentElement?.parentElement as HTMLElement;

function isoDans(jours: number): string {
  const d = new Date(Date.now() + jours * 86_400_000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const SUIVI = {
  ticketsOuverts: 6,
  ticketsClos30d: 2,
  tempsMoyenClotureHeures: 196,
  dossiersARisque: [{ dossierId: 'd2', raisonSociale: 'Oasis Agro', motif: 'Delai legal depasse' }],
  echeancesJ30J15J3: [
    { label: 'J-30', items: [
      { id: 'e1', dossierId: 'd1', libelle: 'Immatriculation au registre du commerce', dateEcheance: isoDans(2), severite: 'CRITIQUE' },
      { id: 'e2', dossierId: 'd3', libelle: 'Publication au BO', dateEcheance: isoDans(20), severite: 'APPROCHE' },
    ] },
    { label: 'J-15', items: [{ id: 'e1', dossierId: 'd1', libelle: 'Immatriculation au registre du commerce', dateEcheance: isoDans(2), severite: 'CRITIQUE' }] },
    { label: 'J-3', items: [{ id: 'e1', dossierId: 'd1', libelle: 'Immatriculation au registre du commerce', dateEcheance: isoDans(2), severite: 'CRITIQUE' }] },
  ],
  ticketsParStatut: [
    { label: 'CREATION_TICKET', count: 2 },
    { label: 'GENERATION_DOCUMENTS', count: 1 },
    { label: 'DEROULEMENT_DEMARCHE', count: 3 },
    { label: 'CLOTURE_DOSSIER', count: 9 },
  ],
  evolutionMensuelle: [{ month: '2026-09', crees: 3, clotures: 1 }, { month: '2026-10', crees: 2, clotures: 1 }],
  chargeParEmploye: [{ userId: 'u1', email: 'n.idrissi@acme.ma', ticketsOuverts: 4, echeancesAssignees: 0 }],
  productiviteIncluse: true,
};

describe('SupervisorDashboard', () => {
  beforeEach(() => {
    kpis.mockReset();
    ticketsPerEmploye.mockReset();
    getSuperviseur.mockReset();
    listDossiers.mockReset();
    kpis.mockResolvedValue({ tickets: 15, ticketsNouveaux: 2, ticketsEnCours: 4, ticketsClotures: 9, ticketsAnnules: 0, dossiers: 42, dossiersActifs: 40, deboursTotalMad: 0, demandesNonTraitees: 3, computedAt: '' });
    ticketsPerEmploye.mockResolvedValue([
      { userId: 'u1', firstName: 'Nadia', lastName: 'Idrissi', enCours: 4, clotures: 0, annules: 0 },
      { userId: 'u2', firstName: 'Omar', lastName: 'Berrada', enCours: 1, clotures: 1, annules: 1 },
    ]);
    getSuperviseur.mockResolvedValue(SUIVI);
    listDossiers.mockResolvedValue([
      { id: 'd1', raisonSociale: 'Atlas Distribution' },
      { id: 'd3', raisonSociale: 'Coopérative Argane' },
    ]);
  });

  it('quatre indicateurs qui comptent, sans doublon (L6b E17)', async () => {
    rendre();
    expect(await screen.findByText('dont 2 à la création')).toBeInTheDocument();
    expect(carte('Tickets ouverts')).toHaveTextContent('6');
    expect(carte('Dossiers en retard')).toHaveTextContent('1');
    expect(carte('Délais sous 7 jours')).toHaveTextContent('1');
    expect(carte('Demandes non traitées')).toHaveTextContent('3');
    // Retires : total des dossiers, nombre d'employes, clotures depuis toujours, score, courbes.
    for (const retire of ['Total dossiers', 'Employés', 'Score', 'Activité (14 derniers jours)', 'Tickets par type', 'Évolution des tickets (30 jours)', 'Suivi du cabinet']) {
      expect(screen.queryByText(retire)).not.toBeInTheDocument();
    }
    expect(screen.queryByText('42')).not.toBeInTheDocument();
  });

  it('delais a surveiller : retard nomme en francais, puis delais a venir avec le nom du dossier, une seule fois', async () => {
    rendre();
    const liste = await screen.findByRole('list', { name: /Délais légaux des 30 prochains jours/ });
    const lignes = within(liste).getAllByRole('listitem');
    // La tranche J-30 contient J-15 et J-3 : chaque delai n'apparait qu'une fois.
    expect(lignes).toHaveLength(2);
    expect(lignes[0]).toHaveTextContent('Atlas Distribution');
    expect(lignes[0]).toHaveTextContent('Immatriculation au registre du commerce');
    expect(lignes[0]).toHaveTextContent('Dans 2 jours');
    expect(lignes[1]).toHaveTextContent('Coopérative Argane');
    expect(screen.getByRole('link', { name: 'Oasis Agro' })).toHaveAttribute('href', '/data-rooms?dossier=d2');
    expect(screen.getByText('Délai légal dépassé')).toBeInTheDocument();
    expect(screen.queryByText('Delai legal depasse')).not.toBeInTheDocument();
  });

  it('equipe : noms des employes, jamais un identifiant ; charge triee', async () => {
    rendre();
    const tableau = await screen.findByRole('table', { name: 'Charge de chaque employé' });
    const lignes = within(tableau).getAllByRole('row').slice(1);
    expect(lignes[0]).toHaveTextContent('Nadia Idrissi');
    expect(lignes[1]).toHaveTextContent('Omar Berrada');
    expect(screen.queryByText(/n\.idrissi/)).not.toBeInTheDocument();
  });

  it('activite : tickets ouverts par etape etiquetes, duree moyenne lisible en jours', async () => {
    rendre();
    const etapes = await screen.findByRole('list', { name: 'Tickets ouverts par étape du parcours' });
    expect(within(etapes).getByText("Création du ticket et collecte d'information")).toBeInTheDocument();
    expect(within(etapes).getAllByRole('listitem')).toHaveLength(3);
    // Total des seuls tickets ouverts (2 + 1 + 3), les clotures ne comptent pas.
    expect(screen.getByText('Tickets ouverts', { selector: 'span' }).parentElement).toHaveTextContent('6');
    // 196 h => 8,2 jours (et non "196,0 h").
    expect(screen.getByText('8,2 jours (2 tickets clôturés)')).toBeInTheDocument();
    // Tableau equivalent des colonnes (comptages entiers).
    const table = screen.getByRole('table', { name: /Tickets créés et clôturés par mois/ });
    expect(table).toHaveTextContent('3');
    expect(table.textContent).not.toMatch(/\d[.,]\d/);
  });

  it('aucune cloture sur 30 jours : la duree moyenne ne s affiche pas a zero', async () => {
    getSuperviseur.mockResolvedValue({ ...SUIVI, ticketsClos30d: 0, tempsMoyenClotureHeures: 0 });
    rendre();
    expect(await screen.findByText('aucun ticket clôturé')).toBeInTheDocument();
    expect(screen.queryByText(/^0 h/)).not.toBeInTheDocument();
  });

  it('forfait sans productivite : explication a la place du graphique mensuel', async () => {
    getSuperviseur.mockResolvedValue({ ...SUIVI, productiviteIncluse: false });
    rendre();
    expect(await screen.findByTestId('fonction-hors-forfait')).toBeInTheDocument();
    expect(screen.queryByText('Tickets créés et clôturés par mois')).not.toBeInTheDocument();
  });

  it('une source en echec s affiche "--" et sa zone le dit, jamais un zero invente', async () => {
    kpis.mockRejectedValue(new Error('503'));
    ticketsPerEmploye.mockRejectedValue(new Error('503'));
    getSuperviseur.mockRejectedValue(new Error('503'));

    rendre();

    expect(await screen.findByText('Données indisponibles')).toBeInTheDocument();
    expect(screen.getByText('Délais indisponibles')).toBeInTheDocument();
    expect(screen.getByText('Activité indisponible')).toBeInTheDocument();
    expect(screen.getAllByText('--')).toHaveLength(4);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });
});

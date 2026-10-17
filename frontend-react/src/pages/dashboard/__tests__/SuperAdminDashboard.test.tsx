import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SuperAdminDashboard } from '../SuperAdminDashboard';

// Lot L6b (E9) : tableau de bord de la plateforme (super-admin). Meme defaut que celui du
// superviseur : un instantane partiel ne doit ni planter l'ecran ni inventer un zero.
// Lot L6b (E17) : libelles a la place des codes, barres etiquetees, alertes au ton de leur gravite.

const getSuperAdmin = vi.fn();
vi.mock('../../../services/dashboard.service', () => ({
  dashboardService: { getSuperAdmin: (...a: unknown[]) => getSuperAdmin(...a) },
}));
vi.mock('recharts', async (orig) => {
  const reel = await orig<typeof import('recharts')>();
  return { ...reel, ResponsiveContainer: () => null };
});

const listWorkspaces = vi.fn();
vi.mock('../../../services/admin.service', () => ({
  adminService: { listWorkspaces: (...a: unknown[]) => listWorkspaces(...a) },
}));

const rendre = () => render(<SuperAdminDashboard />, { wrapper: MemoryRouter });
const carte = (libelle: string) => screen.getByText(libelle, { selector: 'p, p > a' }).closest('p')?.parentElement?.parentElement as HTMLElement;

describe('SuperAdminDashboard', () => {
  beforeEach(() => {
    getSuperAdmin.mockReset();
    listWorkspaces.mockReset();
    listWorkspaces.mockResolvedValue([]);
  });

  it('ne plante pas sur un instantane vide : indicateurs "--"', async () => {
    getSuperAdmin.mockResolvedValue({});
    rendre();
    expect(await screen.findByText('Utilisateurs')).toBeInTheDocument();
    expect(screen.getAllByText('--').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByRole('heading', { level: 1, name: 'Tableau de bord de la plateforme' })).toBeInTheDocument();
  });

  it('rend les chiffres de la plateforme et la sante des services, nommes', async () => {
    getSuperAdmin.mockResolvedValue({
      totalWorkspaces: 12,
      activeWorkspaces30d: 9,
      totalUsers: 87,
      signups30d: [{ day: '2026-10-01', count: 2 }, { day: '2026-10-05', count: 1 }],
      storage: { totalBytes: 2 * 1024 * 1024 * 1024, fileCount: 340 },
      criticalAuditEvents24h: [],
      servicesHealth: { 'auth-service': 'UP', 'ai-service': 'DOWN' },
      workspacesParStatut: [{ label: 'ACTIVE', count: 10 }, { label: 'ESSAI', count: 2 }],
    });
    rendre();
    expect(await screen.findByText('10 actifs · 2 en essai')).toBeInTheDocument();
    expect(carte('Cabinets')).toHaveTextContent('12');
    expect(carte('Utilisateurs')).toHaveTextContent('87');
    expect(screen.getByText('3 créés sur 30 jours')).toBeInTheDocument();
    expect(screen.getByText('2,00 Go')).toBeInTheDocument();
    expect(screen.getByText('340 documents')).toBeInTheDocument();
    expect(screen.getByText('10 actifs · 2 en essai')).toBeInTheDocument();
    expect(screen.getByText('Comptes et connexions')).toBeInTheDocument();
    expect(screen.getByText('Hors ligne')).toBeInTheDocument();
    expect(screen.getByText('1 sur 2 en ligne')).toBeInTheDocument();
    // Retire : le graphique journalier des inscriptions (pics).
    expect(screen.queryByText('Inscriptions (30 jours)')).not.toBeInTheDocument();
  });

  it('repartition des cabinets : libelles francais et valeurs ecrites, jamais un code', async () => {
    getSuperAdmin.mockResolvedValue({
      workspacesParForfait: [{ label: 'professionnel', count: 3 }, { label: 'non_defini', count: 1 }],
      workspacesParStatut: [{ label: 'ACTIVE', count: 3 }, { label: 'ESSAI', count: 1 }, { label: 'PENDING_VERIFICATION', count: 1 }],
    });
    rendre();
    // Forfaits : anneau avec total, liste equivalente (libelle : valeur).
    const forfaits = await screen.findByRole('list', { name: 'Cabinets par forfait (total 4)' });
    expect(within(forfaits).getByText('Professionnel : 3')).toBeInTheDocument();
    expect(within(forfaits).getByText('Non défini : 1')).toBeInTheDocument();
    // Statuts : barre empilee, tableau equivalent.
    const statuts = screen.getByRole('table', { name: 'Cabinets par statut' });
    expect(within(statuts).getByText('Essai')).toBeInTheDocument();
    expect(within(statuts).getByText('En attente de vérification')).toBeInTheDocument();
    expect(screen.queryByText('PENDING_VERIFICATION')).not.toBeInTheDocument();
    expect(screen.queryByText('ESSAI')).not.toBeInTheDocument();
  });

  it('croissance : nouveaux cabinets par mois et cumul exact a partir du total (L6b E18)', async () => {
    getSuperAdmin.mockResolvedValue({
      totalWorkspaces: 10,
      signupsMensuels: [{ month: '2026-08', count: 2 }, { month: '2026-09', count: 0 }, { month: '2026-10', count: 3 }],
    });
    rendre();
    const table = await screen.findByRole('table', { name: /Nouveaux cabinets par mois et total/ });
    const lignes = within(table).getAllByRole('row').slice(1);
    // Total actuel 10, 5 inscrits sur la periode : 5 avant, puis 7, 7, 10.
    expect(lignes.map((l) => l.textContent)).toEqual(['août 2627', 'sept. 2607', 'oct. 26310']);
    expect(screen.getByRole('img', { name: 'Nombre de cabinets sur douze mois' })).toBeInTheDocument();
  });

  it('croissance incoherente (plus d inscriptions que de cabinets) : aucun cumul invente', async () => {
    getSuperAdmin.mockResolvedValue({ totalWorkspaces: 1, signupsMensuels: [{ month: '2026-10', count: 3 }] });
    rendre();
    const table = await screen.findByRole('table', { name: /Nouveaux cabinets par mois et total/ });
    expect(within(table).queryByText('Cabinets au total')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Nombre de cabinets sur douze mois' })).not.toBeInTheDocument();
  });

  it('stockage par cabinet en Go, depuis la liste des cabinets (L6b E18)', async () => {
    getSuperAdmin.mockResolvedValue({});
    listWorkspaces.mockResolvedValue([
      { id: 'w1', code: 'A', name: 'Atlas Conseil', contactEmail: null, statut: 'ACTIVE', forfait: 'max', employes: 1, clients: 1, storageBytes: 3 * 1024 ** 3, storageFiles: 10, createdAt: '' },
      { id: 'w2', code: 'B', name: 'Nord Juris', contactEmail: null, statut: 'ACTIVE', forfait: 'max', employes: 1, clients: 1, storageBytes: null, storageFiles: null, createdAt: '' },
    ]);
    rendre();
    const liste = await screen.findByRole('list', { name: 'Stockage par cabinet' });
    expect(within(liste).getByText('3,00 Go')).toBeInTheDocument();
    // Stockage inconnu : le cabinet n'est pas compte a zero.
    expect(within(liste).queryByText('Nord Juris')).not.toBeInTheDocument();
  });

  it('alertes de securite : libelle du serveur, type d element en francais, jamais le code (L6b E9, E17)', async () => {
    getSuperAdmin.mockResolvedValue({
      criticalAuditEvents24h: [
        { id: 'e1', action: 'LOGIN_FAILED', actionLabel: 'Connexion échouée', userId: null,
          workspaceId: null, entityType: 'user', createdAt: '2026-10-15T08:00:00Z' },
      ],
    });
    rendre();
    expect(await screen.findByText('Connexion échouée')).toBeInTheDocument();
    expect(screen.getByText('Compte utilisateur')).toBeInTheDocument();
    expect(screen.queryByText('LOGIN_FAILED')).not.toBeInTheDocument();
    expect(screen.queryByText('user')).not.toBeInTheDocument();
  });

  it('service indisponible : le dit, et garde les acces de gestion', async () => {
    getSuperAdmin.mockRejectedValue(new Error('503'));
    rendre();
    expect(await screen.findByText('Tableau de bord indisponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir les cabinets' })).toHaveAttribute('href', '/admin/workspaces');
  });
});

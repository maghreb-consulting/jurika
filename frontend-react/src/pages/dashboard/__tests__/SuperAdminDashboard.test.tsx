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

const rendre = () => render(<SuperAdminDashboard />, { wrapper: MemoryRouter });

describe('SuperAdminDashboard', () => {
  beforeEach(() => {
    getSuperAdmin.mockReset();
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
    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('87')).toBeInTheDocument();
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
    const forfaits = await screen.findByRole('list', { name: 'Cabinets par forfait' });
    expect(within(forfaits).getByText('Professionnel')).toBeInTheDocument();
    expect(within(forfaits).getByText('Non défini')).toBeInTheDocument();
    expect(within(forfaits).getByText('3')).toBeInTheDocument();
    const statuts = screen.getByRole('list', { name: 'Cabinets par statut' });
    expect(within(statuts).getByText('Essai')).toBeInTheDocument();
    expect(within(statuts).getByText('En attente de vérification')).toBeInTheDocument();
    expect(screen.queryByText('PENDING_VERIFICATION')).not.toBeInTheDocument();
    expect(screen.queryByText('ESSAI')).not.toBeInTheDocument();
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

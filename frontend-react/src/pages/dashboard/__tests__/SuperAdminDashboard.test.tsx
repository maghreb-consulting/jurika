import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SuperAdminDashboard } from '../SuperAdminDashboard';

// Lot L6b (E9) : tableau de bord de la plateforme (super-admin). Meme defaut que celui du
// superviseur : un instantane partiel ne doit ni planter l'ecran ni inventer un zero.

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
    expect(await screen.findByText('Utilisateurs plateforme')).toBeInTheDocument();
    expect(screen.getAllByText('--').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByRole('heading', { level: 1, name: 'Tableau de bord de la plateforme' })).toBeInTheDocument();
  });

  it('rend les chiffres de la plateforme et la sante des services', async () => {
    getSuperAdmin.mockResolvedValue({
      totalWorkspaces: 12,
      activeWorkspaces30d: 9,
      totalUsers: 87,
      storage: { totalBytes: 2 * 1024 * 1024 * 1024, fileCount: 340 },
      criticalAuditEvents24h: [],
      servicesHealth: { 'auth-service': 'UP', 'ai-service': 'DOWN' },
      workspacesParStatut: [{ label: 'ACTIVE', count: 10 }],
    });
    rendre();
    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('87')).toBeInTheDocument();
    expect(screen.getByText('2,00 Go')).toBeInTheDocument();
    expect(screen.getByText('340 documents')).toBeInTheDocument();
    expect(screen.getByText('10 actifs')).toBeInTheDocument();
    expect(screen.getByText('auth-service')).toBeInTheDocument();
    expect(screen.getByText('Hors ligne')).toBeInTheDocument();
  });

  it('evenements critiques : libelle du serveur, jamais le code (L6b E9)', async () => {
    getSuperAdmin.mockResolvedValue({
      criticalAuditEvents24h: [
        { id: 'e1', action: 'LOGIN_FAILED', actionLabel: 'Connexion échouée', userId: null,
          workspaceId: null, entityType: 'user', createdAt: '2026-10-15T08:00:00Z' },
      ],
    });
    rendre();
    expect(await screen.findByText('Connexion échouée')).toBeInTheDocument();
    expect(screen.queryByText('LOGIN_FAILED')).not.toBeInTheDocument();
  });

  it('service indisponible : le dit, et garde les acces de gestion', async () => {
    getSuperAdmin.mockRejectedValue(new Error('503'));
    rendre();
    expect(await screen.findByText('Tableau de bord indisponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voir les cabinets' })).toHaveAttribute('href', '/admin/workspaces');
  });
});

/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { Role } from '../../../types/auth';

/**
 * Lot M (2026-06-30) — Non-regression : le CLIENT a une experience reduite
 * (Dashboard + Data Room). Il ne doit PAS voir Upgrade, Chat ni ChatBot IA.
 * Les autres roles (EMPLOYE / SUPERVISEUR / SUPER_ADMIN) restent inchanges.
 */

const stateRef: {
  current: {
    user: { role: Role; email: string; workspaceId: string; workspaceCode?: string } | null;
    logout: () => Promise<void>;
  };
} = {
  current: {
    user: { role: 'CLIENT', email: 'client@acme.ma', workspaceId: 'ws-12345', workspaceCode: 'JUR-ACME1' },
    logout: vi.fn().mockResolvedValue(undefined),
  },
};

// Sidebar / AppTopNav consomment useAuthStore via selecteur : (s) => s.user.
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof stateRef.current) => T) => selector(stateRef.current),
}));

// Stubs des widgets topbar (appels reseau au mount) — hors-scope de ce test nav.
vi.mock('../DeadlineCountWidget', () => ({ DeadlineCountWidget: () => null }));
vi.mock('../NotificationBell', () => ({ NotificationBell: () => null }));
vi.mock('../ThemeToggle', () => ({ ThemeToggle: () => null }));

import { Sidebar } from '../Sidebar';
import { AppTopNav } from '../AppTopNav';

function setRole(role: Role) {
  stateRef.current.user = {
    role,
    email: `${role.toLowerCase()}@acme.ma`,
    workspaceId: 'ws-12345',
    workspaceCode: 'JUR-ACME1',
  };
}

function renderSidebar() {
  return render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>,
  );
}

function renderTopNav() {
  return render(
    <MemoryRouter>
      <AppTopNav />
    </MemoryRouter>,
  );
}

describe('Lot M — restrictions de navigation CLIENT', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Sidebar', () => {
    it('CLIENT : pas de Upgrade, pas de Chat, pas de ChatBot IA', () => {
      setRole('CLIENT');
      renderSidebar();
      expect(screen.queryByText('Upgrade')).not.toBeInTheDocument();
      expect(screen.queryByText('Chat')).not.toBeInTheDocument();
      expect(screen.queryByText('ChatBot IA')).not.toBeInTheDocument();
      // Garde l'essentiel : Dashboard + Data Room.
      expect(screen.getByText('Mon Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Data Room')).toBeInTheDocument();
    });

    it('EMPLOYE : Upgrade + Chat + ChatBot IA toujours presents (non-regression)', () => {
      setRole('EMPLOYE');
      renderSidebar();
      expect(screen.getByText('Upgrade')).toBeInTheDocument();
      expect(screen.getByText('Chat')).toBeInTheDocument();
      expect(screen.getByText('ChatBot IA')).toBeInTheDocument();
    });

    it('SUPERVISEUR : Upgrade + Chat + ChatBot IA toujours presents (non-regression)', () => {
      setRole('SUPERVISEUR');
      renderSidebar();
      expect(screen.getByText('Upgrade')).toBeInTheDocument();
      expect(screen.getByText('Chat')).toBeInTheDocument();
      expect(screen.getByText('ChatBot IA')).toBeInTheDocument();
    });
  });

  describe('AppTopNav', () => {
    it('CLIENT : pas de CTA Upgrade, pas de Chat', () => {
      setRole('CLIENT');
      renderTopNav();
      expect(screen.queryByTestId('topnav-upgrade-cta')).not.toBeInTheDocument();
      expect(screen.queryByText('Chat')).not.toBeInTheDocument();
      // Garde son espace Data Room.
      expect(screen.getByText('Mon Espace')).toBeInTheDocument();
    });

    it('EMPLOYE : CTA Upgrade + Chat presents (non-regression)', () => {
      setRole('EMPLOYE');
      renderTopNav();
      expect(screen.getByTestId('topnav-upgrade-cta')).toBeInTheDocument();
      expect(screen.getByText('Chat')).toBeInTheDocument();
    });
  });
});

import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { Role } from '../../../types/auth';

/**
 * Lot M (2026-06-30) — Non-regression : le CLIENT a une experience reduite
 * (Dashboard + Data Room). Il ne doit PAS voir le changement de forfait ni le Chat.
 * Lot L6 (E6) : le super-admin s'affiche "JURIKA" (CDC 3.1) ; le compteur
 * d'echeances (calendrier du cabinet) n'est montre qu'au cabinet.
 * (Les tests de l'ancienne Sidebar, jamais montee, sont retires avec elle.)
 * Lot L6b (E3) : l'ancienne barre AppTopNav est scindee en en-tete (AppEnTete) et menu
 * lateral (MenuLateral) ; les memes donnees sont attendues dans la coquille rendue.
 */

const stateRef: {
  current: {
    user: { role: Role; email: string; userId: string; workspaceId: string; workspaceCode?: string } | null;
    logout: () => Promise<void>;
  };
} = {
  current: {
    user: { role: 'CLIENT', email: 'client@acme.ma', userId: 'u-1', workspaceId: 'ws-12345', workspaceCode: 'JUR-ACME1' },
    logout: vi.fn().mockResolvedValue(undefined),
  },
};

// L'en-tete consomme useAuthStore via selecteur : (s) => s.user.
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof stateRef.current) => T) => selector(stateRef.current),
}));

// Stubs des widgets topbar (appels reseau au mount) — hors-scope de ce test nav.
vi.mock('../DeadlineCountWidget', () => ({ DeadlineCountWidget: () => <span data-testid="echeances" /> }));
vi.mock('../NotificationBell', () => ({ NotificationBell: () => <span data-testid="cloche" /> }));
vi.mock('../ThemeToggle', () => ({ ThemeToggle: () => null }));

import { AppEnTete } from '../AppEnTete';
import { MenuLateral } from '../MenuLateral';
import { useAidesStore } from '../../../store/aidesStore';

function setRole(role: Role) {
  stateRef.current.user = {
    role,
    email: `${role.toLowerCase()}@acme.ma`,
    userId: 'u-1',
    workspaceId: 'ws-12345',
    workspaceCode: 'JUR-ACME1',
  };
}

function renderTopNav() {
  const role = stateRef.current.user!.role;
  return render(
    <MemoryRouter>
      <AppEnTete />
      <MenuLateral role={role} replie={false} onBasculer={() => undefined} />
    </MemoryRouter>,
  );
}

describe('Coquille : en-tete et menu lateral', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('CLIENT : pas de changement de forfait, pas de Chat, pas d’échéances', () => {
    setRole('CLIENT');
    renderTopNav();
    expect(screen.queryByTestId('topnav-upgrade-cta')).not.toBeInTheDocument();
    expect(screen.queryByText('Messagerie')).not.toBeInTheDocument();
    expect(screen.queryByTestId('echeances')).not.toBeInTheDocument();
    // Garde son espace Data Room.
    expect(screen.getByText('Mon espace')).toBeInTheDocument();
  });

  // L6b (E8) : le changement de forfait est une operation du SUPERVISEUR (billing-service,
  // BillingController : apercu et changement en hasRole('SUPERVISEUR')). L'employe ne voit
  // plus un bouton que le serveur lui refuse (backlog L0).
  it('EMPLOYE : Chat et échéances présents, pas de changement de forfait (refusé par le serveur)', () => {
    setRole('EMPLOYE');
    renderTopNav();
    expect(screen.queryByTestId('topnav-upgrade-cta')).not.toBeInTheDocument();
    expect(screen.getByText('Messagerie')).toBeInTheDocument();
    expect(screen.getByTestId('echeances')).toBeInTheDocument();
  });

  it('SUPERVISEUR : changement de forfait présent dans l’en-tête', () => {
    setRole('SUPERVISEUR');
    renderTopNav();
    expect(screen.getByTestId('topnav-upgrade-cta')).toHaveTextContent('Changer de forfait');
    expect(screen.getByTestId('topnav-upgrade-cta')).toHaveAttribute('href', '/app/billing/change-plan');
  });

  it('SUPER_ADMIN : présenté sous le nom JURIKA, sans le code du workspace qui l’héberge', () => {
    setRole('SUPER_ADMIN');
    renderTopNav();
    expect(screen.getByTestId('topnav-nom')).toHaveTextContent('JURIKA');
    expect(screen.getByTestId('topnav-sous-titre')).toHaveTextContent('Administration de la plateforme');
    expect(screen.queryByText('super_admin@acme.ma')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Menu du compte/ }));
    expect(screen.queryByText('JUR-ACME1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('echeances')).not.toBeInTheDocument();
  });

  it('le menu du compte propose « Mon compte », la visite guidée et le réaffichage des aides', async () => {
    setRole('EMPLOYE');
    useAidesStore.setState({ masquees: new Set(['visite-employe', 'page-ticket']), utilisateur: null, chargee: true });
    renderTopNav();
    fireEvent.click(screen.getByRole('button', { name: /Menu du compte/ }));
    expect(screen.getByRole('menuitem', { name: /Mon compte/ })).toHaveAttribute('href', '/profile');
    fireEvent.click(screen.getByRole('menuitem', { name: /Revoir la visite guidée/ }));
    expect(useAidesStore.getState().masquees.has('visite-employe')).toBe(false);
    expect(useAidesStore.getState().masquees.has('page-ticket')).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: /Menu du compte/ }));
    fireEvent.click(screen.getByRole('menuitem', { name: /Réafficher les aides/ }));
    expect(useAidesStore.getState().masquees.size).toBe(0);
    expect(await screen.findByText('Les aides masquées sont de nouveau affichées.')).toBeInTheDocument();
  });
});

// Lot L5 (E9, RG-COM-09) : le client est averti par courriel ; pas de cloche.
describe('Cloche des notifications', () => {
  it('absente pour le CLIENT, présente pour EMPLOYE et SUPERVISEUR', () => {
    setRole('CLIENT');
    const { unmount } = renderTopNav();
    expect(screen.queryByTestId('cloche')).not.toBeInTheDocument();
    unmount();
    for (const role of ['EMPLOYE', 'SUPERVISEUR'] as Role[]) {
      setRole(role);
      const r = renderTopNav();
      expect(screen.getByTestId('cloche')).toBeInTheDocument();
      r.unmount();
    }
  });
});

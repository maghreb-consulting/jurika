import { lazy } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Role } from '../../../types/auth';

/**
 * Lot L6b (E3) : coquille commune. Menu lateral par role, ecran charge a la demande avec
 * squelette, preferences du compte lues a la connexion et erreur visible, menu repliable.
 */
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api }));
vi.mock('../../contrat/useGardeContrat', () => ({ useGardeContrat: () => 'LIBRE' }));
vi.mock('../../chatbot/AssistantJuridique', () => ({ AssistantJuridique: () => null }));
vi.mock('../../ui/VisiteGuidee', () => ({ VisiteGuidee: () => null }));
vi.mock('../../../lib/useSessionKeepAlive', () => ({ useSessionKeepAlive: () => undefined }));
vi.mock('../DeadlineCountWidget', () => ({ DeadlineCountWidget: () => <span data-testid="echeances" /> }));
vi.mock('../NotificationBell', () => ({ NotificationBell: () => <span data-testid="cloche" /> }));
vi.mock('../IceReminderBanner', () => ({ IceReminderBanner: () => null }));
vi.mock('../BandeauEssai', () => ({ BandeauEssai: () => null }));
vi.mock('../BandeauImpaye', () => ({ BandeauImpaye: () => null }));
vi.mock('../../../store/chatUnreadStore', () => ({
  useChatUnreadStore: (sel: (s: { total: number }) => number) => sel({ total: 0 }),
  useChatUnreadSync: () => undefined,
}));

import { AppShell } from '../AppShell';
import { useAuthStore } from '../../../store/authStore';
import { usePreferencesStore } from '../../../store/preferencesStore';
import { useAidesStore } from '../../../store/aidesStore';

function connecter(role: Role) {
  useAuthStore.setState({
    user: { role, email: `${role.toLowerCase()}@cabinet.ma`, userId: `u-${role}`, workspaceId: 'ws-1', workspaceCode: 'JUR-TEST1' } as never,
    isAuthenticated: true,
  } as never);
}

let resoudre: (() => void) | null = null;
const EcranLent = lazy(
  () =>
    new Promise<{ default: () => JSX.Element }>((ok) => {
      resoudre = () => ok({ default: () => <h1>Écran chargé</h1> });
    }),
);

function rendre(chemin = '/dashboard', ecran = <p>Contenu de la page</p>) {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="*" element={ecran} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  api.get.mockReset();
  api.put.mockReset();
  api.get.mockImplementation((url: string) =>
    url === '/users/me/preferences' ? Promise.resolve({ data: { theme: 'CLAIR', juriMasque: false } }) : Promise.resolve({ data: { cles: [] } }),
  );
  usePreferencesStore.getState().vider();
  useAidesStore.getState().vider();
  localStorage.clear();
});

afterEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false } as never);
});

describe('Coquille', () => {
  it.each([
    ['EMPLOYE', ['Mes tickets', 'Mes Data Rooms'], ['Cabinets', 'Traçabilité']],
    ['SUPERVISEUR', ['Tableau de bord', 'Tous les tickets', 'Dossiers à vérifier', 'Équipe'], ['Cabinets', 'Mes tickets']],
    ['CLIENT', ['Mon espace', 'Mes demandes', 'Requêtes du cabinet'], ['Messagerie', 'Mes tickets']],
    ['SUPER_ADMIN', ['Cabinets', 'Journal d’audit', 'Système de design'], ['Mes tickets', 'Toutes les Data Rooms', 'Messagerie']],
  ] as [Role, string[], string[]][])('menu lateral du role %s', async (role, presents, absents) => {
    connecter(role);
    rendre();
    const menu = within(screen.getByTestId('menu-lateral')).getByRole('navigation', { name: 'Menu principal' });
    for (const p of presents) expect(within(menu).getByRole('link', { name: p })).toBeInTheDocument();
    for (const a of absents) expect(within(menu).queryByRole('link', { name: a })).not.toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Contenu de la page');
    await act(async () => undefined);
  });

  it('entree active : aria-current sur la page courante (detail d un ticket)', async () => {
    connecter('EMPLOYE');
    rendre('/tickets/42');
    expect(screen.getByRole('link', { name: 'Mes tickets' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Tableau de bord' })).not.toHaveAttribute('aria-current');
    await act(async () => undefined);
  });

  it('ecran charge a la demande : squelette de page pendant le chargement, puis l ecran', async () => {
    connecter('EMPLOYE');
    rendre('/tickets', <EcranLent />);
    expect(within(screen.getByRole('main')).getByRole('status')).toHaveTextContent('Chargement de la page');
    await act(async () => {
      resoudre?.();
    });
    expect(await screen.findByRole('heading', { name: 'Écran chargé' })).toBeInTheDocument();
  });

  it('lit les preferences du compte a la connexion et applique le theme choisi', async () => {
    connecter('EMPLOYE');
    rendre();
    await act(async () => undefined);
    expect(api.get).toHaveBeenCalledWith('/users/me/preferences');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(usePreferencesStore.getState().preferences?.theme).toBe('CLAIR');
  });

  it('preferences illisibles : message visible dans la coquille, avec Reessayer', async () => {
    api.get.mockImplementation((url: string) =>
      url === '/users/me/preferences' ? Promise.reject({ response: { status: 503 } }) : Promise.resolve({ data: { cles: [] } }),
    );
    connecter('EMPLOYE');
    rendre();
    expect(await screen.findByText(/n’ont pas pu être lues/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument();
  });

  it('menu repliable : le choix est retenu dans ce navigateur, les libelles restent accessibles', async () => {
    connecter('EMPLOYE');
    rendre();
    fireEvent.click(screen.getByRole('button', { name: 'Réduire le menu' }));
    expect(localStorage.getItem('jurika.menu.replie')).toBe('1');
    expect(screen.getByRole('button', { name: 'Déplier le menu' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('link', { name: 'Mes tickets' })).toHaveAttribute('title', 'Mes tickets');
    await act(async () => undefined);
  });

  it('lien d evitement vers le contenu', async () => {
    connecter('CLIENT');
    rendre();
    expect(screen.getByRole('link', { name: 'Aller au contenu' })).toHaveAttribute('href', '#contenu');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenu');
    await act(async () => undefined);
  });
});

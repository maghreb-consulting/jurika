/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { Role } from '../../../types/auth';

/**
 * Fix 2026-06-30 — Onglet Notifications : ne doit plus rester vide.
 *  - knownTypes vide -> repli sur la liste de types par defaut.
 *  - role CLIENT -> pas de types internes (tickets / transfert / workflow).
 *  - toggle -> persiste via setPreferences().
 */

const getPreferences = vi.fn();
const setPreferences = vi.fn();

vi.mock('../../../services/notification.service', () => ({
  notificationService: {
    getPreferences: (...a: unknown[]) => getPreferences(...a),
    setPreferences: (...a: unknown[]) => setPreferences(...a),
  },
}));

const roleRef: { current: Role } = { current: 'EMPLOYE' };
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u1', workspaceId: 'w1', email: 'e@jurika.ma', role: roleRef.current }),
}));

import { NotificationPreferencesTab } from '../NotificationPreferencesTab';

describe('<NotificationPreferencesTab>', () => {
  beforeEach(() => {
    getPreferences.mockReset();
    setPreferences.mockReset();
    roleRef.current = 'EMPLOYE';
  });

  it('affiche des toggles meme quand le backend renvoie knownTypes vide (repli par defaut)', async () => {
    getPreferences.mockResolvedValue({ preferences: {}, knownTypes: [] });

    render(<NotificationPreferencesTab />);

    await waitFor(() => expect(screen.getByTestId('notif-pref-CHAT_MESSAGE')).toBeInTheDocument());
    // Quelques types par defaut presents.
    expect(screen.getByTestId('notif-pref-TICKET_ASSIGNED')).toBeInTheDocument();
    expect(screen.getByTestId('notif-pref-DEADLINE_DUE')).toBeInTheDocument();
    // Pas d'etat vide quand il y a des toggles.
    expect(screen.queryByTestId('notif-pref-empty')).not.toBeInTheDocument();
  });

  it('respecte knownTypes du backend quand fourni', async () => {
    getPreferences.mockResolvedValue({
      preferences: { CHAT_MESSAGE: false },
      knownTypes: ['CHAT_MESSAGE', 'WORKSPACE_EVENT'],
    });

    render(<NotificationPreferencesTab />);

    await waitFor(() => expect(screen.getByTestId('notif-pref-CHAT_MESSAGE')).toBeInTheDocument());
    expect(screen.getByTestId('notif-pref-WORKSPACE_EVENT')).toBeInTheDocument();
    expect(screen.queryByTestId('notif-pref-TICKET_ASSIGNED')).not.toBeInTheDocument();
  });

  it('CLIENT : pas de types internes (tickets / transfert / workflow)', async () => {
    roleRef.current = 'CLIENT';
    getPreferences.mockResolvedValue({ preferences: {}, knownTypes: [] });

    render(<NotificationPreferencesTab />);

    await waitFor(() => expect(screen.getByTestId('notif-pref-CHAT_MESSAGE')).toBeInTheDocument());
    expect(screen.queryByTestId('notif-pref-TICKET_ASSIGNED')).not.toBeInTheDocument();
    expect(screen.queryByTestId('notif-pref-DOSSIER_TRANSFER')).not.toBeInTheDocument();
    expect(screen.queryByTestId('notif-pref-WORKFLOW_COMPLETED')).not.toBeInTheDocument();
    // Conserve les types pertinents.
    expect(screen.getByTestId('notif-pref-DEADLINE_DUE')).toBeInTheDocument();
  });

  it('toggle persiste via setPreferences()', async () => {
    getPreferences.mockResolvedValue({ preferences: { CHAT_MESSAGE: true }, knownTypes: ['CHAT_MESSAGE'] });
    setPreferences.mockResolvedValue(undefined);

    render(<NotificationPreferencesTab />);

    const row = await screen.findByTestId('notif-pref-CHAT_MESSAGE');
    fireEvent.click(row.querySelector('button')!);

    await waitFor(() => expect(setPreferences).toHaveBeenCalledWith({ CHAT_MESSAGE: false }));
  });

  it('service injoignable : affiche tout de meme les toggles par defaut + bandeau erreur', async () => {
    getPreferences.mockRejectedValue(new Error('Network Error'));

    render(<NotificationPreferencesTab />);

    await waitFor(() => expect(screen.getByTestId('notif-pref-CHAT_MESSAGE')).toBeInTheDocument());
  });
});

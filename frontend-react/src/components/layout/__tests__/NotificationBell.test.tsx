import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { Notification } from '../../../types/notification';

const list = vi.fn();
const unreadCount = vi.fn();
const markRead = vi.fn();
vi.mock('../../../services/notification.service', () => ({
  notificationService: {
    list: (...a: unknown[]) => list(...a),
    unreadCount: (...a: unknown[]) => unreadCount(...a),
    markRead: (...a: unknown[]) => markRead(...a),
    markAllRead: vi.fn(),
  },
}));
vi.mock('../../../lib/realtimeSocket', () => ({ getSocket: () => null }));

import { NotificationBell } from '../NotificationBell';

const base = { workspaceId: 'ws', userId: 'u', actionUrl: null, metadata: null, createdAt: new Date().toISOString() };
const lue: Notification = { ...base, id: 'n1', type: 'DOSSIER_TRANSFER', title: 'Transfert accepté', message: 'ATLAS', readAt: '2026-10-12T08:00:00Z' };
const nonLue: Notification = { ...base, id: 'n2', type: 'DEADLINE_DUE', title: 'Délai dépassé', message: 'CNSS', readAt: null };

// Lot L5 (E9, RG-COM-08) : centre de notifications (liste, lu / non lu, aide).
describe('<NotificationBell>', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    unreadCount.mockResolvedValue(1);
    list.mockResolvedValue({ items: [nonLue, lue], total: 2 });
  });

  it('liste les notifications et filtre les non lues', async () => {
    render(<NotificationBell />, { wrapper: MemoryRouter });
    fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));

    expect(await screen.findByText('Délai dépassé')).toBeInTheDocument();
    expect(screen.getByText('Transfert accepté')).toBeInTheDocument();
    expect(screen.getAllByTestId('notification-non-lue')).toHaveLength(1);
    expect(screen.getByText(/Délai à échéance/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: 'Non lues' }));
    await waitFor(() => expect(screen.queryByText('Transfert accepté')).not.toBeInTheDocument());
    expect(screen.getByText('Délai dépassé')).toBeInTheDocument();
  });

  it('porte son aide', async () => {
    render(<NotificationBell />, { wrapper: MemoryRouter });
    fireEvent.click(screen.getByRole('button', { name: /Notifications/ }));
    expect(await screen.findByRole('button', { name: 'Aide sur les notifications' })).toBeInTheDocument();
  });
});

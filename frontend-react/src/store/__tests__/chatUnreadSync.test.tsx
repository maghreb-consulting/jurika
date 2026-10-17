import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

const listConversations = vi.fn(() => Promise.resolve([]));
const getAbonnement = vi.fn();
vi.mock('../../services/chat.service', () => ({ chatService: { listConversations: () => listConversations() } }));
vi.mock('../../services/billing.service', () => ({ billingService: { getAbonnement: () => getAbonnement() } }));
vi.mock('../../lib/realtimeSocket', () => ({ getSocket: () => null }));

import { useAuthStore } from '../authStore';
import { useChatUnreadSync } from '../chatUnreadStore';
import { oublierForfait } from '../../lib/useForfait';

function connecter(role: string) {
  useAuthStore.setState({ user: { userId: 'u1', role } as never, isAuthenticated: true });
}

/**
 * Lot L6b (E13), exploration de bout en bout : le compteur de la messagerie (menu lateral)
 * appelait GET /chat/conversations pour TOUS les roles. Le realtime-service repond 403
 * ROLE_REFUSE au client et au super-admin (RG-U10), et 403 FORFAIT_SANS_MESSAGERIE a un cabinet
 * dont le forfait n'inclut pas la messagerie : un 403 a chaque ecran.
 */
describe('useChatUnreadSync : seulement quand la messagerie est ouverte a ce compte', () => {
  beforeEach(() => {
    listConversations.mockClear();
    getAbonnement.mockReset();
    oublierForfait();
  });

  it.each(['CLIENT', 'SUPER_ADMIN'])('%s : aucun appel a la messagerie', async (role) => {
    getAbonnement.mockResolvedValue({ fonctions: [] });
    connecter(role);
    renderHook(() => useChatUnreadSync());
    await new Promise((r) => setTimeout(r, 20));
    expect(listConversations).not.toHaveBeenCalled();
  });

  it('employe, forfait sans messagerie : aucun appel', async () => {
    getAbonnement.mockResolvedValue({ fonctions: [{ code: 'MESSAGERIE_INTERNE', incluse: false }] });
    connecter('EMPLOYE');
    renderHook(() => useChatUnreadSync());
    await waitFor(() => expect(getAbonnement).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 20));
    expect(listConversations).not.toHaveBeenCalled();
  });

  it('employe, forfait avec messagerie : le compteur est charge', async () => {
    getAbonnement.mockResolvedValue({ fonctions: [{ code: 'MESSAGERIE_INTERNE', incluse: true }] });
    connecter('EMPLOYE');
    renderHook(() => useChatUnreadSync());
    await waitFor(() => expect(listConversations).toHaveBeenCalled());
  });
});

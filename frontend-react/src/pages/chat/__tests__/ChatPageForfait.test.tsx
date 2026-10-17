import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b (exploration de bout en bout sur le Z440, 2026-10-16) : /chat d'un cabinet dont le forfait
 * n'inclut pas la messagerie recevait un 403 FORFAIT_SANS_MESSAGERIE : l'ecran montait la messagerie
 * (et l'appelait) AVANT de connaitre le forfait. Il attend maintenant la reponse.
 */
const listConversations = vi.fn();
let resoudre: (v: unknown) => void = () => undefined;
const getAbonnement = vi.fn(() => new Promise((r) => { resoudre = r; }));
vi.mock('../../../services/billing.service', () => ({ billingService: { getAbonnement: () => getAbonnement() } }));
vi.mock('../../../services/chat.service', () => ({
  chatService: { listConversations: (...a: unknown[]) => listConversations(...a), getMessages: vi.fn(async () => []), markRead: vi.fn() },
}));
vi.mock('../../../services/auth.service', () => ({ authService: { listChatContacts: vi.fn(async () => []) } }));
vi.mock('../../../lib/realtimeSocket', () => ({ getSocket: () => null }));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u-1', workspaceId: 'w-1', role: 'EMPLOYE' }),
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: 'u-1', workspaceId: 'w-1', role: 'EMPLOYE' } }),
}));

const { ChatPage } = await import('../ChatPage');
const { oublierForfait } = await import('../../../lib/useForfait');

describe('ChatPage : le forfait est connu avant tout appel a la messagerie', () => {
  beforeEach(() => { Element.prototype.scrollIntoView = vi.fn(); listConversations.mockReset(); listConversations.mockResolvedValue([]); oublierForfait(); });

  it('forfait sans messagerie : aucun appel, explication', async () => {
    render(<MemoryRouter><ChatPage /></MemoryRouter>);
    expect(listConversations).not.toHaveBeenCalled();
    await act(async () => { resoudre({ fonctions: [{ code: 'MESSAGERIE_INTERNE', incluse: false, disponibleDes: 'Professionnel' }] }); });
    expect(await screen.findByText(/Messagerie interne/)).toBeInTheDocument();
    expect(listConversations).not.toHaveBeenCalled();
  });

  it('forfait avec messagerie : les conversations sont chargees apres la reponse', async () => {
    render(<MemoryRouter><ChatPage /></MemoryRouter>);
    expect(listConversations).not.toHaveBeenCalled();
    await act(async () => { resoudre({ fonctions: [{ code: 'MESSAGERIE_INTERNE', incluse: true }] }); });
    await vi.waitFor(() => expect(listConversations).toHaveBeenCalled());
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatPage } from './ChatPage';
import type { Conversation, ChatMessage } from '../../types/chat';

// ---------------------------------------------------------------------------
// BUG 2 (2026-06-26) — Le SUPERVISEUR doit pouvoir envoyer des messages : le
// chat est l'exception voulue à la règle « superviseur oversight-only ».
// On monte ChatPage avec un user SUPERVISEUR et on vérifie qu'il n'y a AUCUNE
// bannière "lecture seule" et que la zone de saisie est bien rendue.
// ---------------------------------------------------------------------------

const SUPERVISEUR = { userId: 'sup-1', role: 'SUPERVISEUR' };

vi.mock('../../store/authStore', () => ({
  useCurrentUser: () => SUPERVISEUR,
}));

vi.mock('../../services/chat.service', () => ({
  chatService: {
    // Inliné dans la factory : vi.mock est hoisté, pas d'accès aux consts du module.
    listConversations: vi.fn().mockResolvedValue([
      {
        id: 'conv-1',
        peerId: 'peer-emp-1',
        lastMessage: 'Bonjour superviseur',
        lastMessageAt: '2026-06-26T10:00:00.000Z',
        unreadCount: 0,
      } satisfies Conversation,
    ]),
    listMessages: vi.fn().mockResolvedValue([] as ChatMessage[]),
  },
}));

vi.mock('../../services/auth.service', () => ({
  authService: {
    listChatContacts: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('../../lib/realtimeSocket', () => ({
  getSocket: () => null,
}));

beforeEach(() => {
  vi.clearAllMocks();
  // jsdom n'implémente pas scrollIntoView (appelé par l'auto-scroll au montage).
  Element.prototype.scrollIntoView = vi.fn();
});

describe('ChatPage — accès superviseur (BUG 2)', () => {
  it("n'affiche AUCUNE bannière lecture seule pour un SUPERVISEUR", async () => {
    render(<ChatPage />);
    await waitFor(() => {
      expect(screen.getByText('Bonjour superviseur')).toBeInTheDocument();
    });
    expect(screen.queryByText(/lecture seule/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Mode superviseur/i)).not.toBeInTheDocument();
  });

  it('rend la zone de saisie quand une conversation est ouverte', async () => {
    const user = userEvent.setup();
    render(<ChatPage />);

    const convoText = await screen.findByText('Bonjour superviseur');
    await user.click(convoText.closest('button')!);

    // L'input de saisie est présent -> l'envoi n'est plus gaté par isReadOnly.
    expect(
      await screen.findByPlaceholderText('Ecrire un message...'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Mode superviseur — lecture seule/i)).not.toBeInTheDocument();
  });
});

/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { Conversation } from '../../../types/chat';

// --- Mocks services / socket / store -----------------------------------------
vi.mock('../../../services/chat.service', () => ({
  chatService: {
    listConversations: vi.fn().mockResolvedValue([]),
    listMessages: vi.fn().mockResolvedValue([]),
    envoyer: vi.fn(),
  },
}));

vi.mock('../../../services/auth.service', () => ({
  authService: {
    listChatContacts: vi.fn().mockResolvedValue([
      { userId: 'peer-1', firstName: 'Sara', lastName: 'B', email: 's@jurika.ma', role: 'EMPLOYE', status: 'ACTIVE' },
    ]),
  },
}));

const fakeSocket = {
  connected: true,
  on: vi.fn(),
  off: vi.fn(),
  emit: vi.fn(),
};
vi.mock('../../../lib/realtimeSocket', () => ({
  getSocket: () => fakeSocket,
}));

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'me', firstName: 'Moi', lastName: 'Test', email: 'me@jurika.ma', role: 'EMPLOYE' }),
}));

import { ChatPage } from '../ChatPage';
import { chatService } from '../../../services/chat.service';

describe('<ChatPage>', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // jsdom n'implémente pas scrollIntoView (utilisé par l'auto-scroll).
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("utilise un <textarea> multi-lignes pour la saisie du message", async () => {
    render(<ChatPage />);

    // Ouvre la liste de contacts puis sélectionne un correspondant -> active la
    // conversation, ce qui monte la zone de saisie.
    fireEvent.click(await screen.findByTestId('chat-new-conversation'));
    fireEvent.click(await screen.findByTestId('chat-contact-item'));

    const input = await screen.findByTestId('chat-message-input');
    expect(input.tagName).toBe('TEXTAREA');
    expect(input).toHaveClass('resize-none');

    // Shift+Enter n'envoie pas (nouvelle ligne) ; le socket ne doit pas emit.
    fireEvent.change(input, { target: { value: 'Bonjour' } });
    fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });
    expect(fakeSocket.emit).not.toHaveBeenCalledWith('chat:send', expect.anything(), expect.anything());
  });

  it('enregistre les listeners socket une seule fois (pas de churn)', async () => {
    render(<ChatPage />);
    await waitFor(() => expect(fakeSocket.on).toHaveBeenCalledWith('chat:message', expect.any(Function)));
    const messageBinds = fakeSocket.on.mock.calls.filter((c) => c[0] === 'chat:message').length;

    // Sélectionner une conversation ne doit PAS re-binder le socket.
    fireEvent.click(await screen.findByTestId('chat-new-conversation'));
    fireEvent.click(await screen.findByTestId('chat-contact-item'));

    const after = fakeSocket.on.mock.calls.filter((c) => c[0] === 'chat:message').length;
    expect(after).toBe(messageBinds);
  });

  // 2026-07-03 — Régression « Maximum update depth exceeded » (ChatPage.tsx:284).
  // Ouvrir une conversation avec unreadCount > 0 déclenchait une boucle infinie :
  // l'effet dépendait de `conversations` ET appelait `setConversations`
  // (prev.map crée toujours un nouveau tableau). Une boucle de re-render lève une
  // erreur qui ferait échouer ce montage — le test la capture donc directement,
  // et vérifie que le comportement fonctionnel est préservé (emit read + badge
  // remis à zéro).
  it('ouvre une conversation non lue sans boucle infinie de re-render', async () => {
    vi.mocked(chatService.listConversations).mockResolvedValueOnce([
      {
        id: 'conv-unread',
        peerId: 'peer-1',
        lastMessage: 'Message non lu',
        lastMessageAt: '2026-07-03T09:00:00.000Z',
        unreadCount: 3,
      } satisfies Conversation,
    ]);

    render(<ChatPage />);

    // Le badge de messages non lus (3) est visible dans la liste.
    const badge = await screen.findByText('3');
    expect(badge).toBeInTheDocument();

    // Ouvrir la conversation : historique chargé + accusé de lecture émis.
    fireEvent.click(await screen.findByText('Message non lu'));

    await waitFor(() =>
      expect(fakeSocket.emit).toHaveBeenCalledWith('chat:read', { conversationId: 'conv-unread' }),
    );

    // Le compteur non-lu retombe à zéro (badge retiré) et la page reste stable :
    // si une boucle infinie survenait, React aurait levé « Maximum update depth ».
    await waitFor(() => expect(screen.queryByText('3')).not.toBeInTheDocument());
    expect(await screen.findByTestId('chat-message-input')).toBeInTheDocument();
  });

  // Lot L5 (E9, RG-COM-06) : le serveur refuse un destinataire hors cabinet ;
  // son message s'affiche tel quel et le brouillon est rendu a l'utilisateur.
  it('affiche le refus du serveur et rend le message a la saisie', async () => {
    const refus = 'Ce destinataire n’appartient pas à votre cabinet.';
    vi.mocked(chatService.envoyer).mockRejectedValueOnce(new Error(refus));
    render(<ChatPage />);
    fireEvent.click(await screen.findByTestId('chat-new-conversation'));
    fireEvent.click(await screen.findByTestId('chat-contact-item'));
    const input = await screen.findByTestId('chat-message-input');
    fireEvent.change(input, { target: { value: 'Message refuse' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(await screen.findByText(refus)).toBeInTheDocument();
    expect((input as HTMLTextAreaElement).value).toBe('Message refuse');
  });

  // Lot L5b (E1, RG-COM-06) : retour de test « Hors-ligne, les messages ne partent pas ».
  // Le message est enregistre par le serveur (REST) meme sans temps reel ; l'indicateur
  // de connexion ne bloque jamais l'envoi.
  it('envoie et affiche le message meme sans connexion temps reel', async () => {
    fakeSocket.connected = false;
    vi.mocked(chatService.envoyer).mockResolvedValueOnce({
      id: 'm-1', conversationId: 'c-1', from: 'me', to: 'peer-1', body: 'Bonjour Sara',
      createdAt: '2026-10-13T10:00:00.000Z',
    });
    render(<ChatPage />);
    fireEvent.click(await screen.findByTestId('chat-new-conversation'));
    fireEvent.click(await screen.findByTestId('chat-contact-item'));
    const input = await screen.findByTestId('chat-message-input');
    fireEvent.change(input, { target: { value: 'Bonjour Sara' } });
    expect(screen.getByRole('button', { name: /Envoyer/ })).toBeEnabled();
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(chatService.envoyer).toHaveBeenCalledWith('peer-1', 'Bonjour Sara'));
    // Le message s'affiche dans la conversation et en apercu dans la liste.
    expect((await screen.findAllByText('Bonjour Sara')).length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/Hors-ligne/)).not.toBeInTheDocument();
    expect(screen.getByText(/Temps réel interrompu/)).toBeInTheDocument();
    fakeSocket.connected = true;
  });

  it("porte l'aide de la messagerie interne", async () => {
    render(<ChatPage />);
    expect(await screen.findByRole('note', { name: 'Messagerie interne du cabinet' })).toBeInTheDocument();
    expect(screen.getByText(/vos clients n’ont pas accès à la messagerie/)).toBeInTheDocument();
  });
});

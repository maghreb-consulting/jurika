import { useEffect } from 'react';
import { create } from 'zustand';
import { chatService } from '../services/chat.service';
import { getSocket } from '../lib/realtimeSocket';
import { useAuthStore } from './authStore';

/**
 * 2026-07-03 — Compteur de messages NON LUS pour le badge du menu « Chat »
 * (Sidebar + AppTopNav). Remplace l'ancien `badge: 3` codé en dur qui affichait
 * toujours « 3 » quel que soit le nombre réel de messages.
 *
 * Source de vérité = le realtime-service : GET /chat/conversations renvoie
 * unreadCount par conversation (COUNT des messages recipient=moi & read_at NULL).
 * On somme ces compteurs. On rafraîchit à chaque `chat:message` entrant (débounce)
 * et après qu'une conversation a été marquée lue (ChatPage émet chat:read).
 */
interface ChatUnreadState {
  total: number;
  refresh: () => Promise<void>;
}

let inFlight: Promise<void> | null = null;

export const useChatUnreadStore = create<ChatUnreadState>((set) => ({
  total: 0,
  refresh: async () => {
    // Déduplication : une seule requête en vol à la fois (évite les rafales
    // quand plusieurs messages arrivent d'un coup).
    if (inFlight) return inFlight;
    inFlight = (async () => {
      try {
        const convs = await chatService.listConversations();
        const total = convs.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0);
        set({ total });
      } catch {
        /* non-bloquant : le badge n'est jamais critique */
      } finally {
        inFlight = null;
      }
    })();
    return inFlight;
  },
}));

/** Rafraîchit le total depuis le serveur (utilisable hors composant React). */
export function refreshChatUnread(): void {
  void useChatUnreadStore.getState().refresh();
}

/**
 * Monté une fois par la barre de navigation (Sidebar / AppTopNav) : charge le
 * total au montage puis le recalcule à chaque message entrant (débounce 400 ms,
 * le temps que l'éventuel chat:read soit persisté côté serveur).
 */
export function useChatUnreadSync(): void {
  const userId = useAuthStore((s) => s.user?.userId);
  const refresh = useChatUnreadStore((s) => s.refresh);

  useEffect(() => {
    if (!userId) return;
    void refresh();
    const socket = getSocket();
    if (!socket) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const onMessage = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => void refresh(), 400);
    };
    socket.on('chat:message', onMessage);
    return () => {
      socket.off('chat:message', onMessage);
      if (timer) clearTimeout(timer);
    };
  }, [userId, refresh]);
}

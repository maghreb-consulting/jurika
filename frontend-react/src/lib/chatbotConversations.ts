import type { ChatbotEntry } from '../types/chat';

/**
 * Persistance locale (localStorage) des conversations de l'assistant IA, par
 * utilisateur. Il n'existe pas de stockage back dédié aux fils du chatbot :
 * on conserve donc l'historique côté client (clé namespacée par userId) pour
 * pouvoir basculer entre conversations et retrouver ses échanges au refresh.
 */
export interface ChatbotConversation {
  id: string;
  /** Titre = 1er message tronqué ; vide tant qu'aucune question posée. */
  title: string;
  entries: ChatbotEntry[];
  createdAt: string;
  updatedAt: string;
}

const KEY_PREFIX = 'jurika:chatbot:conversations:';
const MAX_CONVERSATIONS = 50;

export function storageKeyFor(userId: string | null | undefined): string {
  return `${KEY_PREFIX}${userId ?? 'anon'}`;
}

/** Titre lisible dérivé de la 1re question (tronqué). */
export function deriveTitle(question: string): string {
  const t = question.trim().replace(/\s+/g, ' ');
  return t.length > 42 ? `${t.slice(0, 42)}…` : t;
}

/**
 * Recharge et assainit les conversations : un `loading:true` persisté (onglet
 * fermé en plein appel) redevient false, et les échanges "pendants" (ni réponse
 * ni erreur) sont retirés pour ne pas laisser de bulle vide au rechargement.
 */
export function loadConversations(userId: string | null | undefined): ChatbotConversation[] {
  try {
    const raw = localStorage.getItem(storageKeyFor(userId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ChatbotConversation[];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((c) => ({
        ...c,
        entries: (c.entries ?? [])
          .map((e) => ({ ...e, loading: false }))
          .filter((e: ChatbotEntry) => Boolean(e.answer) || Boolean(e.error)),
      }))
      .slice(0, MAX_CONVERSATIONS);
  } catch {
    return [];
  }
}

export function saveConversations(
  userId: string | null | undefined,
  conversations: ChatbotConversation[],
): void {
  try {
    localStorage.setItem(
      storageKeyFor(userId),
      JSON.stringify(conversations.slice(0, MAX_CONVERSATIONS)),
    );
  } catch {
    /* quota / mode privé : best-effort, on n'échoue jamais l'UI */
  }
}

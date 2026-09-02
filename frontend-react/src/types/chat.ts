export interface ChatMessage {
  id: string;
  conversationId: string;
  from: string;
  to: string;
  body: string;
  createdAt: string;
  workspaceId?: string;
  pending?: boolean;
}

export interface Conversation {
  id: string;
  peerId: string;
  workspaceId?: string;
  lastMessage?: string | null;
  lastMessageAt?: string | null;
  unreadCount?: number;
}

export interface ChatbotSource {
  reference: string;
  article: string;
  /** Extrait de la source (backend: champ `extract`). Nullable côté KB tronquée. */
  extract: string;
  /** Score de pertinence du retrieval (vecteur/FTS), null pour les sources KB. */
  rank?: number | null;
}

export interface ChatbotAnswer {
  reponse: string;
  sources: ChatbotSource[];
  confidence: number;
  /** Mode de réponse : "llm" (génération ancrée) ou "fts" (repli KB+FTS). */
  ragMode?: string;
  /** Nombre de passages du corpus effectivement récupérés. */
  chunksRetrieved?: number;
}

export interface ChatbotEntry {
  id: string;
  question: string;
  answer?: ChatbotAnswer;
  error?: string;
  loading?: boolean;
  createdAt: string;
}

/**
 * Une source fiable ingeree dans le corpus RAG (1 entree par document).
 * Les cles sont en snake_case car renvoyees telles quelles par le backend
 * (JdbcTemplate -> noms de colonnes SQL).
 */
export interface ChatbotSourceDoc {
  doc_id: string;
  source: string;
  chunks: number;
  uploaded_by: string | null;
  created_at: string;
}

export type ChatTypingEvent = { from: string };
export type ChatReadAck = { conversationId: string };

export interface ChatSendAck {
  ok: boolean;
  message?: ChatMessage;
  error?: string;
}

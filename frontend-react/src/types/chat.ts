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
  /** Mode de réponse (lot L5 E8) : "llm" (rédaction ancrée), "extraits" (passages restitués), "aucune-source". */
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
 * Lot L5b E2 (RG-COM-12) : conversation du chatbot, conservee par le serveur pour
 * l'utilisateur connecte (jamais visible par un autre utilisateur).
 */
export interface ChatbotConversationResume {
  id: string;
  titre: string;
  archivee: boolean;
  archiveeLe: string | null;
  creeeLe: string;
  /** Date de la derniere question posee (tri de la liste). */
  derniereActivite: string;
  nombreEchanges: number;
}

/** Une question enregistree et sa reponse (sources citees comprises). */
export interface ChatbotEchange {
  id: string;
  question: string;
  reponse: string;
  sources: ChatbotSource[];
  ragMode: string | null;
  confidence: number | null;
  creeLe: string;
}

export interface ChatbotConversationDetail extends ChatbotConversationResume {
  echanges: ChatbotEchange[];
}

/** Reponse du serveur a une question posee dans une conversation (nouvelle ou existante). */
export interface ChatbotQuestionResultat {
  conversation: ChatbotConversationResume;
  echange: ChatbotEchange;
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

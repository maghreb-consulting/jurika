import axios, { type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from '../lib/api';
import { REALTIME_URL } from '../lib/realtimeSocket';
import type { ChatMessage, Conversation } from '../types/chat';

/**
 * The realtime service exposes its own REST endpoints (chat history) directly,
 * not through the Java gateway. We therefore use a separate axios instance
 * pointing at VITE_REALTIME_URL.
 *
 * NOTE V9 (2026-06-25) : le transfert de dossier a quitte le realtime-service.
 * Il est desormais authoritatif dans ticket-service (responsable_id durable +
 * reassignation des tickets + audit DOSSIER_TRANSFERE) — cf. transfer.service.ts.
 */
const realtimeApi = axios.create({
  baseURL: REALTIME_URL,
  headers: { 'Content-Type': 'application/json' },
});

realtimeApi.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccessToken();
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

export const chatService = {
  async listConversations(): Promise<Conversation[]> {
    const { data } = await realtimeApi.get<Conversation[]>('/chat/conversations');
    return data;
  },

  /**
   * Lot L5b (E1, RG-COM-06) : envoi par le serveur, enregistre des l'envoi, avec ou sans
   * connexion temps reel. Un refus rend le message du serveur (cabinet, destinataire...).
   */
  async envoyer(to: string, body: string): Promise<ChatMessage> {
    try {
      const { data } = await realtimeApi.post<ChatMessage>('/chat/messages', { to, body });
      return data;
    } catch (e) {
      const data = axios.isAxiosError(e) ? (e.response?.data as { error?: string } | undefined) : undefined;
      throw new Error(data?.error ?? 'Le message n’a pas pu être envoyé. Vérifiez votre connexion et réessayez.');
    }
  },

  async listMessages(conversationId: string): Promise<ChatMessage[]> {
    const { data } = await realtimeApi.get<ChatMessage[]>(
      `/chat/conversations/${conversationId}/messages`,
    );
    return data;
  },
};

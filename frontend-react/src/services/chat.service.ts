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
  baseURL: `${REALTIME_URL}/api/v1`,
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

  async listMessages(conversationId: string): Promise<ChatMessage[]> {
    const { data } = await realtimeApi.get<ChatMessage[]>(
      `/chat/conversations/${conversationId}/messages`,
    );
    return data;
  },
};

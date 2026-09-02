import { api } from '../lib/api';
import type { ChatbotAnswer, ChatbotSourceDoc } from '../types/chat';

export const chatbotService = {
  async ask(question: string): Promise<ChatbotAnswer> {
    const { data } = await api.post<ChatbotAnswer>('/chatbot/ask', { question });
    return data;
  },

  /** Ingere un document source (pdf/docx/txt) dans le corpus RAG du workspace. */
  async addSource(file: File): Promise<{ docId: string; source: string; chunks: number }> {
    const form = new FormData();
    form.append('file', file);
    const { data } = await api.post('/chatbot/sources', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  /** Ingere un texte colle (sans fichier). */
  async addSourceText(
    title: string,
    text: string,
  ): Promise<{ docId: string; source: string; chunks: number }> {
    const form = new FormData();
    form.append('title', title);
    form.append('text', text);
    const { data } = await api.post('/chatbot/sources', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  /** Liste les sources du workspace courant. */
  async listSources(): Promise<ChatbotSourceDoc[]> {
    const { data } = await api.get<ChatbotSourceDoc[]>('/chatbot/sources');
    return data;
  },

  /** Supprime une source (tous ses chunks). */
  async deleteSource(docId: string): Promise<void> {
    await api.delete(`/chatbot/sources/${docId}`);
  },
};

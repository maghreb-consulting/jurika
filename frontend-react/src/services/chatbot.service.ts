import { api } from '../lib/api';
import type {
  ChatbotAnswer,
  ChatbotConversationDetail,
  ChatbotConversationResume,
  ChatbotQuestionResultat,
  ChatbotSourceDoc,
} from '../types/chat';

export const chatbotService = {
  async ask(question: string): Promise<ChatbotAnswer> {
    const { data } = await api.post<ChatbotAnswer>('/chatbot/ask', { question });
    return data;
  },

  // Lot L5b E2 (RG-COM-12) : conversations de l'utilisateur connecte, conservees par le serveur.

  /** Mes conversations (actives ou archivees), de la plus recemment active a la plus ancienne. */
  async listerConversations(archivees = false): Promise<ChatbotConversationResume[]> {
    const { data } = await api.get<ChatbotConversationResume[]>('/chatbot/conversations', {
      params: { archivees },
    });
    return data;
  },

  /** Ouvre une conversation par sa premiere question (le titre en est tire). */
  async creerConversation(question: string): Promise<ChatbotQuestionResultat> {
    const { data } = await api.post<ChatbotQuestionResultat>('/chatbot/conversations', { question });
    return data;
  },

  async lireConversation(id: string): Promise<ChatbotConversationDetail> {
    const { data } = await api.get<ChatbotConversationDetail>(`/chatbot/conversations/${id}`);
    return data;
  },

  async poserQuestion(id: string, question: string): Promise<ChatbotQuestionResultat> {
    const { data } = await api.post<ChatbotQuestionResultat>(`/chatbot/conversations/${id}/questions`, {
      question,
    });
    return data;
  },

  async renommerConversation(id: string, titre: string): Promise<ChatbotConversationResume> {
    const { data } = await api.patch<ChatbotConversationResume>(`/chatbot/conversations/${id}`, { titre });
    return data;
  },

  async archiverConversation(id: string): Promise<ChatbotConversationResume> {
    const { data } = await api.post<ChatbotConversationResume>(`/chatbot/conversations/${id}/archiver`);
    return data;
  },

  async desarchiverConversation(id: string): Promise<ChatbotConversationResume> {
    const { data } = await api.post<ChatbotConversationResume>(`/chatbot/conversations/${id}/desarchiver`);
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

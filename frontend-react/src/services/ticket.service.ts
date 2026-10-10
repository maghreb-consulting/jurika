import { api } from '../lib/api';
import type {
  CreateTicketPayload,
  Debours,
  DeboursPayload,
  PageResponse,
  Ticket,
  TicketNote,
  TicketPriorite,
  TicketStatut,
  TicketType,
  TransitionPayload,
  UpdateTicketPayload,
} from '../types/ticket';

export interface TicketSearchParams {
  statuts?: TicketStatut[];
  types?: TicketType[];
  priorites?: TicketPriorite[];
  assigneId?: string;
  dossierId?: string;
  q?: string;
  sortBy?: string;
  desc?: boolean;
  limit?: number;
  offset?: number;
}

export const ticketService = {
  /** Lot L1 (RG-TKT-07) : note interne du ticket (vide si jamais enregistree). */
  async getNote(ticketId: string): Promise<TicketNote> {
    const { data } = await api.get<TicketNote>(`/tickets/${ticketId}/note`);
    return data;
  },

  /** Lot L1 (RG-TKT-07) : enregistrement de la note, vide accepte. */
  async saveNote(ticketId: string, contenu: string): Promise<TicketNote> {
    const { data } = await api.put<TicketNote>(`/tickets/${ticketId}/note`, { contenu });
    return data;
  },

  async list(params: TicketSearchParams = {}): Promise<PageResponse<Ticket>> {
    const { data } = await api.get<PageResponse<Ticket>>('/tickets', { params });
    return data;
  },

  async get(id: string): Promise<Ticket> {
    const { data } = await api.get<Ticket>(`/tickets/${id}`);
    return data;
  },

  async create(payload: CreateTicketPayload): Promise<Ticket> {
    const { data } = await api.post<Ticket>('/tickets', payload);
    return data;
  },

  async update(id: string, payload: UpdateTicketPayload): Promise<Ticket> {
    const { data } = await api.patch<Ticket>(`/tickets/${id}`, payload);
    return data;
  },

  async transition(id: string, payload: TransitionPayload): Promise<Ticket> {
    const { data } = await api.post<Ticket>(`/tickets/${id}/transition`, payload);
    return data;
  },

  async listDebours(ticketId: string): Promise<{ items: Debours[]; total: number }> {
    const { data } = await api.get<{ items: Debours[]; total: number }>(
      `/tickets/${ticketId}/debours`,
    );
    return data;
  },

  async createDebours(ticketId: string, payload: DeboursPayload): Promise<Debours> {
    const { data } = await api.post<Debours>(`/tickets/${ticketId}/debours`, payload);
    return data;
  },

  async updateDebours(ticketId: string, deboursId: string, payload: DeboursPayload): Promise<Debours> {
    const { data } = await api.patch<Debours>(`/tickets/${ticketId}/debours/${deboursId}`, payload);
    return data;
  },

  async deleteDebours(ticketId: string, deboursId: string): Promise<void> {
    await api.delete(`/tickets/${ticketId}/debours/${deboursId}`);
  },

  async downloadDeboursPdf(ticketId: string, reference: string): Promise<void> {
    const response = await api.get(`/tickets/${ticketId}/debours/export-pdf`, {
      responseType: 'blob',
    });
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Etat_debours_${reference}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },
};

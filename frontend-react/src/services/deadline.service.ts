import { api } from "../lib/api";
import type {
  CreateDeadlinePayload,
  Deadline,
  DeadlineListResponse,
  DeadlineStatut,
} from "../types/deadline";

export interface DeadlineQuery {
  statut?: DeadlineStatut;
  from?: string;
  to?: string;
  limit?: number;
}

export const deadlineService = {
  async list(params: DeadlineQuery = {}): Promise<DeadlineListResponse> {
    const { data } = await api.get<DeadlineListResponse>("/deadlines", { params });
    return data;
  },

  async overdue(limit = 50): Promise<DeadlineListResponse> {
    const { data } = await api.get<DeadlineListResponse>("/deadlines/overdue", { params: { limit } });
    return data;
  },

  async countOpen(): Promise<number> {
    const { data } = await api.get<{ count: number }>("/deadlines/count-open");
    return data.count;
  },

  async create(payload: CreateDeadlinePayload): Promise<Deadline> {
    const { data } = await api.post<Deadline>("/deadlines", payload);
    return data;
  },

  async complete(id: string): Promise<Deadline> {
    const { data } = await api.patch<Deadline>(`/deadlines/${id}/complete`);
    return data;
  },

  async dismiss(id: string): Promise<Deadline> {
    const { data } = await api.patch<Deadline>(`/deadlines/${id}/dismiss`);
    return data;
  },
};

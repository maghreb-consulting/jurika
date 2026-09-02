import { api } from '../lib/api';
import type { AgentBriefing } from '../types/agent';

/**
 * Lot IA-1 — API de l'agent copilote de l'employe.
 * Toutes les routes sont scopees a l'utilisateur courant via son JWT.
 */
export const agentService = {
  /** GET /api/v1/agent/briefing/me — briefing du jour (genere si absent/obsolete). */
  async getMyBriefing(): Promise<AgentBriefing> {
    const { data } = await api.get<AgentBriefing>('/agent/briefing/me');
    return data;
  },

  /** POST /api/v1/agent/briefing/refresh — regenere le briefing. */
  async refresh(): Promise<AgentBriefing> {
    const { data } = await api.post<AgentBriefing>('/agent/briefing/refresh', null);
    return data;
  },

  /** POST /api/v1/agent/briefing/{id}/seen — marque le briefing comme vu. */
  async markSeen(id: string): Promise<AgentBriefing> {
    const { data } = await api.post<AgentBriefing>(`/agent/briefing/${id}/seen`, null);
    return data;
  },
};

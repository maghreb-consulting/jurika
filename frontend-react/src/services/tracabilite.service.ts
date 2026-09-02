import { api } from '../lib/api';
import type { TracabiliteFilters, TracabilitePage } from '../types/tracabilite';

/**
 * Traçabilité (E2) — appelle dashboard-service via la gateway. Le workspace
 * n'est jamais envoye : il est derive du JWT cote backend (scoping RLS).
 */
export const tracabiliteService = {
  /** Journal complet du workspace (SUPERVISEUR). */
  async search(filters: TracabiliteFilters = {}): Promise<TracabilitePage> {
    const params: Record<string, string | number> = {};
    if (filters.entityType) params.entityType = filters.entityType;
    if (filters.entityId) params.entityId = filters.entityId;
    if (filters.userId) params.userId = filters.userId;
    if (filters.action) params.action = filters.action;
    if (filters.from) params.from = filters.from;
    if (filters.to) params.to = filters.to;
    if (filters.actorStatus) params.actorStatus = filters.actorStatus;
    params.limit = filters.limit ?? 50;
    params.offset = filters.offset ?? 0;
    const { data } = await api.get<TracabilitePage>('/tracabilite', { params });
    return data;
  },

  /** Historique d'une entite precise (dossier / ticket) — EMPLOYE + SUPERVISEUR. */
  async entityHistory(entityType: string, entityId: string, limit = 50): Promise<TracabilitePage> {
    const { data } = await api.get<TracabilitePage>('/tracabilite/entite', {
      params: { entityType, entityId, limit },
    });
    return data;
  },
};

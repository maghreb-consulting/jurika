import { api } from '../lib/api';

export interface AuditEntry {
  id: number;
  workspaceId: string | null;
  userId: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  sourceService: string | null;
  correlationId: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface AuditPage {
  items: AuditEntry[];
  total: number;
  offset: number;
  limit: number;
  filters: Record<string, unknown>;
}

export interface AuditFilters {
  workspaceId?: string;
  userId?: string;
  /** Type d'utilisateur (CLIENT / SUPERVISEUR / EMPLOYE / SUPER_ADMIN). */
  role?: string;
  action?: string;
  sourceService?: string;
  fromDate?: string;
  toDate?: string;
  offset?: number;
  limit?: number;
}

/** Valeurs distinctes du journal pour alimenter les listes deroulantes. */
export interface AuditFacets {
  actions: string[];
  sourceServices: string[];
}

export const auditService = {
  async search(filters: AuditFilters): Promise<AuditPage> {
    const params: Record<string, string | number> = {};
    if (filters.workspaceId) params.workspaceId = filters.workspaceId;
    if (filters.userId) params.userId = filters.userId;
    if (filters.role) params.role = filters.role;
    if (filters.action) params.action = filters.action;
    if (filters.sourceService) params.sourceService = filters.sourceService;
    if (filters.fromDate) params.fromDate = filters.fromDate;
    if (filters.toDate) params.toDate = filters.toDate;
    params.offset = filters.offset ?? 0;
    params.limit = filters.limit ?? 50;
    const { data } = await api.get<AuditPage>('/admin/audit', { params });
    return data;
  },

  /** Actions + services source distincts (pour les filtres liste deroulante). */
  async facets(): Promise<AuditFacets> {
    const { data } = await api.get<AuditFacets>('/admin/audit/facets');
    return data;
  },

  exportCsv(items: AuditEntry[]): string {
    const header = ['id', 'createdAt', 'sourceService', 'action', 'workspaceId', 'userId',
      'entityType', 'entityId', 'correlationId'];
    const escape = (v: unknown) => {
      if (v === null || v === undefined) return '';
      const s = String(v);
      return s.includes(',') || s.includes('"') || s.includes('\n')
        ? '"' + s.replace(/"/g, '""') + '"'
        : s;
    };
    const rows = items.map((e) => [
      e.id, e.createdAt, e.sourceService, e.action, e.workspaceId, e.userId,
      e.entityType, e.entityId, e.correlationId,
    ].map(escape).join(','));
    return [header.join(','), ...rows].join('\n');
  },
};

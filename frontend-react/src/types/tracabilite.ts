// E2 — Traçabilité (lecture de audit_log scopee au workspace).

export interface TracabiliteEntry {
  id: number;
  action: string;
  actionLabel: string;
  userId: string | null;
  entityType: string | null;
  entityId: string | null;
  metadata: string | null;
  sourceService: string | null;
  createdAt: string;
}

export interface TracabilitePage {
  items: TracabiliteEntry[];
  total: number;
  limit: number;
  offset: number;
}

/**
 * Traçabilité (2026-07-15) — statut de l'acteur pour le filtre dedie.
 * `ACTIVE` = comptes encore actifs ; `RETIRED` = comptes desactives OU supprimes ;
 * absent = tous.
 */
export type ActorStatusFilter = 'ACTIVE' | 'RETIRED';

export interface TracabiliteFilters {
  entityType?: string;
  entityId?: string;
  userId?: string;
  action?: string;
  from?: string;
  to?: string;
  /** Traçabilité (2026-07-15) — filtre par statut de l'acteur (cote backend). */
  actorStatus?: ActorStatusFilter;
  limit?: number;
  offset?: number;
}

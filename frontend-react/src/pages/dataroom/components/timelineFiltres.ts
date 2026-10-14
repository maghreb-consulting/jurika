/**
 * Filtres de l'historique des operations (TimelineFiltersDrawer) et leur conversion en
 * parametres d'API. Separes du composant (lot L6, lint react-refresh/only-export-components).
 */
export interface TimelineFilters {
  types: string[];
  from: string; // YYYY-MM-DD
  to: string;
}

export const DEFAULT_TIMELINE_FILTERS: TimelineFilters = {
  types: [],
  from: '',
  to: '',
};

/** Ecran -> parametres de l'API. */
export function timelineFiltersToApiParams(f: TimelineFilters): {
  types?: string[];
  from?: string;
  to?: string;
} {
  return {
    types: f.types.length > 0 ? f.types : undefined,
    from: f.from ? `${f.from}T00:00:00Z` : undefined,
    to: f.to ? `${f.to}T23:59:59Z` : undefined,
  };
}

export function countActiveTimelineFilters(f: TimelineFilters): number {
  let n = 0;
  if (f.types.length > 0) n += 1;
  if (f.from || f.to) n += 1;
  return n;
}

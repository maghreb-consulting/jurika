import type { DocumentType, VersionScope } from '../../../types/dataroom';

/**
 * Filtres de la recherche du dossier juridique (barre JuridiqueSearchBar) et leur
 * conversion en parametres d'API. Separes du composant pour que le rechargement a chaud
 * ne serve que des composants (lot L6, lint react-refresh/only-export-components).
 */
export interface SearchFilters {
  q: string;
  types: (DocumentType | string)[];
  from: string; // YYYY-MM-DD ou ''
  to: string;
  versionScope: VersionScope;
}

export const DEFAULT_FILTERS: SearchFilters = {
  q: '',
  types: [],
  from: '',
  to: '',
  versionScope: 'CURRENT',
};

/**
 * Convertit SearchFilters (ecran) en parametres de l'API.
 * Conversion date YYYY-MM-DD vers Instant ISO 8601 (UTC, debut/fin du jour).
 */
export function filtersToApiParams(f: SearchFilters): {
  q?: string;
  types?: string[];
  from?: string;
  to?: string;
  versionScope?: VersionScope;
} {
  return {
    q: f.q.trim() || undefined,
    types: f.types.length > 0 ? f.types : undefined,
    from: f.from ? `${f.from}T00:00:00Z` : undefined,
    to: f.to ? `${f.to}T23:59:59Z` : undefined,
    versionScope: f.versionScope,
  };
}

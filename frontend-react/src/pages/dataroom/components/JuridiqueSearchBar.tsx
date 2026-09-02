import { useEffect, useMemo, useRef, useState } from 'react';
import { Filter, Search, X } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { TextField } from '../../../components/ui/TextField';
import {
  DOCUMENT_TYPE_LABELS,
  VERSION_SCOPE_LABELS,
  type DocumentType,
  type VersionScope,
} from '../../../types/dataroom';

/**
 * Sprint 7 / TASK 1.5 -- Barre de recherche + drawer filtres avances
 * pour la Data Room Juridique.
 *
 * UX :
 *   - Debounce 300ms sur la query texte (evite de spammer FTS PostgreSQL)
 *   - Drawer pour filtres : multi-select types + date range + version scope
 *   - Badge compteur de filtres actifs sur le bouton "Filtres"
 *   - Bouton "X" pour reset tous les filtres (visible si filtres actifs)
 *
 * Sync URL : volontairement OUT pour V1 (eviter scope creep). A ajouter
 * via useSearchParams si demande utilisateur (TASK 1 plan ligne 115).
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

interface Props {
  filters: SearchFilters;
  onChange: (next: SearchFilters) => void;
  totalResults?: number;
  loading?: boolean;
}

const DOCUMENT_TYPE_OPTIONS = Object.entries(DOCUMENT_TYPE_LABELS) as [
  DocumentType,
  string,
][];

const VERSION_SCOPE_OPTIONS = Object.entries(VERSION_SCOPE_LABELS) as [
  VersionScope,
  string,
][];

export function JuridiqueSearchBar({
  filters,
  onChange,
  totalResults,
  loading,
}: Props) {
  // Debounce query
  const [localQuery, setLocalQuery] = useState(filters.q);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // Sync entrant si parent reset filtres
    setLocalQuery(filters.q);
  }, [filters.q]);

  function handleQueryChange(v: string) {
    setLocalQuery(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onChange({ ...filters, q: v });
    }, 300);
  }

  // Drawer filtres
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [draft, setDraft] = useState<SearchFilters>(filters);

  function openDrawer() {
    setDraft(filters);
    setDrawerOpen(true);
  }

  function applyDraft() {
    onChange(draft);
    setDrawerOpen(false);
  }

  function resetAll() {
    onChange({ ...DEFAULT_FILTERS });
    setLocalQuery('');
  }

  function toggleType(t: DocumentType) {
    setDraft((d) => {
      const has = d.types.includes(t);
      return {
        ...d,
        types: has ? d.types.filter((x) => x !== t) : [...d.types, t],
      };
    });
  }

  const activeFiltersCount = useMemo(() => {
    let n = 0;
    if (filters.types.length > 0) n += 1;
    if (filters.from || filters.to) n += 1;
    if (filters.versionScope !== 'CURRENT') n += 1;
    return n;
  }, [filters]);

  const hasAnyActive =
    !!filters.q || activeFiltersCount > 0 || localQuery !== '';

  return (
    <>
      <div className="flex flex-col gap-2 md:flex-row md:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle" />
          <TextField
            value={localQuery}
            onChange={(e) => handleQueryChange(e.target.value)}
            placeholder="Rechercher dans les documents juridiques (titre, nom de fichier)..."
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={openDrawer}
            className="relative"
          >
            <Filter className="mr-1 h-4 w-4" />
            Filtres
            {activeFiltersCount > 0 && (
              <span className="ml-2 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-accent px-1.5 text-[10px] font-bold text-bg-raised">
                {activeFiltersCount}
              </span>
            )}
          </Button>
          {hasAnyActive && (
            <Button
              variant="ghost"
              size="sm"
              onClick={resetAll}
              title="Reinitialiser tous les filtres"
            >
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>

      {(filters.q || activeFiltersCount > 0) && (
        <p className="text-xs text-fg-subtle">
          {loading
            ? 'Recherche en cours...'
            : `${totalResults ?? 0} resultat${(totalResults ?? 0) > 1 ? 's' : ''}`}
          {filters.q && <> pour <strong>"{filters.q}"</strong></>}
        </p>
      )}

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title="Filtres de recherche"
        subtitle="Affinez la liste des documents juridiques"
        width="md"
        footer={
          <div className="flex justify-end gap-2 border-t border-border px-6 py-3">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setDraft({ ...DEFAULT_FILTERS, q: filters.q })}
            >
              Reinitialiser
            </Button>
            <Button size="sm" onClick={applyDraft}>
              Appliquer
            </Button>
          </div>
        }
      >
        <div className="space-y-6">
          <section>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
              Type de document
            </h4>
            <div className="flex flex-wrap gap-2">
              {DOCUMENT_TYPE_OPTIONS.map(([value, label]) => {
                const active = draft.types.includes(value);
                return (
                  <button
                    key={value}
                    type="button"
                    onClick={() => toggleType(value)}
                    className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
                      active
                        ? 'border-accent/40 bg-accent/10 text-accent'
                        : 'border-border bg-bg-raised text-fg-muted hover:border-border-hi'
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </section>

          <section>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
              Periode (date d'upload)
            </h4>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">Du</label>
                <TextField
                  type="date"
                  value={draft.from}
                  onChange={(e) => setDraft({ ...draft, from: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">Au</label>
                <TextField
                  type="date"
                  value={draft.to}
                  onChange={(e) => setDraft({ ...draft, to: e.target.value })}
                />
              </div>
            </div>
          </section>

          <section>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
              Versions
            </h4>
            <div className="space-y-1.5">
              {VERSION_SCOPE_OPTIONS.map(([value, label]) => (
                <label
                  key={value}
                  className="flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm hover:border-border-hi"
                >
                  <input
                    type="radio"
                    name="versionScope"
                    value={value}
                    checked={draft.versionScope === value}
                    onChange={() =>
                      setDraft({ ...draft, versionScope: value as VersionScope })
                    }
                    className="text-accent"
                  />
                  <span className="text-fg-muted">{label}</span>
                </label>
              ))}
            </div>
          </section>
        </div>
      </Drawer>
    </>
  );
}

/**
 * Helper : convertit SearchFilters (UI) -> SearchJuridiqueParams (API).
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

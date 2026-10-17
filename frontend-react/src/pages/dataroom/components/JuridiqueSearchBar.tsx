import { useMemo, useRef, useState } from 'react';
import { Filter, Search, X } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { Badge } from '../../../components/ui/Badge';
import { GroupeRadio } from '../../../components/ui/Choix';
import { PastilleFiltre } from './PastilleFiltre';
import { TextField } from '../../../components/ui/TextField';
import {
  DOCUMENT_TYPE_LABELS,
  VERSION_SCOPE_LABELS,
  type DocumentType,
  type VersionScope,
} from '../../../types/dataroom';
import { DEFAULT_FILTERS, type SearchFilters } from './juridiqueFiltres';

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

  // Synchronisation entrante (le parent remet les filtres a zero), ajustee pendant le rendu.
  const [qVue, setQVue] = useState(filters.q);
  if (qVue !== filters.q) {
    setQVue(filters.q);
    setLocalQuery(filters.q);
  }

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
      <div className="flex flex-col gap-2 @md:flex-row @md:items-center">
        <div className="relative flex-1">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 z-10 h-4 w-4 -translate-y-1/2 text-fg-subtle"
          />
          <TextField
            type="search"
            value={localQuery}
            onChange={(e) => handleQueryChange(e.target.value)}
            placeholder="Rechercher dans les documents juridiques (titre, nom de fichier)…"
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" size="sm" icon={<Filter />} onClick={openDrawer}>
            Filtres
            {activeFiltersCount > 0 && <Badge variant="default">{activeFiltersCount}</Badge>}
          </Button>
          {hasAnyActive && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              iconOnly
              icon={<X />}
              onClick={resetAll}
              title="Effacer la recherche et les filtres"
              aria-label="Effacer la recherche et les filtres"
            />
          )}
        </div>
      </div>

      {(filters.q || activeFiltersCount > 0) && (
        <p className="text-xs text-fg-muted" aria-live="polite">
          {loading
            ? 'Recherche en cours…'
            : `${totalResults ?? 0} résultat${(totalResults ?? 0) > 1 ? 's' : ''}`}
          {filters.q && (
            <>
              {' '}pour <strong className="text-fg">« {filters.q} »</strong>
            </>
          )}
        </p>
      )}

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title="Filtres de recherche"
        subtitle="Affinez la liste des documents juridiques"
        width="md"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setDraft({ ...DEFAULT_FILTERS, q: filters.q })}
            >
              Réinitialiser
            </Button>
            <Button type="button" size="sm" onClick={applyDraft}>
              Appliquer
            </Button>
          </div>
        }
      >
        <div className="space-y-6">
          <p className="text-sm text-fg-muted">
            Choisissez un ou plusieurs types, une période ou les versions à inclure, puis cliquez sur
            « Appliquer ». La recherche par mot reste active.
          </p>
          <section aria-labelledby="filtre-type-document">
            <h3 id="filtre-type-document" className="mb-2 text-sm font-medium text-fg">
              Type de document
            </h3>
            <div className="flex flex-wrap gap-2">
              {DOCUMENT_TYPE_OPTIONS.map(([value, label]) => (
                <PastilleFiltre key={value} actif={draft.types.includes(value)} onClick={() => toggleType(value)}>
                  {label}
                </PastilleFiltre>
              ))}
            </div>
          </section>

          <section aria-labelledby="filtre-periode">
            <h3 id="filtre-periode" className="mb-2 text-sm font-medium text-fg">
              Période (date d’ajout du document)
            </h3>
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label="Du"
                type="date"
                value={draft.from}
                onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              />
              <TextField
                label="Au"
                type="date"
                value={draft.to}
                onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              />
            </div>
          </section>

          <GroupeRadio
            legende="Versions"
            name="versionScope"
            value={draft.versionScope}
            onChange={(v) => setDraft({ ...draft, versionScope: v as VersionScope })}
            options={VERSION_SCOPE_OPTIONS.map(([value, label]) => ({ value, label }))}
          />
        </div>
      </Drawer>
    </>
  );
}

import { Search, X } from 'lucide-react';
import {
  PRIORITE_LABELS,
  STATUT_LABELS,
  TICKET_TYPE_LABELS,
} from '../../types/ticket';
import type {
  TicketPriorite,
  TicketStatut,
  TicketType,
} from '../../types/ticket';

interface Props {
  query: string;
  onQueryChange: (q: string) => void;
  filterType: TicketType | '';
  onFilterTypeChange: (t: TicketType | '') => void;
  filterPriorite: TicketPriorite | '';
  onFilterPrioriteChange: (p: TicketPriorite | '') => void;
  filterStatut: TicketStatut | '';
  onFilterStatutChange: (s: TicketStatut | '') => void;
}

/**
 * Barre de filtres tickets : recherche + type + priorite + statut.
 * Affiche un bouton "Reinitialiser" si au moins un filtre est actif.
 */
export function TicketsFilters({
  query,
  onQueryChange,
  filterType,
  onFilterTypeChange,
  filterPriorite,
  onFilterPrioriteChange,
  filterStatut,
  onFilterStatutChange,
}: Props) {
  const activeCount =
    (query ? 1 : 0) +
    (filterType ? 1 : 0) +
    (filterPriorite ? 1 : 0) +
    (filterStatut ? 1 : 0);

  function reset() {
    onQueryChange('');
    onFilterTypeChange('');
    onFilterPrioriteChange('');
    onFilterStatutChange('');
  }

  return (
    <div className="bg-bg-raised rounded-xl p-3 md:p-4 border border-border shadow-sm">
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-subtle" />
          <input
            type="text"
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder="Rechercher reference, titre, description..."
            className="h-9 pl-9 pr-4 w-full bg-bg-overlay border border-border rounded-lg text-sm focus:border-accent focus:outline-none"
          />
        </div>

        <select
          value={filterType}
          onChange={(e) => onFilterTypeChange(e.target.value as TicketType | '')}
          className="h-9 px-3 bg-bg-overlay border border-border rounded-lg text-xs hover:border-accent transition"
          aria-label="Filtrer par type"
        >
          <option value="">Tous les types</option>
          {(Object.entries(TICKET_TYPE_LABELS) as [TicketType, string][]).map(
            ([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ),
          )}
        </select>

        <select
          value={filterPriorite}
          onChange={(e) =>
            onFilterPrioriteChange(e.target.value as TicketPriorite | '')
          }
          className="h-9 px-3 bg-bg-overlay border border-border rounded-lg text-xs hover:border-accent transition"
          aria-label="Filtrer par priorite"
        >
          <option value="">Toutes priorites</option>
          {(Object.entries(PRIORITE_LABELS) as [TicketPriorite, string][]).map(
            ([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ),
          )}
        </select>

        <select
          value={filterStatut}
          onChange={(e) =>
            onFilterStatutChange(e.target.value as TicketStatut | '')
          }
          className="h-9 px-3 bg-bg-overlay border border-border rounded-lg text-xs hover:border-accent transition"
          aria-label="Filtrer par statut"
        >
          <option value="">Tous statuts</option>
          {(Object.entries(STATUT_LABELS) as [TicketStatut, string][]).map(
            ([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ),
          )}
        </select>

        {activeCount > 0 && (
          <button
            type="button"
            onClick={reset}
            className="h-9 px-3 text-xs text-danger hover:bg-danger/10 rounded-lg transition flex items-center gap-1"
          >
            <X className="w-3 h-3" />
            Reinitialiser ({activeCount})
          </button>
        )}
      </div>
    </div>
  );
}

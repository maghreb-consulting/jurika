import { Search, X } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
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

const options = <K extends string>(libelles: Record<K, string>, tous: string) => [
  { value: '', label: tous },
  ...(Object.entries(libelles) as [K, string][]).map(([value, label]) => ({ value, label })),
];

const OPTIONS_TYPE = options(TICKET_TYPE_LABELS, 'Tous les types');
const OPTIONS_PRIORITE = options(PRIORITE_LABELS, 'Toutes les priorités');
const OPTIONS_STATUT = options(STATUT_LABELS, 'Tous les statuts');

/**
 * Barre de filtres des tickets : recherche, type, priorite, statut (lot L6b : champs de
 * la bibliotheque). "Reinitialiser" n'apparait que si un filtre est actif, avec leur nombre.
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
    <div role="search" aria-label="Filtrer les tickets" className="@container mb-5 rounded-xl border border-border bg-bg-raised p-3 shadow-card">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-[14rem] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 z-10 h-4 w-4 -translate-y-1/2 text-fg-subtle" aria-hidden="true" />
          <TextField
            type="search"
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder="Rechercher par référence, titre ou description…"
            aria-label="Rechercher un ticket"
            className="pl-9"
          />
        </div>
        <Select
          aria-label="Filtrer par type"
          value={filterType}
          onChange={(e) => onFilterTypeChange(e.target.value as TicketType | '')}
          options={OPTIONS_TYPE}
          containerClassName="w-full @md:w-auto"
        />
        <Select
          aria-label="Filtrer par priorité"
          value={filterPriorite}
          onChange={(e) => onFilterPrioriteChange(e.target.value as TicketPriorite | '')}
          options={OPTIONS_PRIORITE}
          containerClassName="w-full @md:w-auto"
        />
        <Select
          aria-label="Filtrer par statut"
          value={filterStatut}
          onChange={(e) => onFilterStatutChange(e.target.value as TicketStatut | '')}
          options={OPTIONS_STATUT}
          containerClassName="w-full @md:w-auto"
        />
        {activeCount > 0 && (
          <Button type="button" variant="ghost" size="md" icon={<X className="h-4 w-4" aria-hidden="true" />} onClick={reset}>
            Réinitialiser ({activeCount})
          </Button>
        )}
      </div>
    </div>
  );
}

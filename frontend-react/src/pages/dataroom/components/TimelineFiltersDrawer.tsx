import { useEffect, useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { TextField } from '../../../components/ui/TextField';

/**
 * Sprint 7 / TASK 2.3 -- Drawer filtres pour la timeline historique
 * (types de tickets + periode de cloture).
 *
 * Types ticket pris en charge : valeurs metier connues issues de
 * Guide_Workflows_V2.md (CREATION, MODIFICATION, DISSOLUTION, LIQUIDATION,
 * AGO, SUCCURSALE, FERMETURE_SUCCURSALE). Liste extensible.
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

const TICKET_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'CREATION', label: 'Creation' },
  { value: 'MODIFICATION', label: 'Modification' },
  { value: 'DISSOLUTION', label: 'Dissolution' },
  { value: 'LIQUIDATION', label: 'Liquidation' },
  { value: 'AGO', label: 'PV AGO' },
  { value: 'SUCCURSALE', label: 'Succursale' },
  { value: 'FERMETURE_SUCCURSALE', label: 'Fermeture succursale' },
  { value: 'IMPORT', label: 'Import dossier' },
];

interface Props {
  open: boolean;
  filters: TimelineFilters;
  onClose: () => void;
  onApply: (next: TimelineFilters) => void;
}

export function TimelineFiltersDrawer({ open, filters, onClose, onApply }: Props) {
  const [draft, setDraft] = useState<TimelineFilters>(filters);

  useEffect(() => {
    if (open) setDraft(filters);
  }, [open, filters]);

  function toggleType(t: string) {
    setDraft((d) => {
      const has = d.types.includes(t);
      return {
        ...d,
        types: has ? d.types.filter((x) => x !== t) : [...d.types, t],
      };
    });
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Filtres de la timeline"
      subtitle="Affinez l'historique des operations cloturees"
      width="md"
      footer={
        <div className="flex justify-between gap-2 border-t border-border px-6 py-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setDraft(DEFAULT_TIMELINE_FILTERS)}
          >
            Reinitialiser
          </Button>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={onClose}>
              Annuler
            </Button>
            <Button
              size="sm"
              onClick={() => {
                onApply(draft);
                onClose();
              }}
            >
              Appliquer
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-6">
        <section>
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
            Type d'operation
          </h4>
          <div className="flex flex-wrap gap-2">
            {TICKET_TYPE_OPTIONS.map(({ value, label }) => {
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
            Periode de cloture
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
      </div>
    </Drawer>
  );
}

/** Helper UI -> API params. */
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

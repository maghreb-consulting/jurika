import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { TextField } from '../../../components/ui/TextField';
import { DEFAULT_TIMELINE_FILTERS, type TimelineFilters } from './timelineFiltres';

/**
 * Sprint 7 / TASK 2.3 -- Drawer filtres pour la timeline historique
 * (types de tickets + periode de cloture).
 *
 * Types ticket pris en charge : valeurs metier connues issues de
 * Guide_Workflows_V2.md (CREATION, MODIFICATION, DISSOLUTION, LIQUIDATION,
 * AGO, SUCCURSALE, FERMETURE_SUCCURSALE). Liste extensible.
 */

const TICKET_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'CREATION', label: 'Création' },
  { value: 'MODIFICATION', label: 'Modification' },
  { value: 'DISSOLUTION', label: 'Dissolution' },
  { value: 'LIQUIDATION', label: 'Liquidation' },
  { value: 'AGO', label: 'PV AGO' },
  { value: 'SUCCURSALE', label: 'Succursale' },
  { value: 'FERMETURE_SUCCURSALE', label: 'Fermeture de succursale' },
  { value: 'IMPORT', label: 'Import de dossier' },
];

interface Props {
  open: boolean;
  filters: TimelineFilters;
  onClose: () => void;
  onApply: (next: TimelineFilters) => void;
}

export function TimelineFiltersDrawer({ open, filters, onClose, onApply }: Props) {
  const [draft, setDraft] = useState<TimelineFilters>(filters);

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertureVue, setOuvertureVue] = useState<{ open: boolean; filters: TimelineFilters } | null>(null);
  if (!ouvertureVue || ouvertureVue.open !== open || ouvertureVue.filters !== filters) {
    setOuvertureVue({ open, filters });
    if (open) setDraft(filters);
  }

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
      title="Filtres de l’historique"
      subtitle="Affinez l’historique des opérations clôturées"
      width="md"
      footer={
        <div className="flex justify-between gap-2 border-t border-border px-6 py-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setDraft(DEFAULT_TIMELINE_FILTERS)}
          >
            Réinitialiser
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
            Type d’opération
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
            Période de clôture
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

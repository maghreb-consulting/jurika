import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { TextField } from '../../../components/ui/TextField';
import { PastilleFiltre } from './PastilleFiltre';
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
        <div className="flex justify-between gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={() => setDraft(DEFAULT_TIMELINE_FILTERS)}>
            Réinitialiser
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={onClose}>
              Annuler
            </Button>
            <Button
              type="button"
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
        <p className="text-sm text-fg-muted">
          Choisissez les types d’opération et la période de clôture, puis cliquez sur « Appliquer ».
          Sans choix, tout l’historique s’affiche.
        </p>
        <section aria-labelledby="filtre-type-operation">
          <h3 id="filtre-type-operation" className="mb-2 text-sm font-medium text-fg">
            Type d’opération
          </h3>
          <div className="flex flex-wrap gap-2">
            {TICKET_TYPE_OPTIONS.map(({ value, label }) => (
              <PastilleFiltre key={value} actif={draft.types.includes(value)} onClick={() => toggleType(value)}>
                {label}
              </PastilleFiltre>
            ))}
          </div>
        </section>

        <section aria-labelledby="filtre-periode-cloture">
          <h3 id="filtre-periode-cloture" className="mb-2 text-sm font-medium text-fg">
            Période de clôture
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
      </div>
    </Drawer>
  );
}

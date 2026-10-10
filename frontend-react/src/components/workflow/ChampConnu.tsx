import type { VariableDuMagasin } from '../../services/workflow.service';
import { libelleProvenance, ouCorriger } from './provenanceLibelles';

/** Lot L3 (RG-VAR-03) : une donnee deja connue, affichee en lecture avec sa provenance. */
export function ChampConnu({ label, variable }: { label: string; variable: VariableDuMagasin }) {
  return (
    <div data-testid={`champ-connu-${variable.variable}`}>
      <p className="mb-1 block text-xs font-medium text-fg">{label}</p>
      <p className="text-sm text-fg">{variable.valeur}</p>
      <p className="text-[11px] text-fg-subtle">
        {libelleProvenance(variable)}. {ouCorriger(variable)}
      </p>
    </div>
  );
}

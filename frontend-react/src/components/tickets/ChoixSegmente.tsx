import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Lot L6b (E7) : choix exclusif entre quelques affichages (par statut / tableau, mois /
 * semaine...). Boutons a bascule (aria-pressed) dans un groupe nomme ; l'etat actif porte la
 * teinte or douce (etat actif) et l'etat enfonce est annonce, la couleur n'est pas seule.
 */
export interface OptionSegment<T extends string> {
  valeur: T;
  libelle: string;
  icone?: ReactNode;
  testId?: string;
}

export function ChoixSegmente<T extends string>({
  libelle,
  options,
  valeur,
  onChange,
}: {
  libelle: string;
  options: OptionSegment<T>[];
  valeur: T;
  onChange: (v: T) => void;
}) {
  return (
    <div role="group" aria-label={libelle} className="inline-flex rounded-lg border border-border bg-bg-raised p-0.5">
      {options.map((o) => {
        const actif = o.valeur === valeur;
        return (
          <button
            key={o.valeur}
            type="button"
            aria-pressed={actif}
            data-testid={o.testId}
            onClick={() => onChange(o.valeur)}
            className={cn(
              'inline-flex h-8 items-center gap-1.5 whitespace-nowrap rounded-md px-3 text-sm font-medium transition-colors duration-150',
              actif ? 'bg-accent-soft text-accent' : 'text-fg-muted hover:bg-bg-overlay hover:text-fg',
            )}
          >
            {o.icone && <span aria-hidden="true" className="inline-flex [&>svg]:h-4 [&>svg]:w-4">{o.icone}</span>}
            {o.libelle}
          </button>
        );
      })}
    </div>
  );
}

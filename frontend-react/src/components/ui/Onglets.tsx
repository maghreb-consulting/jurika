import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Onglets (lot L6b, E1), motif ARIA "tabs" : role tablist / tab / tabpanel, un seul onglet
 * dans l'ordre de tabulation, fleches gauche / droite, Debut / Fin. Onglet actif souligne
 * d'or. Controle : `actif` + `onChange`.
 *
 * Avec `contenu` sur chaque onglet, le composant rend le panneau actif ; sans contenu,
 * l appelant rend le sien sous la liste d onglets.
 */
export interface Onglet {
  id: string;
  libelle: ReactNode;
  contenu?: ReactNode;
  badge?: ReactNode;
  disabled?: boolean;
}

export function Onglets({
  onglets,
  actif,
  onChange,
  libelle,
  className,
}: {
  onglets: Onglet[];
  actif: string;
  onChange: (id: string) => void;
  /** Nom accessible de la liste d'onglets. */
  libelle: string;
  className?: string;
}) {
  const base = useId();
  const refs = useRef(new Map<string, HTMLButtonElement>());
  const actifs = onglets.filter((o) => !o.disabled);
  const courant = onglets.find((o) => o.id === actif);

  function aller(e: KeyboardEvent<HTMLDivElement>) {
    const i = actifs.findIndex((o) => o.id === actif);
    let cible: Onglet | undefined;
    if (e.key === 'ArrowRight') cible = actifs[(i + 1) % actifs.length];
    else if (e.key === 'ArrowLeft') cible = actifs[(i - 1 + actifs.length) % actifs.length];
    else if (e.key === 'Home') cible = actifs[0];
    else if (e.key === 'End') cible = actifs[actifs.length - 1];
    if (!cible) return;
    e.preventDefault();
    onChange(cible.id);
    refs.current.get(cible.id)?.focus();
  }

  return (
    <div className={className}>
      <div role="tablist" aria-label={libelle} onKeyDown={aller} className="flex gap-1 overflow-x-auto border-b border-border">
        {onglets.map((o) => {
          const choisi = o.id === actif;
          return (
            <button
              key={o.id}
              ref={(el) => {
                if (el) refs.current.set(o.id, el);
                else refs.current.delete(o.id);
              }}
              type="button"
              role="tab"
              id={`${base}-onglet-${o.id}`}
              aria-selected={choisi}
              aria-controls={choisi && o.contenu !== undefined ? `${base}-panneau-${o.id}` : undefined}
              tabIndex={choisi ? 0 : -1}
              disabled={o.disabled}
              onClick={() => onChange(o.id)}
              className={cn(
                '-mb-px inline-flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors duration-150',
                'disabled:cursor-not-allowed disabled:opacity-55',
                choisi ? 'border-accent text-fg' : 'border-transparent text-fg-muted hover:border-border-hi hover:text-fg',
              )}
            >
              {o.libelle}
              {o.badge}
            </button>
          );
        })}
      </div>
      {courant?.contenu !== undefined && (
        <div
          role="tabpanel"
          id={`${base}-panneau-${courant.id}`}
          aria-labelledby={`${base}-onglet-${courant.id}`}
          tabIndex={0}
          className="pt-4 focus-visible:outline-offset-4"
        >
          {courant.contenu}
        </div>
      )}
    </div>
  );
}

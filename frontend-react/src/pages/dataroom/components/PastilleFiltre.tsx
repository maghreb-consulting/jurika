import type { ReactNode } from 'react';
import { Check } from 'lucide-react';

/**
 * Lot L6b (E6) : choix a bascule d'un filtre (type de document, type d'operation).
 * Bouton a etat (aria-pressed) : contour au repos, teinte or douce et coche une fois choisi
 * (l'etat n'est pas porte par la seule couleur).
 */
export function PastilleFiltre({
  actif,
  onClick,
  children,
}: {
  actif: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={actif}
      onClick={onClick}
      className={`inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-xs font-medium transition-colors duration-150 ${
        actif
          ? 'border-accent bg-accent-soft text-fg'
          : 'border-border-input bg-bg-raised text-fg-muted hover:border-border-hi hover:text-fg'
      }`}
    >
      {actif && <Check aria-hidden="true" className="h-3.5 w-3.5" />}
      {children}
    </button>
  );
}

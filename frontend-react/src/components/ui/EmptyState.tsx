import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Etat vide (lot L6b, E1) : il explique ce qui manque et QUOI FAIRE. Fournir un `title`
 * qui constate ("Aucun ticket pour le moment"), une `description` qui dit la marche a
 * suivre, et si possible l'action (`cta`, un bouton principal) ; `secondary` : action
 * annexe (lien d'aide...). `icon` : decorative.
 *
 * `compact` : version reduite (dans une carte, une cellule de tableau).
 */
export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  cta?: ReactNode;
  secondary?: ReactNode;
  className?: string;
  compact?: boolean;
}

export function EmptyState({ icon, title, description, cta, secondary, className, compact = false }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center text-center', compact ? 'px-4 py-6' : 'px-6 py-12', className)}>
      {icon && (
        <div
          aria-hidden="true"
          className={cn(
            'mb-4 flex items-center justify-center rounded-full bg-bg-overlay text-fg-muted [&>svg]:h-6 [&>svg]:w-6',
            compact ? 'h-10 w-10' : 'h-12 w-12',
          )}
        >
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-heading">{title}</h3>
      {description && <div className="mt-1.5 max-w-md text-sm leading-relaxed text-fg-muted">{description}</div>}
      {(cta || secondary) && (
        <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
          {cta}
          {secondary}
        </div>
      )}
    </div>
  );
}

/** Nom francais du meme composant. */
export const EtatVide = EmptyState;

import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * En-tete d'une section depliable (lot L6b, E5 W2 ; ajout a la bibliotheque). Toute la
 * largeur de la section est cliquable ; le chevron dit l'etat (droite : repliee, bas :
 * depliee) et aria-expanded l'annonce. L'etat appartient a l'appelant (`ouvert`).
 *
 * Ex. : <EnTeteDepliant ouvert={open} onClick={() => setOpen((v) => !v)}>Titre</EnTeteDepliant>
 */
export interface EnTeteDepliantProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  ouvert: boolean;
  /** Icone decorative avant le titre. */
  icone?: ReactNode;
  children: ReactNode;
}

export function EnTeteDepliant({ ouvert, icone, children, className, ...rest }: EnTeteDepliantProps) {
  const Chevron = ouvert ? ChevronDown : ChevronRight;
  return (
    <button
      type="button"
      aria-expanded={ouvert}
      className={cn(
        'flex w-full items-center justify-between gap-2 rounded-xl px-4 py-3 text-left transition-colors duration-150',
        'hover:bg-bg-overlay/60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
        className,
      )}
      {...rest}
    >
      <span className="flex min-w-0 items-center gap-2">
        {icone && (
          <span aria-hidden="true" className="inline-flex shrink-0 text-fg-muted [&>svg]:h-4 [&>svg]:w-4">
            {icone}
          </span>
        )}
        {children}
      </span>
      <Chevron className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
    </button>
  );
}

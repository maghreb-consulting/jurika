import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Badge d'etat (lot L6b, E1) : texte d'etat sur sa teinte douce (contraste AA verifie).
 * L'etat n'est jamais porte par la seule couleur : le libelle le dit ; `dot` ajoute une
 * pastille (decorative).
 *
 * Variantes : `default` (or : actif, selectionne), `success`, `warning`, `danger`, `info`,
 * `neutral` (gris : brouillon, inactif).
 */
export type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const VARIANTES: Record<BadgeVariant, string> = {
  default: 'bg-accent-soft text-accent border-accent/30',
  success: 'bg-success-soft text-success border-success/30',
  warning: 'bg-warning-soft text-warning border-warning/30',
  danger: 'bg-danger-soft text-danger border-danger/30',
  info: 'bg-info-soft text-info border-info/30',
  neutral: 'bg-bg-overlay text-fg-muted border-border',
};

export function Badge({
  variant = 'default',
  children,
  className,
  dot = false,
  title,
}: {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
  dot?: boolean;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium',
        VARIANTES[variant],
        className,
      )}
    >
      {dot && <span aria-hidden="true" className="h-1.5 w-1.5 shrink-0 rounded-full bg-current" />}
      {children}
    </span>
  );
}

/** Nom francais du meme composant. */
export const BadgeEtat = Badge;

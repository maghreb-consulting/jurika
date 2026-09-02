import type { HTMLAttributes, ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';

interface Props extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  /**
   * Sprint 12.5 T5 — Hairline or signature en haut de la carte.
   * Cohérent avec marketing-site (cards "scellées").
   */
  accent?: boolean;
  /**
   * Sprint 12.5 T5 — Hover lift + glow gold subtle. À utiliser sur les
   * cards cliquables/interactives (pas pour les KPI passifs).
   */
  interactive?: boolean;
}

/**
 * Sprint 12.5 T5 — Card primitive repeinte sur palette marketing.
 *
 * AVANT : white bg + slate-200 border + shadow-sm (light theme implicit).
 * APRÈS : bg-raised navy + border-border + shadow-card token, suit data-theme
 * automatiquement. Variantes optionnelles `accent` et `interactive`.
 */
export function Card({
  children,
  className,
  accent = false,
  interactive = false,
  ...rest
}: Props) {
  return (
    <div
      className={twMerge(
        'rounded-2xl border border-border bg-bg-raised shadow-card',
        accent && 'border-t-2 border-t-accent',
        interactive &&
          'transition-transform duration-200 hover:-translate-y-0.5 hover:shadow-card-lifted hover:border-border-hi',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}

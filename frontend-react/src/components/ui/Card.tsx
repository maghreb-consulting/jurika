import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Carte (lot L6b, E1) : surface blanche (sombre : marine eleve), bordure fine, ombre
 * legere. En-tete optionnel : `title` (h2 par defaut, `titleAs` pour un autre niveau),
 * `description`, `actions` (a droite). `padding={false}` pour un contenu bord a bord
 * (tableau).
 *
 * `accent` : filet or en haut (carte mise en avant, une par ecran au plus).
 * `interactive` : carte cliquable (survol : ombre ; aucun deplacement).
 */
export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  children?: ReactNode;
  accent?: boolean;
  interactive?: boolean;
  title?: ReactNode;
  titleAs?: 'h2' | 'h3' | 'h4';
  description?: ReactNode;
  actions?: ReactNode;
  padding?: boolean;
}

export function Card({
  children,
  className,
  accent = false,
  interactive = false,
  title,
  titleAs: Titre = 'h2',
  description,
  actions,
  padding = true,
  ...rest
}: CardProps) {
  const enTete = title || description || actions;
  return (
    <div
      className={cn(
        '@container rounded-xl border border-border bg-bg-raised shadow-card',
        accent && 'border-t-2 border-t-accent',
        interactive && 'transition-[box-shadow,border-color] duration-150 hover:border-border-hi hover:shadow-card-lifted',
        className,
      )}
      {...rest}
    >
      {enTete && (
        <div className={cn('flex flex-wrap items-start justify-between gap-3', padding ? 'px-5 pt-4' : 'border-b border-border px-5 py-3')}>
          <div className="min-w-0">
            {title && <Titre className="text-base font-semibold text-heading">{title}</Titre>}
            {description && <p className="mt-0.5 text-sm text-fg-muted">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </div>
      )}
      {enTete && padding ? <div className="px-5 pb-5 pt-3">{children}</div> : children}
    </div>
  );
}

/** Nom francais du meme composant. */
export const Carte = Card;

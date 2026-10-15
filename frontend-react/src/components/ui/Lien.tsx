import type { AnchorHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { cn } from '../../lib/utils';
import { classesBouton, type ButtonSize, type ButtonVariant } from './boutonClasses';

/**
 * Lien de la bibliotheque (lot L6b, E1). Un lien se lit comme un lien : couleur or et
 * souligne (toujours : pas d'information portee par la seule couleur).
 *
 * - `to` : lien interne (react-router) ; `href` : lien externe (nouvel onglet signale,
 *   rel="noopener noreferrer").
 * - `variante="discret"` : texte courant souligne (listes denses, pieds de page).
 * - `commeBouton` : un lien qui mene a un ecran mais doit ressembler a un bouton
 *   (action principale d'un en-tete de page) : memes classes que <Button>.
 */
type Commun = {
  children: ReactNode;
  className?: string;
  variante?: 'defaut' | 'discret';
  commeBouton?: { variant?: ButtonVariant; size?: ButtonSize };
};

export type LienProps =
  | (Commun & Omit<LinkProps, 'className' | 'children'> & { href?: never })
  | (Commun & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className' | 'children'> & { href: string; to?: never; externe?: boolean });

export const CLASSES_LIEN =
  'rounded-sm font-medium text-accent underline decoration-1 underline-offset-2 hover:text-accent-hover hover:decoration-2';
const CLASSES_LIEN_DISCRET = 'rounded-sm text-fg-muted underline decoration-1 underline-offset-2 hover:text-fg';

export function Lien(props: LienProps) {
  const { children, className, variante = 'defaut', commeBouton, ...rest } = props;
  const classes = commeBouton
    ? classesBouton(commeBouton.variant ?? 'secondary', commeBouton.size ?? 'md', className)
    : cn(variante === 'discret' ? CLASSES_LIEN_DISCRET : CLASSES_LIEN, className);

  if ('href' in rest && typeof rest.href === 'string') {
    const { externe = /^https?:\/\//.test(rest.href), ...a } = rest as AnchorHTMLAttributes<HTMLAnchorElement> & {
      href: string;
      externe?: boolean;
    };
    return (
      <a
        {...a}
        className={cn(classes, externe && 'inline-flex items-center gap-1')}
        target={externe ? '_blank' : a.target}
        rel={externe ? 'noopener noreferrer' : a.rel}
      >
        {children}
        {externe && (
          <>
            <ExternalLink className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span className="sr-only"> (s’ouvre dans un nouvel onglet)</span>
          </>
        )}
      </a>
    );
  }
  return (
    <Link {...(rest as Omit<LinkProps, 'className' | 'children'>)} className={classes}>
      {children}
    </Link>
  );
}

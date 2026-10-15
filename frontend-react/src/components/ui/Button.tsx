import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { BASE_BOUTON, TAILLES_BOUTON, TAILLES_ICONE, VARIANTES_BOUTON, type ButtonSize, type ButtonVariant } from './boutonClasses';

export type { ButtonSize, ButtonVariant } from './boutonClasses';

/**
 * Bouton de la bibliotheque (lot L6b, E1). Un bouton ressemble toujours a un bouton
 * (forme, fond ou contour, hauteur fixe) ; un texte ne ressemble jamais a un bouton.
 *
 * Variantes : `primary` (principal, or : UNE action principale par ecran), `secondary`
 * (contour), `ghost` (discret : fond teinte, sans contour), `danger` (action
 * irreversible). Tailles `sm` (32 px), `md` (36 px), `lg` (44 px).
 *
 * `loading` : roue + aria-busy, le bouton est desactive et garde sa largeur.
 * `icon` / `iconEnd` : icone avant / apres le libelle (decorative, aria-hidden).
 * `iconOnly` : bouton carre sans libelle visible ; `aria-label` obligatoire.
 */
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
  iconEnd?: ReactNode;
  iconOnly?: boolean;
  fullWidth?: boolean;
  /** @deprecated L6b : effet "magnetique" retire (mouvement superflu) ; sans effet. */
  magnetic?: boolean;
  children?: ReactNode;
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('inline-block h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent', className)}
    />
  );
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    icon,
    iconEnd,
    iconOnly = false,
    fullWidth = false,
    magnetic: _magnetic,
    className,
    disabled,
    children,
    ...rest
  },
  ref,
) {
  void _magnetic;
  return (
    <button
      ref={ref}
      className={cn(
        BASE_BOUTON,
        VARIANTES_BOUTON[variant],
        iconOnly ? TAILLES_ICONE[size] : TAILLES_BOUTON[size],
        fullWidth && 'w-full',
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner /> : icon && <span aria-hidden="true" className="inline-flex shrink-0 [&>svg]:h-4 [&>svg]:w-4">{icon}</span>}
      {children}
      {iconEnd && !loading && <span aria-hidden="true" className="inline-flex shrink-0 [&>svg]:h-4 [&>svg]:w-4">{iconEnd}</span>}
    </button>
  );
});

/** Nom francais du meme composant. */
export const Bouton = Button;

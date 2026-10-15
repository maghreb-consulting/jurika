import { cn } from '../../lib/utils';

/** Classes du bouton (lot L6b, E1), partagees par <Button> et les liens qui en ont l'apparence. */
export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export const BASE_BOUTON =
  'relative inline-flex select-none items-center justify-center gap-2 whitespace-nowrap rounded-lg font-medium ' +
  'transition-colors duration-150 ease-[var(--ease-standard)] ' +
  'disabled:cursor-not-allowed disabled:opacity-55 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus';

export const VARIANTES_BOUTON: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-accent-fg shadow-card hover:bg-accent-hover enabled:active:bg-accent-hover',
  secondary: 'border border-border-input bg-bg-raised text-fg shadow-card hover:bg-bg-overlay',
  ghost: 'bg-bg-overlay text-fg hover:bg-border',
  danger: 'bg-danger text-danger-fg shadow-card hover:bg-danger-hover',
};

export const TAILLES_BOUTON: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-9 px-4 text-sm',
  lg: 'h-11 px-5 text-base',
};

export const TAILLES_ICONE: Record<ButtonSize, string> = {
  sm: 'h-8 w-8 px-0',
  md: 'h-9 w-9 px-0',
  lg: 'h-11 w-11 px-0',
};

/** Classes d'un bouton, pour un lien (<Link>) qui doit en avoir l'apparence. */
export function classesBouton(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', className?: string): string {
  return cn(BASE_BOUTON, VARIANTES_BOUTON[variant], TAILLES_BOUTON[size], className);
}


import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { Skeleton } from '../ui/LoadingSkeleton';

import { ABSENT, valeurOuAbsent } from './valeurs';

interface Props {
  label: string;
  /** null / undefined => "--" (donnee absente). */
  value: string | number | null | undefined;
  hint?: ReactNode;
  icon?: ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'danger';
  /** Chargement : squelette aux dimensions de la valeur (aucun decalage). */
  loading?: boolean;
}

/**
 * Lot L6b (E9) : indicateur chiffre d'un tableau de bord, aux jetons du systeme de design.
 * Le ton (succes, attention, danger) colore la seule valeur et un filet a gauche ; l'or n'est
 * pas utilise ici (reserve a l'action principale et a l'etat actif).
 */
const FILET: Record<NonNullable<Props['variant']>, string> = {
  default: 'border-l-border-hi',
  success: 'border-l-success',
  warning: 'border-l-warning',
  danger: 'border-l-danger',
};

const VALEUR: Record<NonNullable<Props['variant']>, string> = {
  default: 'text-heading',
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
};

export function KpiCard({ label, value, hint, icon, variant = 'default', loading = false }: Props) {
  const affiche = valeurOuAbsent(value);
  return (
    <div className={cn('rounded-xl border border-border border-l-4 bg-bg-raised px-4 py-3 shadow-card', FILET[variant])}>
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-fg-muted">{label}</p>
        {icon && (
          <span className="text-fg-subtle [&>svg]:h-4 [&>svg]:w-4" aria-hidden="true">
            {icon}
          </span>
        )}
      </div>
      <div className="mt-1 flex h-8 items-center">
        {loading ? (
          <Skeleton className="h-6 w-16" />
        ) : (
          <p
            className={cn('text-2xl font-semibold tabular-nums', affiche === ABSENT ? 'text-fg-subtle' : VALEUR[variant])}
            title={affiche === ABSENT ? 'Donnée non disponible' : undefined}
          >
            {affiche}
          </p>
        )}
      </div>
      {hint && <div className="mt-1 text-xs text-fg-muted">{hint}</div>}
    </div>
  );
}

import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { InfoBulle } from '../ui/Aide';
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
  /** Lot L6b (E17) : liste ou ecran ou l'indicateur se detaille ; toute la carte devient un lien. */
  to?: string;
  /** Lot L6b (E17) : ce que compte l'indicateur (infobulle a cote du libelle). */
  aide?: string;
  /** Lot L6b (E18) : petite courbe de tendance a droite de la valeur (Sparkline). */
  tendance?: ReactNode;
}

/**
 * Lot L6b (E9) : indicateur chiffre d'un tableau de bord, aux jetons du systeme de design.
 * Le ton (succes, attention, danger) colore la seule valeur et un filet a gauche ; l'or n'est
 * pas utilise ici (reserve a l'action principale et a l'etat actif).
 *
 * Lot L6b (E17) : `to` rend la carte cliquable (lien etire sur le libelle, le clavier passe par
 * lui) et `aide` explique le calcul dans une infobulle.
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

export function KpiCard({ label, value, hint, icon, variant = 'default', loading = false, to, aide, tendance }: Props) {
  const affiche = valeurOuAbsent(value);
  return (
    <div
      className={cn(
        'relative rounded-xl border border-border border-l-4 bg-bg-raised px-4 py-3 shadow-card',
        to && 'transition-[box-shadow,border-color] duration-150 hover:border-border-hi hover:shadow-card-lifted',
        FILET[variant],
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="flex items-center gap-1 text-xs font-medium uppercase tracking-wide text-fg-muted">
          {to ? (
            <Link to={to} className="rounded-sm after:absolute after:inset-0 after:rounded-xl hover:underline">
              {label}
            </Link>
          ) : (
            label
          )}
          {aide && (
            <span className="relative z-10 normal-case tracking-normal">
              <InfoBulle libelle={`Aide : ${label}`} texte={aide} />
            </span>
          )}
        </p>
        {icon && (
          <span className="text-fg-subtle [&>svg]:h-4 [&>svg]:w-4" aria-hidden="true">
            {icon}
          </span>
        )}
      </div>
      <div className="mt-1 flex h-8 items-center justify-between gap-2">
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
        {!loading && tendance && <span className="shrink-0">{tendance}</span>}
      </div>
      {hint && <div className="mt-1 text-xs text-fg-muted">{hint}</div>}
    </div>
  );
}

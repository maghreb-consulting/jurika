import type { ReactNode } from 'react';
import { cn } from '../../../lib/utils';

interface Props {
  title: string;
  icon?: ReactNode;
  /** true -> affiche l'etat vide au lieu des enfants. */
  empty?: boolean;
  emptyLabel?: string;
  /** Action a droite du titre (lien "Exporter", "Tout voir"...). */
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

/**
 * Carte-conteneur d'un graphique de tableau de bord (lot L6b, E9 : jetons du systeme de design).
 * Centralise le cadre, le titre (h3) et l'etat vide, pour que chaque graphique reste minimal.
 */
export function ChartCard({ title, icon, empty = false, emptyLabel = 'Aucune donnée sur la période.', actions, children, className }: Props) {
  return (
    <section className={cn('rounded-xl border border-border bg-bg-raised p-4 shadow-card', className)}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          {icon && (
            <span className="text-fg-subtle [&>svg]:h-4 [&>svg]:w-4" aria-hidden="true">
              {icon}
            </span>
          )}
          <h3 className="text-sm font-semibold text-heading">{title}</h3>
        </div>
        {actions}
      </div>
      {empty ? <p className="py-10 text-center text-sm text-fg-muted">{emptyLabel}</p> : children}
    </section>
  );
}

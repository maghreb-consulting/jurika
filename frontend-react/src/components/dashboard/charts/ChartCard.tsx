import type { ReactNode } from 'react';
import { cn } from '../../../lib/utils';

interface Props {
  title: string;
  icon?: ReactNode;
  /** Lot L6b (E18) : une phrase qui dit ce que montre le graphique (sous le titre). */
  description?: ReactNode;
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
 * Centralise le cadre, le titre (h3), la phrase de lecture et l'etat vide, pour que chaque
 * graphique reste minimal.
 */
export function ChartCard({ title, icon, description, empty = false, emptyLabel = 'Aucune donnée sur la période.', actions, children, className }: Props) {
  return (
    <section className={cn('flex min-w-0 flex-col rounded-xl border border-border bg-bg-raised p-4 shadow-card', className)}>
      <div className="mb-3 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-2">
            {icon && (
              <span className="text-fg-subtle [&>svg]:h-4 [&>svg]:w-4" aria-hidden="true">
                {icon}
              </span>
            )}
            <h3 className="text-sm font-semibold text-heading">{title}</h3>
          </div>
          {description && <p className="mt-0.5 text-xs text-fg-muted">{description}</p>}
        </div>
        {actions}
      </div>
      {empty ? (
        <div className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-border py-10">
          <p className="px-4 text-center text-sm text-fg-muted">{emptyLabel}</p>
        </div>
      ) : (
        <div className="flex-1">{children}</div>
      )}
    </section>
  );
}

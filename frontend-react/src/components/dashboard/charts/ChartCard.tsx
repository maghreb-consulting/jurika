import type { ReactNode } from 'react';

interface Props {
  title: string;
  icon?: ReactNode;
  /** true -> affiche l'etat vide au lieu des enfants. */
  empty?: boolean;
  emptyLabel?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Carte-conteneur standard pour un graphique de dashboard (charte JURIKA).
 * Centralise le cadre (border/bg-raised/rounded) + le titre + l'etat vide,
 * afin que chaque graphique reste minimal et coherent.
 */
export function ChartCard({
  title,
  icon,
  empty = false,
  emptyLabel = 'Aucune donnée',
  children,
  className = '',
}: Props) {
  return (
    <div className={`rounded-xl border border-border bg-bg-raised p-4 ${className}`}>
      <div className="mb-3 flex items-center gap-2">
        {icon && <span className="text-fg-subtle">{icon}</span>}
        <h4 className="text-sm font-semibold text-fg">{title}</h4>
      </div>
      {empty ? (
        <p className="py-10 text-center text-xs text-fg-subtle">{emptyLabel}</p>
      ) : (
        children
      )}
    </div>
  );
}

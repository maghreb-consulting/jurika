import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Chargement (lot L6b, E1) : squelettes AUX DIMENSIONS du contenu attendu (aucun decalage
 * quand il arrive), jamais de roue en pleine page. Pulsation lente, coupee si l'utilisateur
 * reduit les animations. Les squelettes sont decoratifs (aria-hidden) : le conteneur annonce
 * le chargement (`SqueletteZone`, role="status").
 */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('squelette h-4', className)} />;
}

/** Zone en cours de chargement : annonce "Chargement..." aux lecteurs d'ecran. */
export function SqueletteZone({ libelle = 'Chargement…', children, className }: { libelle?: string; children: ReactNode; className?: string }) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <span className="sr-only">{libelle}</span>
      {children}
    </div>
  );
}

export function SqueletteTexte({ lignes = 3, className }: { lignes?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)}>
      {Array.from({ length: lignes }).map((_, i) => (
        <Skeleton key={i} className={cn('h-3.5', i === lignes - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="w-full space-y-2" aria-hidden="true">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex items-center gap-3">
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} className={cn('h-8 flex-1', c === 0 && 'max-w-[40%]')} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function CardSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn('rounded-xl border border-border bg-bg-raised p-5', className)} aria-hidden="true">
      <Skeleton className="mb-3 h-4 w-1/3" />
      <Skeleton className="mb-2 h-3 w-2/3" />
      <Skeleton className="h-3 w-1/2" />
    </div>
  );
}

/**
 * Squelette d'un ecran entier (attente du code d'une page chargee a la demande) : en-tete
 * de page (titre + action) puis trois cartes, aux dimensions de PageEnTete.
 */
export function SquelettePage() {
  return (
    <SqueletteZone libelle="Chargement de la page…">
      <div className="mb-6 flex min-h-[4.5rem] items-end justify-between gap-4 border-b border-border pb-4">
        <div className="w-full max-w-md space-y-2">
          <Skeleton className="h-3 w-40" />
          <Skeleton className="h-7 w-2/3" />
        </div>
        <Skeleton className="h-9 w-36 rounded-lg" />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <CardSkeleton />
        <CardSkeleton />
        <CardSkeleton />
      </div>
    </SqueletteZone>
  );
}

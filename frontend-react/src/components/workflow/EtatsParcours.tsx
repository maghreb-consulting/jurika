import type { ReactNode } from 'react';
import { CardSkeleton, Skeleton, SqueletteTexte, SqueletteZone } from '../ui/LoadingSkeleton';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';

/**
 * Lot L6b (E5) : etats communs des pages de parcours.
 *
 * - SqueletteParcours : chargement aux dimensions de la page (en-tete, frise, contenu),
 *   aucun decalage a l'arrivee des donnees.
 * - ErreurParcours : chargement impossible, avec le chemin pour revenir aux tickets.
 */
export function SqueletteParcours({ testId, libelle = 'Chargement du parcours…' }: { testId?: string; libelle?: string }) {
  return (
    <div data-testid={testId}>
      <SqueletteZone libelle={libelle}>
        <div className="mb-6 flex min-h-[4.5rem] items-end justify-between gap-4 border-b border-border pb-4">
          <div className="w-full max-w-md space-y-2">
            <Skeleton className="h-3 w-40" />
            <Skeleton className="h-7 w-2/3" />
          </div>
          <Skeleton className="h-9 w-36 rounded-lg" />
        </div>
        <div className="mb-5 rounded-xl border border-border bg-bg-raised p-4">
          <Skeleton className="mb-3 h-4 w-48" />
          <div className="flex justify-between gap-2">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-8 rounded-full" />
            ))}
          </div>
          <Skeleton className="mt-3 h-1 w-full" />
        </div>
        <div className="rounded-xl border border-border bg-bg-raised p-6">
          <SqueletteTexte lignes={4} />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <CardSkeleton />
            <CardSkeleton />
          </div>
        </div>
      </SqueletteZone>
    </div>
  );
}

export function ErreurParcours({ children, testId }: { children: ReactNode; testId?: string }) {
  return (
    <div data-testid={testId}>
      <Message
        ton="erreur"
        titre="Le parcours ne peut pas s’afficher"
        action={<Lien to="/tickets">Revenir à la liste des tickets</Lien>}
      >
        {children}
      </Message>
    </div>
  );
}

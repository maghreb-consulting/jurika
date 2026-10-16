import { useId, type ReactNode } from 'react';

/**
 * Lot L6b (E17) : section d'un tableau de bord (titre h2, icone, etat, lien a droite), commune
 * aux quatre roles pour une meme hierarchie visuelle.
 */
export function SectionTableauDeBord({
  titre,
  icone,
  etat,
  lien,
  aide,
  children,
  className = 'mb-8',
}: {
  titre: string;
  icone?: ReactNode;
  /** Badge a cote du titre (compteur, alerte). */
  etat?: ReactNode;
  /** Lien a droite ("Voir tous les tickets"). */
  lien?: ReactNode;
  /** Infobulle ou texte court a la suite du titre. */
  aide?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={className}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="flex items-center gap-2 text-lg font-semibold text-heading">
          {icone && (
            <span className="text-fg-subtle [&>svg]:h-5 [&>svg]:w-5" aria-hidden="true">
              {icone}
            </span>
          )}
          {titre}
          {aide}
          {etat}
        </h2>
        {lien && <span className="text-sm">{lien}</span>}
      </div>
      {children}
    </section>
  );
}

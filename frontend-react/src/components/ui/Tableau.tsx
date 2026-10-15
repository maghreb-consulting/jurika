import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { cn } from '../../lib/utils';
import { Skeleton } from './LoadingSkeleton';

/**
 * Tableau de donnees (lot L6b, E1).
 *
 * - En-tete collant (`enTeteCollant`, defaut vrai) : le tableau defile dans son cadre, haut au
 *   plus de l'ecran moins l'en-tete (`hauteurMax`, ex. '24rem'), et l'en-tete de colonnes reste
 *   visible. Un en-tete `sticky` ne peut pas coller a la page depuis un cadre qui defile
 *   lateralement : d'ou le defilement dans le cadre. `enTeteCollant={false}` : pas de cadre
 *   vertical (le tableau suit la page).
 * - Tri VISUEL et controle : une colonne `triable` rend un bouton dans son en-tete ;
 *   `tri` dit la colonne et le sens courants (aria-sort), `onTri` recoit la colonne
 *   cliquee. Le tri des donnees reste a l'appelant (souvent le serveur).
 * - `chargement` : lignes squelettes aux dimensions du tableau (pas de decalage).
 * - `vide` : contenu affiche quand `lignes` est vide (un EmptyState qui explique quoi faire).
 * - `onLigne` : ligne cliquable ; pour le clavier, mettre un lien ou un bouton dans une
 *   cellule (la ligne elle-meme n'est pas focalisable).
 * - `legende` : <caption> (lue par les lecteurs d'ecran ; visible si `legendeVisible`).
 */
export interface ColonneTableau<T> {
  cle: string;
  titre: ReactNode;
  rendu: (ligne: T) => ReactNode;
  triable?: boolean;
  alignement?: 'gauche' | 'droite' | 'centre';
  /** Classe de largeur (ex. 'w-40'). */
  largeur?: string;
  /** Masquee sous ce point de rupture du conteneur : 'sm' | 'md' | 'lg'. */
  masquerSous?: 'sm' | 'md' | 'lg';
}

export interface TriTableau {
  cle: string;
  sens: 'asc' | 'desc';
}

const ALIGN = { gauche: 'text-left', droite: 'text-right', centre: 'text-center' };
const MASQUE = { sm: 'hidden @sm:table-cell', md: 'hidden @md:table-cell', lg: 'hidden @lg:table-cell' };

export function Tableau<T>({
  colonnes,
  lignes,
  cleLigne,
  legende,
  legendeVisible = false,
  tri,
  onTri,
  chargement = false,
  lignesChargement = 5,
  vide,
  onLigne,
  enTeteCollant = true,
  hauteurMax = 'calc(100dvh - 12rem)',
  className,
}: {
  colonnes: ColonneTableau<T>[];
  lignes: T[];
  cleLigne: (ligne: T) => string;
  legende: string;
  legendeVisible?: boolean;
  tri?: TriTableau | null;
  onTri?: (cle: string) => void;
  chargement?: boolean;
  lignesChargement?: number;
  vide?: ReactNode;
  onLigne?: (ligne: T) => void;
  enTeteCollant?: boolean;
  hauteurMax?: string;
  className?: string;
}) {
  const estVide = !chargement && lignes.length === 0;
  return (
    <div
      className={cn('@container w-full overflow-auto overscroll-auto rounded-xl border border-border bg-bg-raised', className)}
      style={enTeteCollant ? { maxHeight: hauteurMax } : undefined}
    >
      <table className="w-full border-collapse text-sm" aria-busy={chargement || undefined}>
        <caption className={legendeVisible ? 'px-4 py-3 text-left text-sm font-semibold text-heading' : 'sr-only'}>{legende}</caption>
        <thead className={cn(enTeteCollant && 'sticky top-0 z-10')}>
          <tr className="border-b border-border bg-bg-overlay">
            {colonnes.map((c) => {
              const actif = tri?.cle === c.cle;
              const ariaSort = c.triable ? (actif ? (tri!.sens === 'asc' ? 'ascending' : 'descending') : 'none') : undefined;
              const Icone = actif ? (tri!.sens === 'asc' ? ArrowUp : ArrowDown) : ArrowUpDown;
              return (
                <th
                  key={c.cle}
                  scope="col"
                  aria-sort={ariaSort}
                  className={cn(
                    'whitespace-nowrap px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-fg-muted',
                    ALIGN[c.alignement ?? 'gauche'],
                    c.largeur,
                    c.masquerSous && MASQUE[c.masquerSous],
                  )}
                >
                  {c.triable && onTri ? (
                    <button
                      type="button"
                      onClick={() => onTri(c.cle)}
                      className={cn(
                        '-mx-1.5 inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 uppercase hover:bg-bg-raised hover:text-fg',
                        actif && 'text-fg',
                      )}
                    >
                      {c.titre}
                      <Icone className={cn('h-3.5 w-3.5', !actif && 'opacity-50')} aria-hidden="true" />
                    </button>
                  ) : (
                    c.titre
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {chargement &&
            Array.from({ length: lignesChargement }).map((_, i) => (
              <tr key={`chargement-${i}`} className="border-b border-border last:border-0">
                {colonnes.map((c) => (
                  <td key={c.cle} className={cn('h-12 px-4', c.masquerSous && MASQUE[c.masquerSous])}>
                    <Skeleton className="h-4 w-3/4" />
                  </td>
                ))}
              </tr>
            ))}
          {!chargement &&
            lignes.map((l) => (
              <tr
                key={cleLigne(l)}
                onClick={onLigne ? () => onLigne(l) : undefined}
                className={cn(
                  'border-b border-border transition-colors duration-150 last:border-0',
                  onLigne && 'cursor-pointer hover:bg-bg-overlay',
                )}
              >
                {colonnes.map((c) => (
                  <td
                    key={c.cle}
                    className={cn('h-12 px-4 py-2 text-fg', ALIGN[c.alignement ?? 'gauche'], c.masquerSous && MASQUE[c.masquerSous])}
                  >
                    {c.rendu(l)}
                  </td>
                ))}
              </tr>
            ))}
          {estVide && (
            <tr>
              <td colSpan={colonnes.length} className="px-4 py-2">
                {vide ?? <p className="py-8 text-center text-sm text-fg-muted">Aucune ligne à afficher.</p>}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

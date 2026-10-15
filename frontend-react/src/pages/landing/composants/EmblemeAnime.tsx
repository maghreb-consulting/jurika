import { useId } from 'react';
import { cn } from '../../../lib/utils';
import embleme from '../../../assets/brand/moucharabieh/embleme.svg';

/**
 * Lot L6b (E10, V3) : embleme Moucharabieh qui se dessine (accroche de la vitrine).
 *
 * Le fichier du logo (assets/brand/moucharabieh/embleme.svg) n'a pas de calques separes
 * pour l'arc et le treillis : l'animation reprend ses coordonnees exactes (meme viewBox,
 * memes chemins) en trois couches decoratives, puis pose le logo du depot par-dessus :
 *   1. la tuile marine apparait (opacite, echelle) ;
 *   2. l'arc se trace (stroke-dashoffset sur un chemin de longueur 1 : seule exception a la
 *      regle "transform et opacity", sur une boite de 64 px, une seule fois) et le socle
 *      s'etire (transform) ;
 *   3. le treillis d'or apparait dans l'arc (opacite, echelle) ;
 *   4. l'embleme du depot se pose par-dessus (opacite) : l'etat final EST le logo valide,
 *      dessin inchange.
 * Couleurs des couches : jetons (marine, or du menu). Boite fixe (64 px) : aucun decalage.
 * "Reduire les animations" : aucune animation (vitrine.css), le logo est visible d'emblee.
 */
export function EmblemeAnime({ className }: { className?: string }) {
  const id = useId().replace(/:/g, '');
  const treillis = `v-lat-${id}`;
  const coupe = `v-arc-${id}`;
  return (
    <span className={cn('v-embleme relative block h-16 w-16', className)} data-testid="embleme-anime" aria-hidden="true">
      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" focusable="false">
        <defs>
          <pattern id={treillis} width="4.2" height="4.2" patternUnits="userSpaceOnUse" patternTransform="rotate(45 50 50)">
            <path d="M0 0 H4.2 M0 0 V4.2" style={{ stroke: 'var(--jeton-nav-active)' }} strokeWidth=".45" fill="none" />
            <circle cx="0" cy="0" r=".7" style={{ fill: 'var(--jeton-nav-active)' }} />
          </pattern>
          <clipPath id={coupe}>
            <path d="M35.07 84 V59.33 A17.6 17.6 0 1 1 64.93 59.33 V84 Z" />
          </clipPath>
        </defs>
        <rect className="v-embleme-tuile" x="4" y="4" width="92" height="92" rx="10" style={{ fill: 'var(--jeton-marine)' }} />
        <g className="v-embleme-treillis" data-treillis clipPath={`url(#${coupe})`}>
          <rect x="30" y="30" width="40" height="56" fill={`url(#${treillis})`} opacity=".85" />
        </g>
        <path
          className="v-embleme-trace"
          data-trace="arc"
          d="M32.19 84 V61.13 A21 21 0 1 1 67.81 61.13 V84"
          pathLength="1"
          fill="none"
          strokeWidth="1.6"
          strokeLinecap="round"
          style={{ stroke: 'var(--jeton-nav-active)' }}
        />
        <path className="v-embleme-socle" d="M14 84 H86" strokeWidth="1.1" style={{ stroke: 'var(--jeton-nav-active)' }} />
      </svg>
      <img
        src={embleme}
        alt=""
        width={64}
        height={64}
        className="v-embleme-logo absolute inset-0 h-full w-full"
        decoding="async"
        draggable={false}
      />
    </span>
  );
}

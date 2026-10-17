import { useId, useRef } from 'react';
import { cn } from '../../../lib/utils';
import embleme from '../../../assets/brand/moucharabieh/embleme.svg';
import { useLecture } from '../demonstrations/useLecture';

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
 *
 * Lot L6c (E2, demande du 2026-10-17) : puis le logo vit en boucle, sobrement (cycle de
 * CYCLE_LOGO_S secondes, logo.ts) : un reflet d'or traverse la tuile, l'arc s'illumine
 * doucement et l'etoile a huit branches scintille. Couche par-dessus le logo du depot,
 * transparente au repos (opacite 0) : hors animation, on voit le logo exact. En pause hors
 * ecran et onglet cache (useLecture : data-pause) ; "reduire les animations" : aucune
 * boucle, le logo reste fixe (vitrine.css).
 */
export function EmblemeAnime({ className }: { className?: string }) {
  const id = useId().replace(/:/g, '');
  const treillis = `v-lat-${id}`;
  const coupe = `v-arc-${id}`;
  const tuile = `v-tuile-${id}`;
  const lueur = `v-lueur-${id}`;
  const racine = useRef<HTMLSpanElement>(null);
  const { joue } = useLecture(racine, 0);
  return (
    <span
      ref={racine}
      className={cn('v-embleme relative block h-16 w-16', className)}
      data-testid="embleme-anime"
      data-pause={joue ? undefined : ''}
      aria-hidden="true"
    >
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
      {/* Boucle (L6c) : par-dessus le logo, invisible au repos. */}
      <svg viewBox="0 0 100 100" className="v-embleme-boucle absolute inset-0 h-full w-full" data-boucle-logo focusable="false">
        <defs>
          <clipPath id={tuile}>
            <rect x="4" y="4" width="92" height="92" rx="10" />
          </clipPath>
          <linearGradient id={lueur} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" style={{ stopColor: 'var(--jeton-nav-active)', stopOpacity: 0 }} />
            <stop offset=".5" style={{ stopColor: 'var(--jeton-nav-active)', stopOpacity: 0.42 }} />
            <stop offset="1" style={{ stopColor: 'var(--jeton-nav-active)', stopOpacity: 0 }} />
          </linearGradient>
        </defs>
        <g clipPath={`url(#${tuile})`}>
          <polygon className="v-embleme-reflet" points="38,-6 60,-6 46,106 24,106" fill={`url(#${lueur})`} />
        </g>
        <path
          className="v-embleme-halo"
          d="M32.19 84 V61.13 A21 21 0 1 1 67.81 61.13 V84"
          fill="none"
          strokeWidth="2.2"
          strokeLinecap="round"
          style={{ stroke: 'var(--jeton-nav-active)' }}
        />
        <path
          className="v-embleme-etoile"
          d="M50.00,21.00 L50.69,22.34 L52.12,21.88 L51.66,23.31 L53.00,24.00 L51.66,24.69 L52.12,26.12 L50.69,25.66 L50.00,27.00 L49.31,25.66 L47.88,26.12 L48.34,24.69 L47.00,24.00 L48.34,23.31 L47.88,21.88 L49.31,22.34Z"
          style={{ fill: 'var(--jeton-nav-fg-hi)' }}
        />
      </svg>
    </span>
  );
}

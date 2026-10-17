import { useId } from 'react';

/**
 * Lot L6b (E11) : JURI, le petit robot de l'aide (dessin original, vectoriel).
 *
 * Marine et or comme la marque ; le clin d'oeil au logo Moucharabieh : l'etoile a huit
 * branches au bout de l'antenne et la fenetre en arc de la poitrine, garnie du meme
 * treillis (deux carres croises). Couleurs lues dans les jetons (fill-marine,
 * nav-active pour l'or clair, focus-inverse pour l'ivoire) : aucune couleur en dur.
 *
 * Le bras gauche fait signe : c'est la moitie visible quand JURI attend au bord droit.
 * Decoratif : `aria-hidden`, le nom accessible est porte par le bouton qui l'entoure.
 */
export function JuriRobot({ className, taille = 56 }: { className?: string; taille?: number }) {
  const brut = useId();
  const id = brut.replace(/[^a-zA-Z0-9_-]/g, '');
  const treillis = `juri-treillis-${id}`;
  return (
    <svg
      viewBox="0 0 56 68"
      width={taille}
      height={(taille * 68) / 56}
      aria-hidden="true"
      focusable="false"
      className={className}
      data-testid="juri-robot"
    >
      <defs>
        <pattern id={treillis} width="5" height="5" patternUnits="userSpaceOnUse">
          <g className="stroke-nav-active" strokeWidth="0.35" fill="none">
            <rect x="1" y="1" width="3" height="3" />
            <rect x="1" y="1" width="3" height="3" transform="rotate(45 2.5 2.5)" />
          </g>
        </pattern>
      </defs>

      {/* Antenne et etoile a huit branches (embleme). */}
      <line x1="28" y1="7" x2="28" y2="12.5" className="stroke-nav-active" strokeWidth="1.4" strokeLinecap="round" />
      <g className="fill-nav-active">
        <rect x="25.6" y="2.6" width="4.8" height="4.8" />
        <rect x="25.6" y="2.6" width="4.8" height="4.8" transform="rotate(45 28 5)" />
      </g>

      {/* Bras : le gauche fait signe, le droit au repos (trait or sous le trait marine). */}
      <path d="M14 48 L6.5 38.5" className="stroke-nav-active" strokeWidth="6" strokeLinecap="round" />
      <path d="M14 48 L6.5 38.5" className="stroke-marine" strokeWidth="3.6" strokeLinecap="round" />
      <circle cx="5.6" cy="37.2" r="2.7" className="fill-nav-active" />
      <path d="M42 48 L48.5 57.5" className="stroke-nav-active" strokeWidth="6" strokeLinecap="round" />
      <path d="M42 48 L48.5 57.5" className="stroke-marine" strokeWidth="3.6" strokeLinecap="round" />
      <circle cx="49.2" cy="58.6" r="2.7" className="fill-nav-active" />

      {/* Cou et corps. */}
      <rect x="24" y="40.5" width="8" height="4.5" rx="1" className="fill-nav-active" />
      <rect x="13" y="44" width="30" height="21" rx="7" className="fill-marine stroke-nav-active" strokeWidth="1.2" />
      {/* Fenetre en arc de la poitrine, treillis du moucharabieh. */}
      <path d="M21.5 62 V54 A6.5 6.5 0 0 1 34.5 54 V62 Z" className="fill-marine stroke-nav-active" strokeWidth="0.9" />
      <path d="M21.5 62 V54 A6.5 6.5 0 0 1 34.5 54 V62 Z" fill={`url(#${treillis})`} opacity="0.9" />

      {/* Tete, oreilles, visiere ivoire, yeux et sourire. */}
      <rect x="3.5" y="22" width="4" height="10" rx="2" className="fill-nav-active" />
      <rect x="48.5" y="22" width="4" height="10" rx="2" className="fill-nav-active" />
      <rect x="7" y="12" width="42" height="30" rx="11" className="fill-marine stroke-nav-active" strokeWidth="1.2" />
      <rect x="12" y="17.5" width="32" height="18" rx="9" className="fill-focus-inverse" />
      <circle cx="21" cy="26" r="3.3" className="fill-marine" />
      <circle cx="35" cy="26" r="3.3" className="fill-marine" />
      <circle cx="22.1" cy="24.9" r="1" className="fill-nav-fg-hi" />
      <circle cx="36.1" cy="24.9" r="1" className="fill-nav-fg-hi" />
      <path d="M24.5 31 Q28 33.8 31.5 31" fill="none" className="stroke-marine" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

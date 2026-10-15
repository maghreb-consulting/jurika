import { useId } from 'react';

/**
 * Lot L6b (E10, V3) : motif de zellige des bandeaux marine de la vitrine.
 *
 * Etoile a huit branches du logo Moucharabieh (deux carres, l'un tourne de 45 degres),
 * dessinee en SVG (<pattern>) a la couleur d'un jeton (or du menu), tres discrete (voir
 * vitrine.css : opacite faible). La couche deborde d'une tuile de chaque cote et glisse
 * lentement d'une tuile (transform seul, boucle sans raccord visible) ; une couche
 * englobante porte la parallaxe (animation liee au defilement, si le navigateur la connait).
 * Decoratif : aria-hidden, aucun evenement de pointeur. "Reduire les animations" : immobile.
 */
export function MotifZellige() {
  const id = `v-zellige-${useId().replace(/:/g, '')}`;
  return (
    <div className="v-zellige" data-motif-zellige aria-hidden="true">
      <div className="v-zellige-parallaxe">
        <svg className="v-zellige-couche" focusable="false">
          <defs>
            <pattern id={id} width="48" height="48" patternUnits="userSpaceOnUse">
              <g fill="none" strokeWidth="1" style={{ stroke: 'var(--jeton-nav-active)' }}>
                <rect x="8.8" y="8.8" width="30.4" height="30.4" />
                <rect x="8.8" y="8.8" width="30.4" height="30.4" transform="rotate(45 24 24)" />
              </g>
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill={`url(#${id})`} />
        </svg>
      </div>
    </div>
  );
}

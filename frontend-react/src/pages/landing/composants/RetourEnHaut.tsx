import { useEffect, useState } from 'react';
import { ArrowUp } from 'lucide-react';
import { mouvementReduit } from '../../../lib/mouvement';

/**
 * Lot L6b (E10, V3) : retour en haut de la vitrine.
 *
 * Le bouton apparait (opacite et translation) apres un peu plus d'un ecran de defilement ;
 * avant, il est inerte (ni clic, ni focus, ni lecteur d'ecran). Un clic remonte en haut de
 * la page en douceur (defilement natif du navigateur, instantane si les animations sont
 * reduites) et rend le focus au contenu principal. Ecoute passive du defilement, lue une
 * fois par image.
 */
export function RetourEnHaut() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let image = 0;
    const lire = () => {
      image = 0;
      setVisible(window.scrollY > window.innerHeight * 1.2);
    };
    const surDefilement = () => {
      if (typeof window.requestAnimationFrame !== 'function') lire();
      else if (!image) image = window.requestAnimationFrame(lire);
    };
    window.addEventListener('scroll', surDefilement, { passive: true });
    lire();
    return () => {
      window.removeEventListener('scroll', surDefilement);
      if (image) window.cancelAnimationFrame(image);
    };
  }, []);

  function remonter() {
    window.scrollTo({ top: 0, behavior: mouvementReduit() ? 'auto' : 'smooth' });
    document.getElementById('contenu')?.focus({ preventScroll: true });
  }

  return (
    <div className="v-retour-haut" data-testid="retour-haut" data-visible={visible ? '' : undefined} inert={!visible}>
      <button
        type="button"
        onClick={remonter}
        aria-label="Revenir en haut de la page"
        title="Revenir en haut de la page"
        className="v-bouton flex h-11 w-11 items-center justify-center rounded-full border border-border bg-bg-raised text-fg shadow-card-lifted hover:bg-bg-overlay focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        <ArrowUp className="v-retour-fleche h-5 w-5" aria-hidden="true" />
      </button>
    </div>
  );
}

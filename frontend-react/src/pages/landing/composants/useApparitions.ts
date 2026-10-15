import { useEffect, type RefObject } from 'react';
import { useMouvementReduit } from '../../../lib/mouvement';

/**
 * Lot L6b (E10, V2) : apparitions au defilement de la vitrine, sans bibliotheque.
 *
 * Les elements marques `data-apparait` recoivent `data-visible` quand ils entrent a l'ecran
 * (IntersectionObserver) ; vitrine.css fait la transition (opacite et translation, 300 ms).
 * - "Reduire les animations", ou navigateur sans IntersectionObserver : rien n'est active,
 *   tout est visible d'emblee.
 * - Ce qui est deja a l'ecran au montage est marque visible AVANT l'activation : aucun
 *   clignotement de l'accroche.
 * - Opacite et transformation seulement : la boite des elements ne change jamais (aucun
 *   decalage de la mise en page).
 */
export function useApparitions(racineRef: RefObject<HTMLElement | null>) {
  const reduit = useMouvementReduit();

  useEffect(() => {
    const racine = racineRef.current;
    if (!racine || reduit || typeof window.IntersectionObserver !== 'function') return undefined;
    const elements = Array.from(racine.querySelectorAll<HTMLElement>('[data-apparait]'));
    const hauteur = window.innerHeight;
    for (const el of elements) {
      const r = el.getBoundingClientRect();
      if (r.top < hauteur && r.bottom >= 0) el.setAttribute('data-visible', '');
    }
    const observateur = new window.IntersectionObserver(
      (entrees) => {
        for (const e of entrees) {
          if (!e.isIntersecting) continue;
          e.target.setAttribute('data-visible', '');
          observateur.unobserve(e.target);
        }
      },
      { rootMargin: '0px 0px -40px 0px', threshold: 0 },
    );
    for (const el of elements) observateur.observe(el);
    racine.setAttribute('data-apparition', '');
    return () => {
      observateur.disconnect();
      racine.removeAttribute('data-apparition');
      for (const el of elements) el.setAttribute('data-visible', '');
    };
  }, [racineRef, reduit]);
}

import { useEffect, type RefObject } from 'react';
import { useMouvementReduit } from '../../../lib/mouvement';

/**
 * Lot L6b (E10, V2) : apparitions au defilement de la vitrine, sans bibliotheque.
 *
 * Les elements marques `data-apparait` recoivent `data-visible="1"` quand ils entrent a l'ecran
 * (IntersectionObserver) ; vitrine.css les anime (opacite et translation) ; ce qui est deja
 * a l'ecran au montage recoit `data-visible="0"` (aucune animation).
 * - V3 (demande du 2026-10-15) : cascade. Chaque element recoit son rang parmi les elements
 *   apparaissants de son parent (`--rang`, 0 a 6) : vitrine.css en tire un delai, les
 *   cartes d'une grille entrent l'une apres l'autre.
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
    const rangs = new Map<Element | null, number>();
    for (const el of elements) {
      const n = rangs.get(el.parentElement) ?? 0;
      el.style.setProperty('--rang', String(Math.min(n, 6)));
      rangs.set(el.parentElement, n + 1);
    }
    const hauteur = window.innerHeight;
    for (const el of elements) {
      const r = el.getBoundingClientRect();
      if (r.top < hauteur && r.bottom >= 0) el.setAttribute('data-visible', '0');
    }
    const observateur = new window.IntersectionObserver(
      (entrees) => {
        for (const e of entrees) {
          if (!e.isIntersecting) continue;
          e.target.setAttribute('data-visible', '1');
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
      for (const el of elements) if (!el.hasAttribute('data-visible')) el.setAttribute('data-visible', '0');
    };
  }, [racineRef, reduit]);
}

import { useSyncExternalStore } from 'react';

/**
 * Lot L6b (E3) : preferences de l'ordinateur lues par media query.
 *
 * - `prefers-reduced-motion: reduce` : aucune animation (CSS : index.css ; JS : ce module) ;
 * - `prefers-color-scheme: dark` : theme "Systeme" (store/themeStore).
 *
 * Sans `matchMedia` (jsdom, vieux navigateur), la reponse est "non" : animations permises,
 * theme clair. Rien n'est suppose au-dela.
 */

const REQUETE_MOUVEMENT = '(prefers-reduced-motion: reduce)';
export const REQUETE_SOMBRE = '(prefers-color-scheme: dark)';

function requete(q: string): MediaQueryList | null {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return null;
  return window.matchMedia(q);
}

/** Vrai si l'utilisateur a demande de reduire les animations. */
export function mouvementReduit(): boolean {
  return requete(REQUETE_MOUVEMENT)?.matches ?? false;
}

/** Vrai si l'ordinateur est regle en sombre. */
export function systemeSombre(): boolean {
  return requete(REQUETE_SOMBRE)?.matches ?? false;
}

/** S'abonne aux changements d'une media query ; renvoie la fonction de desabonnement. */
export function ecouterRequete(q: string, rappel: () => void): () => void {
  const mql = requete(q);
  if (!mql) return () => {};
  mql.addEventListener('change', rappel);
  return () => mql.removeEventListener('change', rappel);
}

/** Crochet : vrai si l'utilisateur a demande de reduire les animations (suit le reglage). */
export function useMouvementReduit(): boolean {
  return useSyncExternalStore(
    (rappel) => ecouterRequete(REQUETE_MOUVEMENT, rappel),
    mouvementReduit,
    () => false,
  );
}

/**
 * Execute `maj` dans une transition de vue (View Transitions API) quand le navigateur la
 * connait et que les animations ne sont pas reduites ; sinon, directement.
 */
export function avecTransitionDeVue(maj: () => void): void {
  const doc = typeof document === 'undefined' ? null : (document as Document & {
    startViewTransition?: (rappel: () => void) => unknown;
  });
  if (doc?.startViewTransition && !mouvementReduit()) {
    doc.startViewTransition(maj);
    return;
  }
  maj();
}

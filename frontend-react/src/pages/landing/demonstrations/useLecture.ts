import { useEffect, useLayoutEffect, useState, useSyncExternalStore, type RefObject } from 'react';
import { useMouvementReduit } from '../../../lib/mouvement';

/**
 * Lot L6c (E1) : lecture des animations de la vitrine (demonstrations et logo).
 *
 * Une animation ne joue que si les trois conditions sont reunies :
 * - "reduire les animations" n'est pas demande (sinon : etat final fixe) ;
 * - l'element est a l'ecran (IntersectionObserver ; sans lui, rien ne joue : etat final fixe) ;
 * - l'onglet est visible (visibilitychange : onglet cache, tout s'arrete).
 * Aucune requete reseau, aucun magasin de l'application.
 */

function ongletVisible(): boolean {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden';
}

function ecouterOnglet(rappel: () => void): () => void {
  document.addEventListener('visibilitychange', rappel);
  return () => document.removeEventListener('visibilitychange', rappel);
}

/** Vrai tant que l'onglet est visible (suit visibilitychange). */
export function useOngletVisible(): boolean {
  return useSyncExternalStore(ecouterOnglet, ongletVisible, () => true);
}

export interface EtatLecture {
  /** L'animation joue (mouvement permis, a l'ecran, onglet visible). */
  joue: boolean;
  /** Etat final fixe : mouvement reduit, ou navigateur sans IntersectionObserver. */
  statique: boolean;
}

export function useLecture(ref: RefObject<HTMLElement | null>, seuil = 0.2): EtatLecture {
  const reduit = useMouvementReduit();
  const onglet = useOngletVisible();
  const [aLEcran, setALEcran] = useState(false);
  const sansObservateur = typeof window === 'undefined' || typeof window.IntersectionObserver !== 'function';

  useEffect(() => {
    const el = ref.current;
    if (!el || reduit || sansObservateur) return undefined;
    const obs = new window.IntersectionObserver((entrees) => {
      const e = entrees[entrees.length - 1];
      if (e) setALEcran(e.isIntersecting);
    }, { threshold: seuil });
    obs.observe(el);
    return () => obs.disconnect();
  }, [ref, reduit, sansObservateur, seuil]);

  const statique = reduit || sansObservateur;
  return { joue: !statique && aLEcran && onglet, statique };
}

/**
 * Montage differe : vrai des que l'element approche de l'ecran (une demi-fenetre avant), et
 * le reste. Les scenes lointaines ne sont construites qu'a leur approche : le chargement de
 * la page (LCP) ne paie que le cadre, dont la place est deja reservee. Sans
 * IntersectionObserver, ou mouvement reduit : vrai d'emblee (etat final affiche).
 */
export function useProche(ref: RefObject<HTMLElement | null>): boolean {
  const reduit = useMouvementReduit();
  const sansObservateur = typeof window === 'undefined' || typeof window.IntersectionObserver !== 'function';
  const [proche, setProche] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || reduit || sansObservateur || proche) return undefined;
    const obs = new window.IntersectionObserver((entrees) => {
      if (entrees.some((e) => e.isIntersecting)) setProche(true);
    }, { rootMargin: '50% 0px' });
    obs.observe(el);
    return () => obs.disconnect();
  }, [ref, reduit, sansObservateur, proche]);
  return proche || reduit || sansObservateur;
}

/**
 * Deroule d'une scene : une etape apres l'autre (durees en millisecondes), en boucle, tant que
 * `joue` est vrai. En pause, l'etape en cours reste affichee ; a la reprise, elle recommence
 * sa duree. Statique : l'etape finale (etat lisible), sans minuterie.
 */
export function useDeroule(durees: readonly number[], joue: boolean, statique: boolean, etapeFinale: number): number {
  const [etape, setEtape] = useState(0);
  useEffect(() => {
    if (!joue || statique) return undefined;
    const t = window.setTimeout(() => setEtape((e) => (e + 1) % durees.length), durees[etape] ?? 1000);
    return () => window.clearTimeout(t);
  }, [joue, statique, etape, durees]);
  return statique ? etapeFinale : etape;
}

/**
 * La scene est dessinee a une taille fixe (largeur x hauteur) puis mise a l'echelle de son
 * cadre (transform: scale, variable --v-echelle) : la boite du cadre a un ratio fixe, rien ne
 * se decale. Mesure par ResizeObserver, ecrite directement dans le style (aucun rendu React).
 */
export function useEchelle(ref: RefObject<HTMLElement | null>, largeur: number) {
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const poser = (w: number) => {
      if (w > 0) el.style.setProperty('--v-echelle', String(Number((w / largeur).toFixed(4))));
    };
    poser(el.getBoundingClientRect().width);
    if (typeof window.ResizeObserver !== 'function') return undefined;
    const obs = new window.ResizeObserver((entrees) => {
      for (const e of entrees) poser(e.contentRect.width);
    });
    obs.observe(el);
    return () => obs.disconnect();
  }, [ref, largeur]);
}

import { useEffect, type RefObject } from 'react';
import { flushSync } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { avecTransitionDeVue, mouvementReduit } from '../../../lib/mouvement';
import { Ecrans } from '../../../routes/pages';

/**
 * Lot L6b (E10, V3) : transitions entre les pages de la vitrine (View Transitions).
 *
 * Un clic sur un lien interne de la vitrine vers une page publique (accueil, conditions
 * generales, mentions legales, essai, inscription, connexion) passe par le helper
 * `avecTransitionDeVue` (lib/mouvement) : l'ancienne page s'efface en glissant, la nouvelle
 * entre (vitrine.css, regles `data-transition-vitrine`, posees le temps de la transition
 * seulement : les transitions des ecrans de la plateforme ne changent pas).
 * Le code de la page visee est charge AVANT la capture de la nouvelle vue (sinon la
 * transition montrerait l'ecran de chargement), dans la limite d'une seconde.
 *
 * Navigateur sans View Transitions, "reduire les animations", clic avec une touche de
 * modification ou un autre bouton, lien externe ou nouvel onglet : rien n'est intercepte,
 * le lien se comporte normalement.
 */

const PAGES: Record<string, () => Promise<unknown>> = {
  '/': Ecrans.LandingPage.charger,
  '/cgu': Ecrans.CguPage.charger,
  '/conditions-generales': Ecrans.CguPage.charger,
  '/mentions-legales': Ecrans.MentionsLegalesPage.charger,
  '/essai': Ecrans.EssaiGratuitPage.charger,
  '/signup': Ecrans.SignupPage.charger,
  '/login': Ecrans.LoginPage.charger,
};

const ATTRIBUT = 'data-transition-vitrine';
const DUREE_MAX_MS = 1000;

function attendre(ms: number) {
  return new Promise<void>((r) => setTimeout(r, ms));
}

/** Attend que l'ecran de chargement des pages publiques (Suspense) ait disparu. */
async function nouvellePageRendue() {
  const debut = Date.now();
  while (Date.now() - debut < DUREE_MAX_MS) {
    await attendre(16);
    if (!document.querySelector('.min-h-dvh[role="status"]')) return;
  }
}

export function useTransitionsVitrine(racineRef: RefObject<HTMLElement | null>) {
  const navigate = useNavigate();

  useEffect(() => {
    const racine = racineRef.current;
    if (!racine) return undefined;
    const surClic = (e: MouseEvent) => {
      const doc = document as Document & { startViewTransition?: unknown };
      if (typeof doc.startViewTransition !== 'function' || mouvementReduit()) return;
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const lien = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
      if (!lien || (lien.target && lien.target !== '_self') || lien.hasAttribute('download')) return;
      const url = new URL(lien.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      const charger = PAGES[url.pathname];
      if (!charger) return;
      // Ancre de la page courante (#section) : defilement natif, pas de transition.
      const ici = new URL(window.location.href);
      if (url.pathname === ici.pathname && url.search === ici.search) return;
      e.preventDefault();
      const destination = `${url.pathname}${url.search}${url.hash}`;
      document.documentElement.setAttribute(ATTRIBUT, '');
      const fin = () => document.documentElement.removeAttribute(ATTRIBUT);
      // Le code de la page est demande tout de suite (il arrive pendant la sortie).
      const pret = Promise.race([charger().catch(() => undefined), attendre(DUREE_MAX_MS)]);
      avecTransitionDeVue(async () => {
        try {
          await pret;
          flushSync(() => navigate(destination));
          await nouvellePageRendue();
        } finally {
          window.setTimeout(fin, 600);
        }
      });
    };
    racine.addEventListener('click', surClic);
    return () => racine.removeEventListener('click', surClic);
  }, [racineRef, navigate]);
}

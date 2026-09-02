import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import Lenis from 'lenis';

/**
 * Sprint 12.5 T13 — Lenis smooth scroll global (subset marketing).
 *
 * Active scroll souple sur dashboards / dataroom / tables longues.
 * Respecte prefers-reduced-motion : aucune instance Lenis n'est créée si
 * l'utilisateur a demandé reduced-motion (préférence système).
 *
 * Pattern d'usage : appeler ce hook depuis un composant rendu À L'INTÉRIEUR
 * du Router (ex. <LenisController/> dans App.tsx) — il dépend de useLocation()
 * pour se réévaluer à CHAQUE changement de route.
 *
 * Fix 2026-06-26 (BUG navigation bloquée) — avant, le hook était monté une
 * seule fois (deps []) et n'évaluait le pathname qu'au mount : en SPA, Lenis
 * restait actif sur /chat et /chatbot (layout verrouillé à 100vh + overflow
 * interne) où son transform document gèle le viewport et tue les NavLink.
 * Désormais l'effet dépend de `pathname` : Lenis est DÉTRUIT en entrant sur
 * une route denylistée et RECRÉÉ propre en la quittant.
 *
 * Detach propre au unmount (HMR-safe).
 */

// Routes où Lenis NE DOIT PAS être actif :
//  - '/'        : la landing (MarketingApp) instancie son propre Lenis et le
//                 branche sur ScrollTrigger.update via window.__lenis ; une 2e
//                 instance casse le pin/scrub de StickyShowcase.
//  - '/chat'    : layout verrouillé à 100vh avec scroll interne (h-[calc(100vh-9rem)]).
//  - '/chatbot' : idem. Lenis applique son transform au document -> viewport
//                 figé, clics/NavLink morts (il fallait re-saisir l'URL).
const LENIS_DENYLIST = new Set<string>(['/', '/chat', '/chatbot']);

export function useLenisScroll(): void {
  const { pathname } = useLocation();

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (LENIS_DENYLIST.has(pathname)) return;

    const lenis = new Lenis({
      duration: 1.0,
      smoothWheel: true,
    });

    let rafId = 0;
    function raf(time: number) {
      lenis.raf(time);
      rafId = window.requestAnimationFrame(raf);
    }
    rafId = window.requestAnimationFrame(raf);

    return () => {
      window.cancelAnimationFrame(rafId);
      // destroy() retire le transform/les styles que Lenis pose sur <html> :
      // pas de transform résiduel quand on quitte une page scrollable.
      lenis.destroy();
    };
  }, [pathname]);
}

import { useEffect, useRef } from 'react';
import Lenis from 'lenis';
import { LENIS_CONFIG } from '../config/motion.js';

/**
 * v2 Flagship — orchestrateur smooth-scroll global (Lenis).
 *
 * Monté UNE SEULE FOIS au niveau App.jsx. Désactivé si
 * `prefers-reduced-motion`. Le wiring avec GSAP ScrollTrigger se fait
 * via `useScrollTrigger`, qui lit la même instance via window.__lenis.
 */
export function useLenis({ enabled = true } = {}) {
  const lenisRef = useRef(null);

  useEffect(() => {
    if (!enabled || typeof window === 'undefined') return undefined;

    const lenis = new Lenis(LENIS_CONFIG);
    lenisRef.current = lenis;
    // Exposé globalement pour les hooks/composants qui veulent invalider
    // ScrollTrigger sur scroll. Pattern officiel GSAP+Lenis.
    window.__lenis = lenis;

    let rafId;
    const raf = (time) => {
      lenis.raf(time);
      rafId = requestAnimationFrame(raf);
    };
    rafId = requestAnimationFrame(raf);

    return () => {
      cancelAnimationFrame(rafId);
      lenis.destroy();
      if (window.__lenis === lenis) delete window.__lenis;
    };
  }, [enabled]);

  return lenisRef;
}

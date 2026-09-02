import { useEffect } from 'react';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

/**
 * v2 Flagship — bridge GSAP ScrollTrigger ↔ Lenis.
 *
 * Monté APRÈS useLenis dans App.jsx. Recâble ScrollTrigger.update sur
 * le tick Lenis (sinon les triggers tirent depuis le scroll natif qui
 * n'avance jamais pendant un smooth scroll → triggers cassés).
 *
 * Pattern officiel : https://lenis.darkroom.engineering/#gsap-scrolltrigger
 */
export function useScrollTrigger({ enabled = true } = {}) {
  useEffect(() => {
    if (!enabled || typeof window === 'undefined') return undefined;

    const lenis = window.__lenis;
    if (!lenis) {
      // Pas de Lenis (reduced-motion ou échec d'init) -> ScrollTrigger
      // fonctionne en mode natif sans wiring.
      return undefined;
    }

    const onScroll = () => ScrollTrigger.update();
    lenis.on('scroll', onScroll);
    return () => lenis.off('scroll', onScroll);
  }, [enabled]);
}

export { gsap, ScrollTrigger };

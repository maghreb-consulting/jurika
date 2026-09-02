import { useEffect } from 'react';
import { useReducedMotion } from './useReducedMotion.js';

/**
 * v2 Flagship — IntersectionObserver helper pour révélations scroll-driven.
 *
 * Scanne `[data-reveal]` dans le scope, ajoute classe `is-in` quand
 * l'élément entre dans le viewport (threshold 15 %).
 *
 * Si reduced-motion : ajoute `is-in` immédiatement à tous (statique).
 */
export function useScrollReveal({ scope = null, threshold = 0.15 } = {}) {
  const reduced = useReducedMotion();

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const root = scope?.current || document;
    const els = Array.from(root.querySelectorAll('[data-reveal]'));
    if (!els.length) return undefined;

    if (reduced) {
      els.forEach((el) => el.classList.add('is-in'));
      return undefined;
    }

    const obs = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in');
            obs.unobserve(entry.target);
          }
        }
      },
      { threshold, rootMargin: '0px 0px -10% 0px' }
    );

    els.forEach((el) => obs.observe(el));
    return () => obs.disconnect();
  }, [scope, threshold, reduced]);
}

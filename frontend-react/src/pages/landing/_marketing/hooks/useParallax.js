import { useEffect, useRef } from 'react';
import { useReducedMotion } from './useReducedMotion.js';

/**
 * v2 Flagship — parallax scroll-driven multi-layers.
 *
 * Scanne le DOM pour `[data-parallax]` (value = speed factor) et translate
 * sur Y selon window.scrollY * speed. Compatible Lenis (lit la même
 * source de vérité scrollY).
 *
 * Convention :
 *  - speed > 0 = couche descend plus vite que le scroll (foreground +0.15x)
 *  - speed < 0 = couche descend plus lentement (background -0.5x)
 *
 * Si `prefers-reduced-motion` : no-op (couches statiques).
 */
export function useParallax({ scope = null } = {}) {
  const reduced = useReducedMotion();
  const rafRef = useRef(null);

  useEffect(() => {
    if (reduced || typeof window === 'undefined') return undefined;

    const root = scope?.current || document;
    const els = Array.from(root.querySelectorAll('[data-parallax]'));
    if (!els.length) return undefined;

    const meta = els.map((el) => ({
      el,
      speed: parseFloat(el.dataset.parallax) || 0,
    }));

    let scheduled = false;
    const update = () => {
      const y = window.scrollY;
      for (const m of meta) {
        // Couches background : -0.5 = monte de 0.5*y quand on scroll
        // Couches foreground : +0.15 = descend de 0.15*y
        const ty = -y * m.speed;
        m.el.style.transform = `translate3d(0, ${ty}px, 0)`;
      }
      scheduled = false;
    };

    const onScroll = () => {
      if (scheduled) return;
      scheduled = true;
      rafRef.current = requestAnimationFrame(update);
    };

    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [scope, reduced]);
}

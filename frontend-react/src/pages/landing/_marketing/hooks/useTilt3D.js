import { useEffect } from 'react';
import { useReducedMotion } from './useReducedMotion.js';

/**
 * v2 Flagship — tilt 3D mousemove subtil sur cards.
 *
 * Pour chaque `[data-tilt]` dans le scope, écoute mousemove et applique
 * rotateX/Y proportionnel au curseur (max ±maxAngle degrés).
 *
 * Si reduced-motion : no-op. Si hover non supporté (mobile) : no-op.
 *
 * Layered with useParallax (pas de conflit, on travaille sur rotate
 * pas translate).
 */
export function useTilt3D({ scope, maxAngle = 6, glare = true } = {}) {
  const reduced = useReducedMotion();

  useEffect(() => {
    if (reduced || typeof window === 'undefined') return undefined;
    // Skip si hover non supporté (touch-only)
    if (!window.matchMedia || !window.matchMedia('(hover: hover)').matches) return undefined;

    const root = scope?.current || document;
    const els = Array.from(root.querySelectorAll('[data-tilt]'));
    if (!els.length) return undefined;

    const handlers = els.map((el) => {
      el.style.transformStyle = 'preserve-3d';
      el.style.willChange = 'transform';

      const onMove = (e) => {
        const r = el.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width; // 0..1
        const y = (e.clientY - r.top) / r.height;
        const rx = (0.5 - y) * maxAngle;
        const ry = (x - 0.5) * maxAngle;
        el.style.transform = `perspective(1200px) rotateX(${rx}deg) rotateY(${ry}deg) translateY(-4px)`;
        if (glare) {
          el.style.setProperty('--mx', `${x * 100}%`);
          el.style.setProperty('--my', `${y * 100}%`);
        }
      };
      const onLeave = () => {
        el.style.transform = 'perspective(1200px) rotateX(0) rotateY(0) translateY(0)';
      };

      el.addEventListener('mousemove', onMove);
      el.addEventListener('mouseleave', onLeave);
      return () => {
        el.removeEventListener('mousemove', onMove);
        el.removeEventListener('mouseleave', onLeave);
      };
    });

    return () => handlers.forEach((off) => off());
  }, [scope, maxAngle, glare, reduced]);
}

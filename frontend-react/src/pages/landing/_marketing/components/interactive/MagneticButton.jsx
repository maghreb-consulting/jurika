import { useEffect, useRef } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * v2 Flagship — bouton magnétique : suit légèrement le curseur (max 8px).
 *
 * Délègue toutes les props HTML au <button>. `forwardRef` non requis ici
 * car on garde un ref interne pour le DOM. Si reduced-motion, no-op.
 *
 * Force d'attraction : 0.25 (subtile, signature Linear).
 */
export function MagneticButton({ children, className = '', strength = 0.25, ...props }) {
  const reduced = useReducedMotion();
  const ref = useRef(null);

  useEffect(() => {
    if (reduced) return undefined;
    const el = ref.current;
    if (!el) return undefined;

    const onMove = (e) => {
      const r = el.getBoundingClientRect();
      const dx = (e.clientX - (r.left + r.width / 2)) * strength;
      const dy = (e.clientY - (r.top + r.height / 2)) * strength;
      el.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;
    };
    const onLeave = () => {
      el.style.transform = 'translate3d(0, 0, 0)';
    };

    el.addEventListener('mousemove', onMove);
    el.addEventListener('mouseleave', onLeave);
    return () => {
      el.removeEventListener('mousemove', onMove);
      el.removeEventListener('mouseleave', onLeave);
    };
  }, [reduced, strength]);

  return (
    <button ref={ref} className={`magnetic-btn ${className}`} {...props}>
      {children}
    </button>
  );
}

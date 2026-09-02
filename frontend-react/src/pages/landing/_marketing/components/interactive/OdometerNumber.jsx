import { useEffect, useRef, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * v2 Flagship — Odomètre : un chiffre qui défile jusqu'à sa valeur finale.
 *
 * Utilise requestAnimationFrame + easing exponential out. Si reduced-motion,
 * affiche la valeur cible direct. Format optionnel via `format` (fonction).
 */
export function OdometerNumber({ to, durationMs = 1200, startDelayMs = 0, format = (v) => Math.round(v), suffix = '' }) {
  const reduced = useReducedMotion();
  const [value, setValue] = useState(reduced ? to : 0);
  const rafRef = useRef(null);

  useEffect(() => {
    if (reduced) {
      setValue(to);
      return undefined;
    }
    let startTime = null;
    const timer = setTimeout(() => {
      const tick = (now) => {
        if (startTime === null) startTime = now;
        const t = Math.min(1, (now - startTime) / durationMs);
        const eased = 1 - Math.pow(1 - t, 3); // ease-out-cubic
        setValue(eased * to);
        if (t < 1) rafRef.current = requestAnimationFrame(tick);
      };
      rafRef.current = requestAnimationFrame(tick);
    }, startDelayMs);
    return () => {
      clearTimeout(timer);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [to, durationMs, startDelayMs, reduced]);

  return <strong className="odometer">{format(value)}{suffix}</strong>;
}

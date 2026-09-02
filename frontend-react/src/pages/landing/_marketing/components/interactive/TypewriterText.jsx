import { useEffect, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';
import { HERO_TIMING } from '../../config/motion.js';

/**
 * v2 Flagship — Typewriter qui tape un texte caractère par caractère.
 *
 * Si reduced-motion : affiche tout instantanément. Sinon : intervalle
 * 40ms/char (configurable via HERO_TIMING.H1_CHAR_INTERVAL_MS).
 */
export function TypewriterText({ text, startDelayMs = 0, intervalMs = HERO_TIMING.H1_CHAR_INTERVAL_MS, onDone, className = '' }) {
  const reduced = useReducedMotion();
  const [visible, setVisible] = useState(reduced ? text : '');

  useEffect(() => {
    if (reduced) {
      setVisible(text);
      if (onDone) onDone();
      return undefined;
    }
    setVisible('');
    let i = 0;
    let intervalId;
    const startTimer = setTimeout(() => {
      intervalId = setInterval(() => {
        i += 1;
        setVisible(text.slice(0, i));
        if (i >= text.length) {
          clearInterval(intervalId);
          if (onDone) onDone();
        }
      }, intervalMs);
    }, startDelayMs);
    return () => {
      clearTimeout(startTimer);
      if (intervalId) clearInterval(intervalId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, reduced]);

  return (
    <span className={`typewriter ${visible.length === text.length ? 'is-done' : ''} ${className}`}>
      {visible}
      {!reduced && visible.length < text.length && <span className="typewriter-caret" aria-hidden="true">|</span>}
    </span>
  );
}

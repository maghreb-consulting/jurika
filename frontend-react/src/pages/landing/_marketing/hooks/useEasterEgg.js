import { useEffect, useState } from 'react';

const TARGET_SEQUENCE = 'JURIKA';
const SESSION_FLAG = 'jurika.easter-egg.fired';

/**
 * v2 Flagship — "Code d'éveil" : détecte la séquence clavier JURIKA.
 *
 * Trigger une seule fois par session (sessionStorage). Le composant qui
 * consomme déclenche pluie de sceaux + logo pulse + toast.
 *
 * @returns {boolean} true quand la séquence est complétée
 */
export function useEasterEgg() {
  const [fired, setFired] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;

    // Si déjà déclenché cette session, ne pas re-armer
    try {
      if (sessionStorage.getItem(SESSION_FLAG) === '1') return undefined;
    } catch {
      // sessionStorage indisponible (incognito strict) — on continue
    }

    let buffer = '';
    const onKey = (e) => {
      // Ignore les modificateurs (Ctrl, Meta, Alt) pour éviter les
      // collisions avec raccourcis browser
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      if (!e.key || e.key.length !== 1) return;

      buffer = (buffer + e.key.toUpperCase()).slice(-TARGET_SEQUENCE.length);

      if (buffer === TARGET_SEQUENCE) {
        try { sessionStorage.setItem(SESSION_FLAG, '1'); } catch { /* noop */ }
        setFired(true);
        // Auto-reset 6s plus tard pour permettre au composant de redémonter
        // proprement si jamais le user veut re-trigger (sessionStorage le
        // bloquera de toute façon).
        setTimeout(() => setFired(false), 6000);
      }
    };

    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  return fired;
}

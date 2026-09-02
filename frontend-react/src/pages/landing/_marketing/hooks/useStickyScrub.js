import { useEffect } from 'react';
import { useReducedMotion } from './useReducedMotion.js';

/**
 * v2 Flagship — sticky-scroll scrub avec GSAP ScrollTrigger.
 *
 * Pin une section pendant `stagesCount × 1` viewport, et avance un index
 * `stage` (0..stagesCount-1) en fonction de la progression du scroll.
 * Le composant parent met à jour visuellement les frames via classes
 * `[data-frame]` ou `[data-step]` synchronisées.
 *
 * Si reduced-motion : pas de pin, les sections s'affichent naturellement.
 *
 * @param {object} opts
 * @param {React.RefObject} opts.scope - container à pin
 * @param {number} opts.stages - nombre d'étapes (= multiplicateur viewport)
 * @param {(index: number) => void} opts.onStageChange - callback à chaque
 *        transition de stage
 */
export function useStickyScrub({ scope, stages = 5, onStageChange }) {
  const reduced = useReducedMotion();

  useEffect(() => {
    if (reduced || !scope?.current || typeof window === 'undefined') return undefined;

    let ScrollTrigger = null;
    let stInstance = null;
    let cancelled = false;
    let lastStage = -1;

    // Lazy import GSAP pour ne pas casser l'env test
    import('gsap').then(({ gsap }) => import('gsap/ScrollTrigger').then((mod) => {
      if (cancelled) return;
      ScrollTrigger = mod.ScrollTrigger;
      gsap.registerPlugin(ScrollTrigger);

      const el = scope.current;
      if (!el) return;

      stInstance = ScrollTrigger.create({
        trigger: el,
        start: 'top top',
        // Sprint 12.6 -- pin tres court : 0.25vh par etape (au lieu de 1.0).
        // 5 etapes x 0.25vh = 1.25 viewport au total au lieu de 5.
        // Le scroll "passe" la section en 1 mouvement de molette continu,
        // anim saine, jamais d'impression de blocage.
        end: () => `+=${stages * window.innerHeight * 0.25}`,
        pin: true,
        pinSpacing: true,
        scrub: 0.3,
        onUpdate: (self) => {
          const idx = Math.min(stages - 1, Math.floor(self.progress * stages));
          if (idx !== lastStage) {
            lastStage = idx;
            if (onStageChange) onStageChange(idx);
          }
        },
      });
    })).catch(() => {
      /* lazy import failed (offline / build-only) — fallback no-pin */
    });

    return () => {
      cancelled = true;
      if (stInstance && stInstance.kill) stInstance.kill();
    };
  }, [scope, stages, onStageChange, reduced]);
}

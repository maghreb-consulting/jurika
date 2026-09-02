import { useEffect, useRef } from 'react';
import type { DirtyGetter } from './useWorkflow';

/**
 * Sprint 2026-06-21 (Cowork P1) — Helper d'enregistrement d'un dirty getter
 * pour une etape de workflow.
 *
 * Usage dans un composant d'etape :
 *
 *   useStepAutosave(stepNumber, () => buildCurrentPayload(), registerDirty);
 *
 * - Au montage, enregistre le getter pour l'etape.
 * - Au demontage, le desinscrit.
 * - Le getter est toujours appele avec la DERNIERE version stable (via ref),
 *   donc on n'a pas besoin de re-register a chaque render.
 *
 * Cote useWorkflow, {@code flushDraft()} est invoque AVANT toute navigation
 * (goToStep/goPrev/goNext) ; le payload retourne par le getter est alors
 * persiste cote backend (saveDraft). L'employe ne perd JAMAIS les champs
 * deja saisis meme s'il quitte sans cliquer "Valider".
 */
export function useStepAutosave(
  step: number,
  getPayload: DirtyGetter,
  registerDirty: ((step: number, getter: DirtyGetter) => () => void) | undefined,
): void {
  // Ref vers la derniere implementation du getter : evite de re-register a
  // chaque render (la fonction passee par le parent change a chaque render).
  const getterRef = useRef<DirtyGetter>(getPayload);
  getterRef.current = getPayload;

  useEffect(() => {
    if (!registerDirty) return;
    const stable: DirtyGetter = () => getterRef.current();
    const unregister = registerDirty(step, stable);
    return () => {
      unregister();
    };
  }, [step, registerDirty]);
}

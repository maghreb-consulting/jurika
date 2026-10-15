import { useEffect, useRef, useLayoutEffect } from 'react';
import type { DirtyGetter } from './useWorkflow';

/** Options de desinscription d'un getter (lot L7, E2). */
export interface DesinscriptionOptions {
  /** Envoie la saisie non encore enregistree avant de desinscrire (demontage de l'etape). */
  flush?: boolean;
}

export type RegisterDirty = (
  step: number,
  getter: DirtyGetter,
) => (options?: DesinscriptionOptions) => void;

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
 * Lot L7 (E2) : le moteur lit ce getter en continu et enregistre la saisie peu
 * apres la frappe, a la navigation, quand la page se masque ou se ferme, et au
 * DEMONTAGE de l'etape (bouton "Quitter", changement de page) : c'est le seul
 * cas ou la desinscription demande un dernier envoi. Un changement d'etape au
 * sein d'une meme page (getter enregistre sous `viewStep`) ne le demande pas :
 * le getter rend deja la saisie de la NOUVELLE etape, et la navigation a envoye
 * l'ancienne avant de basculer.
 */
export function useStepAutosave(
  step: number,
  getPayload: DirtyGetter,
  registerDirty: RegisterDirty | undefined,
): void {
  // Ref vers la derniere implementation du getter : evite de re-register a
  // chaque render (la fonction passee par le parent change a chaque render).
  const getterRef = useRef<DirtyGetter>(getPayload);
  // Mise a jour apres le rendu (lot L6 : un ref ne s'ecrit pas pendant le rendu).
  useLayoutEffect(() => {
    getterRef.current = getPayload;
  });

  // Declare AVANT l'inscription : au demontage, React nettoie les effets dans
  // l'ordre de declaration, donc le drapeau est leve quand l'inscription se
  // nettoie. Il ne l'est pas lors d'un simple changement d'etape.
  const demontageRef = useRef(false);
  useEffect(() => {
    demontageRef.current = false;
    return () => {
      demontageRef.current = true;
    };
  }, []);

  useEffect(() => {
    if (!registerDirty) return;
    const stable: DirtyGetter = () => getterRef.current();
    const unregister = registerDirty(step, stable);
    return () => {
      unregister({ flush: demontageRef.current });
    };
  }, [step, registerDirty]);
}

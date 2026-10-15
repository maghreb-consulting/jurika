import { FriseEtapes } from './FriseEtapes';

export interface RoadmapStep {
  number: number;
  label: string;
  blocked?: boolean;
}

interface Props {
  /** HWM serveur -- etape la plus avancee atteinte (steps <= currentStep sont validees/visitables). */
  currentStep: number;
  steps: RoadmapStep[];
  blocked?: boolean;
  /**
   * Etape actuellement AFFICHEE par l'UI. Si non fournie, defaut = currentStep.
   * Permet a la frise de mettre en relief la position UI distincte de la HWM
   * lorsque le user navigue en arriere.
   */
  viewStep?: number;
  /**
   * Callback de navigation. Si fourni, les etapes de numero <= currentStep deviennent
   * cliquables. Les etapes futures (> currentStep) restent verrouillees -- pas de saut
   * non valide.
   */
  onNavigate?: (step: number) => void;
}

/** Lot L6b (E5) : rendu delegue a la frise commune (FriseEtapes). */
export function WorkflowRoadmap({ currentStep, steps, blocked, viewStep, onNavigate }: Props) {
  return (
    <FriseEtapes
      etapes={steps.map((s) => ({ numero: s.number, libelle: s.label }))}
      plusAvancee={currentStep}
      affichee={viewStep}
      bloquee={blocked}
      onNavigate={onNavigate}
    />
  );
}

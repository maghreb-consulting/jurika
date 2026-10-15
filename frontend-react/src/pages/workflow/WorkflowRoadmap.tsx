import { FriseEtapes } from '../../components/workflow/FriseEtapes';
import { ETAPES_CREATION, libellesEtapes } from './aidesParcours';

const LIBELLES_CREATION = libellesEtapes(ETAPES_CREATION);

interface Props {
  /** HWM serveur -- etape la plus avancee atteinte. */
  currentStep: number;
  totalSteps: number;
  /** Etape actuellement AFFICHEE. Default = currentStep. */
  viewStep?: number;
  /** Si fourni, rend cliquables les etapes 1..currentStep. */
  onNavigate?: (step: number) => void;
  /** Libelles d'etapes optionnels (defaut : etapes de la creation). */
  labels?: Record<number, string>;
}

/** Lot L6b (E5) : rendu delegue a la frise commune (FriseEtapes). */
export function WorkflowRoadmap({ currentStep, totalSteps, viewStep, onNavigate, labels }: Props) {
  const etapes = Array.from({ length: totalSteps }, (_, i) => i + 1).map((n) => ({
    numero: n,
    libelle: labels?.[n] ?? LIBELLES_CREATION[n] ?? `Étape ${n}`,
  }));
  return <FriseEtapes etapes={etapes} plusAvancee={currentStep} affichee={viewStep} onNavigate={onNavigate} />;
}

import { Check } from 'lucide-react';

interface SignupStepperProps {
  currentStep: 0 | 1 | 2 | 3 | 4 | 5 | 6;
  // Simplification inscription (2026-07-13) : Cabinet + Admin fusionnes en une
  // seule etape "Profil". Le wizard passe de 8 a 7 etapes.
  // Etapes : Forfait / Profil / Securite / 2FA / Recap / Paiement / Succes.
}

/**
 * Stepper horizontal pour le wizard signup self-service.
 *
 * <p>Simplification 2026-07-13 : formulaire unique — les etapes Cabinet et
 * Admin sont fusionnees en "Profil". Le skip-plan reste desactive (chaque
 * inscription montre l'etape Forfait au moins en confirmation).
 */
export function SignupStepper({ currentStep }: SignupStepperProps) {
  const steps = [
    { number: 0 as const, label: 'Forfait' },
    { number: 1 as const, label: 'Profil' },
    { number: 2 as const, label: 'Sécurité' },
    { number: 3 as const, label: 'Authentification' },
    { number: 4 as const, label: 'Récapitulatif' },
    { number: 5 as const, label: 'Paiement' },
    { number: 6 as const, label: 'Terminé' },
  ];

  return (
    <ol aria-label="Étapes de l’inscription" className="mb-6 flex items-start justify-center overflow-x-auto px-2 pb-1" data-testid="signup-stepper">
      {steps.map((step, index) => {
        const completed = step.number < currentStep;
        const active = step.number === currentStep;
        return (
          <li key={step.number} className="flex items-start" aria-current={active ? 'step' : undefined}>
            <div className="flex flex-col items-center">
              <div
                className={[
                  'mb-1.5 flex h-8 w-8 items-center justify-center rounded-full transition-colors duration-150',
                  completed ? 'bg-success' : '',
                  active ? 'bg-accent' : '',
                  !completed && !active ? 'border border-border-input bg-bg-raised' : '',
                ].join(' ')}
              >
                {completed ? (
                  <Check className="h-4 w-4 text-success-fg" aria-label="faite" />
                ) : (
                  <span
                    className={`text-sm font-bold ${
                      active ? 'text-accent-fg' : 'text-fg-muted'
                    }`}
                  >
                    {step.number + 1}
                  </span>
                )}
              </div>
              <span
                className={`whitespace-nowrap text-center text-xs ${
                  active ? 'font-semibold text-heading' : 'text-fg-muted'
                }`}
              >
                {step.label}
              </span>
            </div>

            {index < steps.length - 1 && (
              <div
                aria-hidden="true"
                className={`mx-1.5 mt-4 h-px w-5 sm:mx-2 sm:w-8 ${
                  completed ? 'bg-success' : 'bg-border-input'
                }`}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}

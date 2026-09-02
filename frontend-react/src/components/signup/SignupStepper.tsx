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
    { number: 2 as const, label: 'Securite' },
    { number: 3 as const, label: '2FA' },
    { number: 4 as const, label: 'Recap' },
    { number: 5 as const, label: 'Paiement' },
    { number: 6 as const, label: 'Succes' },
  ];

  return (
    <div className="mb-12 flex items-center justify-center overflow-x-auto px-2 pb-1" data-testid="signup-stepper">
      {steps.map((step, index) => {
        const completed = step.number < currentStep;
        const active = step.number === currentStep;
        return (
          <div key={step.number} className="flex items-center">
            <div className="flex flex-col items-center">
              <div
                className={[
                  'mb-2 flex h-10 w-10 items-center justify-center rounded-full transition-all',
                  completed ? 'bg-success' : '',
                  active ? 'bg-accent' : '',
                  !completed && !active ? 'bg-border' : '',
                ].join(' ')}
              >
                {completed ? (
                  <Check className="h-5 w-5 text-bg" />
                ) : (
                  <span
                    className={`text-sm font-bold ${
                      active ? 'text-bg' : 'text-fg-subtle'
                    }`}
                  >
                    {step.number + 1}
                  </span>
                )}
              </div>
              <span
                className={`whitespace-nowrap text-center text-xs sm:text-sm ${
                  active ? 'font-semibold text-accent' : 'text-fg-subtle'
                }`}
              >
                {step.label}
              </span>
            </div>

            {index < steps.length - 1 && (
              <div
                className={`mx-2 sm:mx-3 mb-8 h-0.5 w-6 sm:w-10 ${
                  completed ? 'bg-success' : 'bg-border'
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

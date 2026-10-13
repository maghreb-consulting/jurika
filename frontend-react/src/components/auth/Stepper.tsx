import { Check } from 'lucide-react';

interface StepperProps {
  currentStep: 1 | 2 | 3;
}

/**
 * Indicateur des trois etapes de la connexion (code du workspace, identifiants,
 * double authentification). Etape en cours : or ; etapes faites : vert.
 */
export function Stepper({ currentStep }: StepperProps) {
  const steps = [
    { number: 1, label: 'Code du cabinet' },
    { number: 2, label: 'Identifiants' },
    { number: 3, label: 'Double authentification' },
  ] as const;

  return (
    <ol aria-label="Étapes de la connexion" className="mb-10 flex items-center justify-center gap-2">
      {steps.map((step, index) => {
        const completed = step.number < currentStep;
        const active = step.number === currentStep;
        return (
          <li key={step.number} className="flex items-center" aria-current={active ? 'step' : undefined}>
            <div className="flex items-center gap-2">
              <div
                className={[
                  'flex h-8 w-8 items-center justify-center rounded-full transition-all',
                  completed ? 'bg-success' : '',
                  active ? 'bg-accent' : '',
                  !completed && !active ? 'bg-border' : '',
                ].join(' ')}
              >
                {completed ? (
                  <Check className="h-5 w-5 text-bg-raised" aria-label="faite" />
                ) : (
                  <span
                    className={`text-sm font-semibold ${
                      active ? 'text-bg-raised' : 'text-fg-subtle'
                    }`}
                  >
                    {step.number}
                  </span>
                )}
              </div>
              <span
                className={`hidden text-sm sm:inline ${
                  active ? 'font-semibold text-accent' : 'text-fg-subtle'
                }`}
              >
                {step.label}
              </span>
            </div>

            {index < steps.length - 1 && (
              <div className="mx-3 text-fg-subtle" aria-hidden="true">→</div>
            )}
          </li>
        );
      })}
    </ol>
  );
}

import { Check } from 'lucide-react';

interface StepperProps {
  currentStep: 1 | 2 | 3;
}

/**
 * 3-step horizontal indicator for login flow.
 * Maquette: blue active, green completed, gray pending.
 */
export function Stepper({ currentStep }: StepperProps) {
  const steps = [
    { number: 1, label: 'Code workspace' },
    { number: 2, label: 'Identifiants' },
    { number: 3, label: 'Verification 2FA' },
  ] as const;

  return (
    <div className="mb-10 flex items-center justify-center gap-2">
      {steps.map((step, index) => {
        const completed = step.number < currentStep;
        const active = step.number === currentStep;
        return (
          <div key={step.number} className="flex items-center">
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
                  <Check className="h-5 w-5 text-bg-raised" />
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
              <div className="mx-3 text-fg-subtle">→</div>
            )}
          </div>
        );
      })}
    </div>
  );
}

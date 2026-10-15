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
    <ol aria-label="Étapes de la connexion" className="mb-8 flex items-center justify-center gap-1">
      {steps.map((step, index) => {
        const completed = step.number < currentStep;
        const active = step.number === currentStep;
        return (
          <li key={step.number} className="flex items-center" aria-current={active ? 'step' : undefined}>
            <div className="flex items-center gap-2">
              <div
                className={[
                  'flex h-7 w-7 items-center justify-center rounded-full transition-colors duration-150',
                  completed ? 'bg-success' : '',
                  active ? 'bg-accent' : '',
                  !completed && !active ? 'border border-border-input bg-bg-overlay' : '',
                ].join(' ')}
              >
                {completed ? (
                  <Check className="h-4 w-4 text-success-fg" aria-label="faite" />
                ) : (
                  <span
                    className={`text-sm font-semibold ${
                      active ? 'text-accent-fg' : 'text-fg-muted'
                    }`}
                  >
                    {step.number}
                  </span>
                )}
              </div>
              <span
                // Libelle visible pour l'etape en cours seulement (la carte est etroite) ;
                // les autres restent lus par les lecteurs d'ecran.
                className={active ? 'text-xs font-semibold text-heading' : 'sr-only'}
              >
                {step.label}
              </span>
            </div>

            {index < steps.length - 1 && (
              <div className="mx-2 h-px w-6 bg-border-input" aria-hidden="true" />
            )}
          </li>
        );
      })}
    </ol>
  );
}

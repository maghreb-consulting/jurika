import { Check, Lock } from 'lucide-react';
import { STEP_LABELS_CREATION } from '../../types/workflow';

interface Props {
  /** HWM serveur — etape la plus avancee atteinte. */
  currentStep: number;
  totalSteps: number;
  /** Etape actuellement AFFICHEE. Default = currentStep. */
  viewStep?: number;
  /** Si fourni, rend cliquables les etapes 1..currentStep. */
  onNavigate?: (step: number) => void;
  /** Libelles d etapes optionnels (defaut = labels CREATION). */
  labels?: Record<number, string>;
}

export function WorkflowRoadmap({
  currentStep,
  totalSteps,
  viewStep,
  onNavigate,
  labels,
}: Props) {
  const view = viewStep ?? currentStep;
  const steps = Array.from({ length: totalSteps }, (_, i) => i + 1);
  const labelFor = (n: number) =>
    labels?.[n] ?? STEP_LABELS_CREATION[n] ?? `Etape ${n}`;
  // RG-UX : pourcentage de progression = (etapes validees) / total.
  // Une etape est validee si son numero < currentStep (HWM). On laisse 100% si
  // currentStep > totalSteps.
  const validatedCount = Math.min(currentStep - 1, totalSteps);
  const percent = Math.round((Math.max(0, validatedCount) / totalSteps) * 100);
  return (
    <nav className="rounded-2xl border border-border bg-bg-raised p-4" aria-label="Progression du workflow">
      <ol className="flex items-center gap-1 overflow-x-auto">
        {steps.map((s) => {
          const validated = s < currentStep;
          const reached = s <= currentStep;
          const isFuture = s > currentStep;
          const isView = s === view;
          const isHwm = s === currentStep && !isView;
          const clickable = reached && !!onNavigate && !isView;

          const bubbleClass = validated
            ? 'bg-emerald-600 text-bg-raised'
            : isView
            ? 'bg-accent text-bg-raised ring-4 ring-indigo-100'
            : isHwm
            ? 'bg-indigo-100 text-accent ring-2 ring-accent/40'
            : 'bg-bg-overlay text-fg-subtle';
          const cursorClass = clickable
            ? 'cursor-pointer hover:scale-105 hover:bg-bg-subtle hover:ring-2 hover:ring-accent/30'
            : isFuture
            ? 'cursor-not-allowed opacity-70'
            : '';
          const tooltip = isFuture
            ? `Etape ${s} verrouillee — Completez l'etape precedente`
            : clickable
            ? `Revenir a l'etape ${s}`
            : undefined;

          return (
            <li key={s} className="flex flex-1 items-center gap-2 min-w-[120px]">
              <button
                type="button"
                onClick={clickable ? () => onNavigate?.(s) : undefined}
                disabled={!clickable}
                title={tooltip}
                aria-label={labelFor(s)}
                aria-current={isView ? 'step' : undefined}
                className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-semibold transition ${bubbleClass} ${cursorClass}`}
              >
                {validated ? (
                  <Check className="h-4 w-4" aria-hidden="true" />
                ) : isFuture ? (
                  <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                ) : (
                  s
                )}
              </button>
              <div className="flex-1">
                <p
                  className={`text-xs font-medium ${
                    isView
                      ? 'text-accent'
                      : validated
                      ? 'text-emerald-700'
                      : isHwm
                      ? 'text-accent'
                      : 'text-fg-muted'
                  }`}
                >
                  {labelFor(s)}
                </p>
              </div>
              {s < totalSteps && (
                <div
                  className={`h-0.5 flex-1 ${validated ? 'bg-emerald-300' : 'bg-bg-overlay'}`}
                />
              )}
            </li>
          );
        })}
      </ol>
      {/* Barre de progression globale + compteur */}
      <div className="mt-3 flex items-center gap-3">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-bg-overlay">
          <div
            className="h-full rounded-full bg-accent transition-all duration-300"
            style={{ width: `${percent}%` }}
            aria-hidden="true"
          />
        </div>
        <span className="text-xs font-medium text-fg-subtle">
          {Math.max(0, validatedCount)}/{totalSteps} etapes
        </span>
      </div>
    </nav>
  );
}

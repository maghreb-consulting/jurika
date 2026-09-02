import { Check, Lock } from 'lucide-react';

export interface RoadmapStep {
  number: number;
  label: string;
  blocked?: boolean;
}

interface Props {
  /** HWM serveur — etape la plus avancee atteinte (steps <= currentStep sont validees/visitables). */
  currentStep: number;
  steps: RoadmapStep[];
  blocked?: boolean;
  /**
   * Etape actuellement AFFICHEE par l'UI. Si non fournie, defaut = currentStep.
   * Permet a la roadmap de mettre en relief la position UI distincte de la HWM
   * lorsque le user navigue en arriere.
   */
  viewStep?: number;
  /**
   * Callback de navigation. Si fourni, les etapes de numero ≤ currentStep deviennent
   * cliquables. Les etapes futures (> currentStep) restent verrouillees -- pas de saut
   * non valide.
   */
  onNavigate?: (step: number) => void;
}

export function WorkflowRoadmap({ currentStep, steps, blocked, viewStep, onNavigate }: Props) {
  const view = viewStep ?? currentStep;
  const total = steps.length;
  const validatedCount = Math.max(0, Math.min(currentStep - 1, total));
  const percent = total > 0 ? Math.round((validatedCount / total) * 100) : 0;
  return (
    <nav className="rounded-2xl border border-border bg-bg-raised p-4" aria-label="Progression du workflow">
      <ol className="flex items-center gap-1 overflow-x-auto">
        {steps.map((s, idx) => {
          // Une etape est "validee" si sa position est strictement avant la HWM
          // (la HWM elle-meme est l'etape en cours cote serveur, donc non validee tant
          // qu'on ne l'a pas soumise une derniere fois -- mais on l'affiche comme atteinte).
          const validated = s.number < currentStep;
          const reached = s.number <= currentStep;
          const isFuture = s.number > currentStep;
          const isView = s.number === view;
          const isHwm = s.number === currentStep && !isView;
          const isBlocked = isView && blocked;

          const clickable = reached && !!onNavigate && !isView;
          const baseBubble =
            'flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-all duration-300';
          const bubbleClass = validated
            ? 'bg-emerald-600 text-bg-raised'
            : isBlocked
            ? 'bg-rose-600 text-bg-raised ring-4 ring-rose-100 scale-110 motion-safe:animate-pulse'
            : isView
            ? 'bg-accent text-bg-raised ring-4 ring-accent/30 scale-110 motion-safe:animate-pulse'
            : isHwm
            ? 'bg-indigo-100 text-accent ring-2 ring-accent/40'
            : 'bg-bg-overlay text-fg-subtle';
          const cursorClass = clickable
            ? 'cursor-pointer hover:scale-105 hover:ring-2 hover:ring-accent/30'
            : isFuture
            ? 'cursor-not-allowed opacity-70'
            : '';

          const labelClass = isBlocked
            ? 'text-danger'
            : isView
            ? 'text-accent font-semibold'
            : validated
            ? 'text-emerald-700'
            : isHwm
            ? 'text-accent'
            : 'text-fg-muted';

          const tooltip = isFuture
            ? `Etape ${s.number} verrouillee (validez d'abord les precedentes)`
            : isView
            ? `Etape ${s.number} affichee`
            : clickable
            ? `Revenir a l'etape ${s.number}`
            : undefined;

          return (
            <li key={s.number} className="flex flex-1 items-center gap-2 min-w-[150px]">
              <button
                type="button"
                onClick={clickable ? () => onNavigate?.(s.number) : undefined}
                disabled={!clickable}
                title={tooltip}
                aria-label={tooltip ?? s.label}
                aria-current={isView ? 'step' : undefined}
                className={`${baseBubble} ${bubbleClass} ${cursorClass}`}
              >
                {validated ? (
                  <Check className="h-4 w-4" />
                ) : isFuture ? (
                  <Lock className="h-3.5 w-3.5" />
                ) : isBlocked ? (
                  <Lock className="h-4 w-4" />
                ) : (
                  s.number
                )}
              </button>
              <div className="flex-1 min-w-0">
                <p className={`text-xs leading-snug ${isView ? 'font-semibold' : ''} ${labelClass}`}>
                  {s.label}
                </p>
              </div>
              {idx < steps.length - 1 && (
                <div
                  className={`h-0.5 flex-1 transition-colors duration-500 ${
                    validated ? 'bg-emerald-300' : 'bg-border'
                  }`}
                />
              )}
            </li>
          );
        })}
      </ol>
      <div className="mt-3 flex items-center gap-3">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-bg-overlay">
          <div
            className="h-full rounded-full bg-accent transition-all duration-300"
            style={{ width: `${percent}%` }}
            aria-hidden="true"
          />
        </div>
        <span className="text-xs font-medium text-fg-subtle">
          {validatedCount}/{total} etapes
        </span>
      </div>
    </nav>
  );
}

import { AlertCircle, RefreshCw } from "lucide-react";
import { cn } from "../../lib/utils";

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Etat erreur (UI requirement: AGENT_BRIEF.md section 0 rule 4).
 * Toujours actionable : message clair + bouton retry si applicable.
 */
export function ErrorState({
  title = "Une erreur est survenue",
  message = "Impossible de charger les donnees. Verifiez votre connexion ou reessayez.",
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center rounded-lg border border-red-500/30 bg-red-500/5 px-6 py-10 text-center",
        className,
      )}
    >
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-500/15 text-red-400">
        <AlertCircle className="h-6 w-6" />
      </div>
      <h3 className="text-base font-semibold text-[var(--color-fg)]">{title}</h3>
      <p className="mt-1.5 max-w-md text-sm text-[var(--color-fg-muted)]">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-4 inline-flex h-8 items-center gap-2 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-raised)] px-3 text-sm font-medium text-[var(--color-fg)] hover:border-[var(--color-border-hi)]"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Reessayer
        </button>
      )}
    </div>
  );
}

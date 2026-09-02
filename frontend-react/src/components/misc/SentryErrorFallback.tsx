interface SentryErrorFallbackProps {
  resetError: () => void;
}

/**
 * Fallback affiche quand une exception non gere remonte a Sentry.ErrorBoundary.
 * Le user voit ce composant a la place de la page cassee.
 */
export function SentryErrorFallback({ resetError }: SentryErrorFallbackProps) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-overlay p-6">
      <div className="max-w-md bg-bg-raised border rounded-lg shadow-sm p-6 text-center">
        <h1 className="text-xl font-semibold text-fg mb-2">
          Une erreur inattendue est survenue
        </h1>
        <p className="text-sm text-fg-muted mb-4">
          L'incident a ete signale a notre equipe technique. Vous pouvez retenter
          l'operation ou recharger la page.
        </p>
        <div className="flex gap-2 justify-center">
          <button
            type="button"
            className="px-4 py-2 rounded bg-fg text-bg-raised text-sm hover:bg-fg-muted"
            onClick={resetError}
          >
            Reessayer
          </button>
          <button
            type="button"
            className="px-4 py-2 rounded border border-border-hi text-sm hover:bg-bg-overlay"
            onClick={() => window.location.reload()}
          >
            Recharger la page
          </button>
        </div>
      </div>
    </div>
  );
}

import { RefreshCw } from 'lucide-react';
import { Button } from '../ui/Button';

interface SentryErrorFallbackProps {
  resetError: () => void;
}

/**
 * Fallback affiche quand une exception non geree remonte a Sentry.ErrorBoundary.
 * Le user voit ce composant a la place de la page cassee.
 *
 * Lot L6b (E9) : boutons et jetons de la bibliotheque.
 */
export function SentryErrorFallback({ resetError }: SentryErrorFallbackProps) {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-bg p-6 text-fg">
      <div role="alert" className="max-w-md rounded-xl border border-border bg-bg-raised p-6 text-center shadow-card">
        <h1 className="mb-2 text-xl font-semibold text-heading">Une erreur inattendue est survenue</h1>
        <p className="mb-4 text-sm text-fg-muted">
          Vous pouvez réessayer l’opération ou recharger la page. Si le problème persiste,
          signalez-le au support en indiquant ce que vous faisiez à ce moment-là.
        </p>
        <div className="flex flex-wrap justify-center gap-2">
          <Button type="button" onClick={resetError}>
            Réessayer
          </Button>
          <Button type="button" variant="secondary" icon={<RefreshCw />} onClick={() => window.location.reload()}>
            Recharger la page
          </Button>
        </div>
      </div>
    </main>
  );
}

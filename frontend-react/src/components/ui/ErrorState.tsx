import { AlertCircle, RefreshCw } from 'lucide-react';
import { cn } from '../../lib/utils';
import { Button } from './Button';

/**
 * Etat d'erreur d'une zone ou d'une page (lot L6b, E1) : ce qui s'est passe, quoi faire,
 * bouton "Reessayer" si l'appelant sait recommencer.
 */
interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorState({
  title = 'Une erreur est survenue',
  message = 'Impossible de charger les données. Vérifiez votre connexion, puis réessayez.',
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn('flex flex-col items-center justify-center rounded-xl border border-danger/30 bg-danger-soft px-6 py-10 text-center', className)}
    >
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-bg-raised text-danger" aria-hidden="true">
        <AlertCircle className="h-6 w-6" />
      </div>
      <h3 className="text-base font-semibold text-fg">{title}</h3>
      <p className="mt-1.5 max-w-md text-sm text-fg">{message}</p>
      {onRetry && (
        <Button type="button" variant="secondary" size="sm" className="mt-4" onClick={onRetry} icon={<RefreshCw />}>
          Réessayer
        </Button>
      )}
    </div>
  );
}

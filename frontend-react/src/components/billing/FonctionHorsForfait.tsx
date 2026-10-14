import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';

/**
 * Lot L7 (E5), CDC 4.5 : explication affichee a la place d'une fonction que le forfait du cabinet
 * ne comprend pas (le serveur la refuse aussi, P6).
 */
export function FonctionHorsForfait({ fonction, disponibleDes, compact = false }: {
  fonction: string;
  disponibleDes: string | null;
  compact?: boolean;
}) {
  return (
    <div role="note" data-testid="fonction-hors-forfait"
      className={['rounded-lg border border-border bg-bg-overlay text-sm text-fg-muted', compact ? 'p-3' : 'p-6'].join(' ')}>
      <p className="flex items-start gap-2 font-medium text-fg">
        <Lock className="mt-0.5 h-4 w-4 flex-shrink-0 text-accent" aria-hidden="true" />
        {fonction} : fonction non comprise dans votre forfait.
      </p>
      <p className="mt-1">
        {disponibleDes
          ? `Elle est incluse dès le forfait ${disponibleDes}.`
          : 'Elle est incluse dans un forfait supérieur.'}{' '}
        Le superviseur du cabinet peut changer de forfait depuis{' '}
        <Link to="/app/billing/change-plan" className="text-accent underline">Facturation, Changer de forfait</Link>.
      </p>
    </div>
  );
}

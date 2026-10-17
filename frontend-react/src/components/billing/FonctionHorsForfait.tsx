import { Lock } from 'lucide-react';
import { useCurrentUser } from '../../store/authStore';
import { Lien } from '../ui/Lien';

/**
 * Lot L7 (E5), CDC 4.5 : explication affichee a la place d'une fonction que le forfait du cabinet
 * ne comprend pas (le serveur la refuse aussi, P6).
 */
export function FonctionHorsForfait({ fonction, disponibleDes, compact = false }: {
  fonction: string;
  disponibleDes: string | null;
  compact?: boolean;
}) {
  // L6b (E8) : seul le superviseur change de forfait (le serveur le refuse aux autres) ;
  // le lien vers le changement ne s'affiche qu'a lui.
  const superviseur = useCurrentUser()?.role === 'SUPERVISEUR';
  return (
    <div role="note" data-testid="fonction-hors-forfait"
      className={['rounded-xl border border-border bg-bg-raised text-sm text-fg-muted', compact ? 'p-3' : 'p-6'].join(' ')}>
      <p className="flex items-start gap-2 font-medium text-fg">
        <Lock className="mt-0.5 h-4 w-4 flex-shrink-0 text-fg-subtle" aria-hidden="true" />
        {fonction} : fonction non comprise dans votre forfait.
      </p>
      <p className="mt-1">
        {disponibleDes
          ? `Elle est incluse dès le forfait ${disponibleDes}.`
          : 'Elle est incluse dans un forfait supérieur.'}{' '}
        {superviseur ? (
          <>
            Vous pouvez changer de forfait depuis{' '}
            <Lien to="/app/billing/change-plan">Facturation, Changer de forfait</Lien>.
          </>
        ) : (
          'Le superviseur du cabinet peut changer de forfait depuis Facturation, Changer de forfait.'
        )}
      </p>
    </div>
  );
}

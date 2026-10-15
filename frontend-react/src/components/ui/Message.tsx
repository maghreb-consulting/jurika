import type { ReactNode } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * Message en ligne (lot L6b, E1) : information, succes, attention, erreur. Teinte douce
 * de l'etat, filet de couleur a gauche, icone, titre optionnel, action optionnelle.
 *
 * - `erreur` : role="alert" (annonce immediate) ; les autres : role="status".
 * - `onFermer` : bouton de fermeture (le message se masque ; a l'appelant de le retenir).
 *
 * Pour une confirmation passagere ("Enregistré"), utiliser le toast (useToast).
 */
export type TonMessage = 'info' | 'succes' | 'attention' | 'erreur';

const TONS: Record<TonMessage, { icone: typeof Info; classes: string; icon: string }> = {
  info: { icone: Info, classes: 'bg-info-soft border-l-info', icon: 'text-info' },
  succes: { icone: CheckCircle2, classes: 'bg-success-soft border-l-success', icon: 'text-success' },
  attention: { icone: AlertTriangle, classes: 'bg-warning-soft border-l-warning', icon: 'text-warning' },
  erreur: { icone: AlertCircle, classes: 'bg-danger-soft border-l-danger', icon: 'text-danger' },
};

export function Message({
  ton = 'info',
  titre,
  children,
  action,
  onFermer,
  className,
}: {
  ton?: TonMessage;
  titre?: ReactNode;
  children?: ReactNode;
  action?: ReactNode;
  onFermer?: () => void;
  className?: string;
}) {
  const t = TONS[ton];
  const Icone = t.icone;
  return (
    <div
      role={ton === 'erreur' ? 'alert' : 'status'}
      className={cn('flex items-start gap-3 rounded-lg border border-border border-l-4 px-4 py-3 text-sm text-fg', t.classes, className)}
    >
      <Icone className={cn('mt-0.5 h-5 w-5 shrink-0', t.icon)} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {titre && <p className="font-semibold text-fg">{titre}</p>}
        {children && <div className={cn('leading-relaxed', titre && 'mt-0.5')}>{children}</div>}
        {action && <div className="mt-2 flex flex-wrap gap-2">{action}</div>}
      </div>
      {onFermer && (
        <button
          type="button"
          onClick={onFermer}
          aria-label="Fermer le message"
          className="-mr-1 -mt-0.5 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-fg-muted hover:bg-bg-raised hover:text-fg"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

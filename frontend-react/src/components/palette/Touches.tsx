import { Fragment } from 'react';
import { cn } from '../../lib/utils';

/**
 * Lot L6b (E11) : touches d'un raccourci (<kbd>). `combinaison` : enfoncees ensemble
 * ("Ctrl + K") ; sinon l'une apres l'autre ("g puis t").
 */
export function Touches({ touches, combinaison = false, className }: { touches: string[]; combinaison?: boolean; className?: string }) {
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1 text-xs text-fg-muted', className)}>
      {touches.map((t, i) => (
        <Fragment key={`${t}-${i}`}>
          {i > 0 && <span>{combinaison ? '+' : touches.length > 1 ? 'puis' : ''}</span>}
          <kbd className="inline-flex min-w-[1.5rem] items-center justify-center rounded border border-border-hi bg-bg-raised px-1.5 py-0.5 font-mono text-[11px] font-medium text-fg shadow-card">
            {t}
          </kbd>
        </Fragment>
      ))}
    </span>
  );
}

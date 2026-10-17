import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * Fil d'Ariane (lot L6b, E1) : ou suis-je, comment remonter. Le dernier element est la
 * page courante (aria-current="page", sans lien).
 */
export interface ElementFil {
  libelle: string;
  to?: string;
}

export function FilAriane({ elements, className }: { elements: ElementFil[]; className?: string }) {
  if (elements.length === 0) return null;
  return (
    <nav aria-label="Fil d’Ariane" className={cn('min-w-0', className)}>
      <ol className="flex min-w-0 flex-wrap items-center gap-1 text-xs text-fg-subtle">
        {elements.map((el, i) => {
          const dernier = i === elements.length - 1;
          return (
            <li key={`${el.libelle}-${i}`} className="flex min-w-0 items-center gap-1">
              {i > 0 && <ChevronRight className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />}
              {dernier || !el.to ? (
                <span aria-current={dernier ? 'page' : undefined} className={cn('truncate', dernier && 'font-medium text-fg-muted')}>
                  {el.libelle}
                </span>
              ) : (
                <Link to={el.to} className="truncate rounded-sm underline-offset-2 hover:text-fg hover:underline">
                  {el.libelle}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

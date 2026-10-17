import type { ReactNode } from 'react';
import { CheckCircle } from 'lucide-react';

/**
 * Lot L6b (E5) : bandeau de fin de parcours (synthese). Teinte de succes, sobre : il
 * remplace les anciens degrades de couleur, differents d'un parcours a l'autre.
 */
export function BandeauFinParcours({ titre, children }: { titre: ReactNode; children?: ReactNode }) {
  return (
    <div role="status" className="flex items-start gap-4 rounded-xl border border-success/40 bg-success-soft p-5">
      <CheckCircle className="mt-0.5 h-7 w-7 shrink-0 text-success" aria-hidden="true" />
      <div className="min-w-0">
        <h2 className="text-xl font-semibold text-heading">{titre}</h2>
        {children && <p className="mt-1 text-sm text-fg">{children}</p>}
      </div>
    </div>
  );
}

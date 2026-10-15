import { AlertCircle } from 'lucide-react';
import type { ErreurFacturation } from '../../types/billing';

/** Lot L7 (E5) : refus du serveur, avec ce qui manque ou ce qui depasse (422 : elements nommes). */
export function ErreurFacturationBloc({ erreur }: { erreur: ErreurFacturation | null }) {
  if (!erreur) return null;
  return (
    <div role="alert" className="rounded-lg border border-danger/50 bg-danger/10 p-3 text-sm text-danger" data-testid="erreur-facturation">
      <p className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
        <span>{erreur.message}</span>
      </p>
      {erreur.elements.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-6">
          {erreur.elements.map((e) => <li key={e}>{e}</li>)}
        </ul>
      )}
    </div>
  );
}

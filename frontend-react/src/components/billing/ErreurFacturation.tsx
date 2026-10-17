import { Message } from '../ui/Message';
import type { ErreurFacturation } from '../../types/billing';

/** Lot L7 (E5) : refus du serveur, avec ce qui manque ou ce qui depasse (422 : elements nommes). */
export function ErreurFacturationBloc({ erreur }: { erreur: ErreurFacturation | null }) {
  if (!erreur) return null;
  return (
    <div data-testid="erreur-facturation">
      <Message ton="erreur">
        <p>{erreur.message}</p>
        {erreur.elements.length > 0 && (
          <ul className="mt-2 list-disc space-y-0.5 pl-6">
            {erreur.elements.map((e) => <li key={e}>{e}</li>)}
          </ul>
        )}
      </Message>
    </div>
  );
}

import { useState } from 'react';
import { usePreferencesStore } from '../../store/preferencesStore';
import { Message } from '../ui/Message';
import { Button } from '../ui/Button';

/**
 * Lot L6b (E3) : erreur de lecture ou d'enregistrement des preferences du compte (theme,
 * assistant JURI), affichee dans la coquille : jamais de repli silencieux. "Reessayer"
 * renvoie la derniere modification (ou relit les preferences).
 */
export function ErreurPreferences() {
  const erreur = usePreferencesStore((s) => s.erreur);
  const reessayer = usePreferencesStore((s) => s.reessayer);
  const [enCours, setEnCours] = useState(false);
  const effacer = usePreferencesStore((s) => s.effacerErreur);
  if (!erreur) return null;
  return (
    <div className="px-4 pt-4 md:px-8">
      <Message
        ton="attention"
        titre="Préférences d’affichage"
        onFermer={effacer}
        action={
          <Button
            size="sm"
            variant="secondary"
            loading={enCours}
            onClick={async () => {
              setEnCours(true);
              try {
                await reessayer();
              } finally {
                setEnCours(false);
              }
            }}
          >
            Réessayer
          </Button>
        }
      >
        {erreur}
      </Message>
    </div>
  );
}

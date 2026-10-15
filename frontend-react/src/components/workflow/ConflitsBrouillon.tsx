import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import type { BrouillonLocal } from '../../pages/workflow/brouillonsLocaux';
import { InfoBulle } from '../ui/Aide';
import { Button } from '../ui/Button';

/**
 * Lot L7 (E12) : une saisie gardee dans ce navigateur (session expiree, coupure) n'a
 * pas ete renvoyee parce que l'etape a ete modifiee ailleurs depuis (autre poste,
 * autre onglet). Rien n'est ecrase en silence : l'employe choisit la version a garder.
 * Tant qu'il n'a pas choisi, les champs affichent la version enregistree sur le
 * serveur et la saisie locale reste dans ce navigateur.
 */
export function ConflitsBrouillon({
  conflits,
  onResoudre,
}: {
  conflits: BrouillonLocal[];
  onResoudre: (b: BrouillonLocal, garder: 'local' | 'serveur') => Promise<void>;
}) {
  const [enCours, setEnCours] = useState<string | null>(null);
  if (conflits.length === 0) return null;

  async function choisir(b: BrouillonLocal, garder: 'local' | 'serveur') {
    setEnCours(b.idConflit ?? b.cle);
    try {
      await onResoudre(b, garder);
    } finally {
      setEnCours(null);
    }
  }

  return (
    <section
      role="alert"
      aria-label="Saisie gardée dans ce navigateur"
      className="mb-4 space-y-3 rounded-lg border border-warning/40 border-l-4 border-l-warning bg-warning-soft p-4 text-sm text-fg"
    >
      <p className="flex items-center gap-2 font-semibold">
        <AlertTriangle className="h-4 w-4 text-warning" aria-hidden="true" />
        Une saisie gardée dans ce navigateur n’a pas été envoyée
        <InfoBulle
          libelle="Pourquoi ce choix ?"
          texte="Votre saisie n’a pas pu partir (session expirée ou connexion coupée) ; depuis, l’étape a été modifiée ailleurs. Pour ne rien perdre, rien n’a été remplacé : les champs affichent la version enregistrée, votre saisie reste dans ce navigateur jusqu’à votre choix."
        />
      </p>
      <ul className="space-y-3">
        {conflits.map((b) => (
          <li key={b.idConflit ?? b.cle} className="rounded-md border border-border bg-bg-raised p-3">
            <p>
              Étape {b.step} : saisie gardée le{' '}
              {new Date(b.ecritA).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}.
              L’étape a été modifiée depuis. Quelle version voulez-vous garder ?
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button type="button" size="sm" disabled={enCours != null} onClick={() => void choisir(b, 'local')}>
                Garder ma saisie de ce navigateur
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={enCours != null}
                onClick={() => void choisir(b, 'serveur')}
              >
                Garder la version enregistrée
              </Button>
            </div>
            <p className="mt-2 text-xs text-fg-subtle">
              « Garder ma saisie » remplace l’étape enregistrée et réaffiche les champs ;
              « Garder la version enregistrée » efface la saisie de ce navigateur.
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}

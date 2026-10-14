import { useState } from 'react';
import { Compass, Info } from 'lucide-react';
import { useAidesStore } from '../../store/aidesStore';

/**
 * Lot L6 (E6, CDC 15.4) : bloc "Aides et visite guidee" de la page "Mon compte".
 * Reaffiche toutes les aides masquees (encadres d'aide et visite guidee) : le
 * choix est enregistre cote serveur pour l'utilisateur.
 */
export function ReafficherAides() {
  const reafficherTout = useAidesStore((s) => s.reafficherTout);
  const nombre = useAidesStore((s) => s.masquees.size);
  const [message, setMessage] = useState<{ ok: boolean; texte: string } | null>(null);
  const [enCours, setEnCours] = useState(false);

  function reafficher() {
    setEnCours(true);
    reafficherTout()
      .then(
        () => setMessage({ ok: true, texte: 'Toutes les aides sont de nouveau affichées, ainsi que la visite guidée.' }),
        () =>
          setMessage({
            ok: false,
            texte: 'Les aides sont réaffichées sur cet écran, mais le choix n’a pas pu être enregistré. Réessayez plus tard.',
          }),
      )
      .finally(() => setEnCours(false));
  }

  return (
    <section aria-labelledby="titre-aides" className="rounded-lg border border-border bg-bg-raised p-5">
      <h2 id="titre-aides" className="flex items-center gap-2 text-sm font-semibold text-fg">
        <Compass className="h-4 w-4 text-accent" aria-hidden="true" /> Aides et visite guidée
      </h2>
      <p className="mt-1 text-xs text-fg-muted">
        Chaque écran porte des aides que vous pouvez masquer une fois lues.{' '}
        {nombre > 0
          ? `Vous en avez masqué ${nombre}. Ce bouton les réaffiche toutes, ainsi que la visite guidée de la première connexion.`
          : 'Aucune aide n’est masquée pour le moment.'}
      </p>
      <button
        type="button"
        onClick={reafficher}
        disabled={enCours}
        className="mt-3 inline-flex items-center gap-2 rounded-md border border-border-hi px-3 py-1.5 text-sm text-fg transition hover:bg-bg-overlay disabled:opacity-50"
      >
        <Info className="h-4 w-4" aria-hidden="true" /> Réafficher les aides
      </button>
      {message && (
        <p role="status" className={`mt-2 text-xs ${message.ok ? 'text-success' : 'text-danger'}`}>
          {message.texte}
        </p>
      )}
    </section>
  );
}

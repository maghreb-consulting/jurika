import { useState } from 'react';
import { Check, X } from 'lucide-react';
import { extractError } from '../../lib/api';
import { detecterDonneesPersonnelles, messageReformulation } from '../../lib/donneesPersonnelles';

/** Longueur maximale d'un titre (serveur : ConversationsChatbotService.TITRE_MAX). */
export const TITRE_MAX = 120;

/**
 * Lot L5b E2 (RG-COM-12) : renommage d'une conversation du chatbot. Le titre est controle
 * comme une question (RG-COM-04) : un titre qui contient une donnee de client est refuse.
 */
export function RenommerConversation({
  titre,
  onValider,
  onAnnuler,
}: {
  titre: string;
  onValider: (titre: string) => Promise<void>;
  onAnnuler: () => void;
}) {
  const [valeur, setValeur] = useState(titre);
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  async function valider() {
    const t = valeur.trim().replace(/\s+/g, ' ');
    if (!t) {
      setErreur('Le titre ne peut pas être vide.');
      return;
    }
    const natures = detecterDonneesPersonnelles(t);
    if (natures.length > 0) {
      setErreur(messageReformulation(natures));
      return;
    }
    setEnvoi(true);
    try {
      await onValider(t);
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <form
      className="flex min-w-0 flex-1 flex-col gap-1"
      onSubmit={(ev) => {
        ev.preventDefault();
        void valider();
      }}
    >
      <div className="flex items-center gap-1">
        <label htmlFor="assistant-titre" className="sr-only">
          Titre de la conversation
        </label>
        <input
          id="assistant-titre"
          autoFocus
          maxLength={TITRE_MAX}
          value={valeur}
          onChange={(ev) => {
            setValeur(ev.target.value);
            setErreur(null);
          }}
          onKeyDown={(ev) => {
            if (ev.key === 'Escape') onAnnuler();
          }}
          className="min-w-0 flex-1 rounded-md border border-border bg-bg px-2 py-1 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
        />
        <button
          type="submit"
          disabled={envoi}
          className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg disabled:opacity-50"
          aria-label="Enregistrer le titre"
        >
          <Check className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={onAnnuler}
          className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          aria-label="Annuler le renommage"
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
      {erreur ? (
        <p role="alert" className="text-xs text-danger">
          {erreur}
        </p>
      ) : (
        <p className="text-[11px] text-fg-subtle">
          Donnez un titre qui vous aide à retrouver la conversation, sans donnée de client. Entrée pour
          enregistrer, Échap pour annuler.
        </p>
      )}
    </form>
  );
}

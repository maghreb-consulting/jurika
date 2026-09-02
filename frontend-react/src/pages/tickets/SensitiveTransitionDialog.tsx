import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '../../components/ui/Button';

interface Props {
  /** Titre du dialog (ex : "Annuler le ticket", "Reprendre le ticket"). */
  title: string;
  /** Phrase d'introduction expliquant l'action a l'employe. */
  intro: string;
  /** Reference du ticket affichee en mono dans l'intro (optionnel). */
  reference?: string;
  /** Longueur minimale du motif (10 pour l'annulation, 1 = non vide pour reprise/cloture). */
  minLength: number;
  /** Libelle du bouton de confirmation finale. */
  confirmLabel: string;
  /** Style du bouton d'action (danger pour annulation, primary pour reprise/cloture). */
  variant?: 'danger' | 'primary';
  onClose: () => void;
  onConfirm: (comment: string) => Promise<void>;
}

type Step = 'confirm' | 'comment' | 'final';

/**
 * Dialog de transition SENSIBLE generalise (3 etapes : confirmer -> saisir le
 * motif -> confirmation finale). Sert de mecanisme de consentement explicite +
 * capture du motif pour ANNULER / REPRENDRE / CLOTURER-depuis-ANNULE.
 * Generalise l'ancien CancelTicketDialog.
 */
export function SensitiveTransitionDialog({
  title,
  intro,
  reference,
  minLength,
  confirmLabel,
  variant = 'danger',
  onClose,
  onConfirm,
}: Props) {
  const [step, setStep] = useState<Step>('confirm');
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const commentValid = comment.trim().length >= minLength;
  const helper =
    minLength > 1 ? `${comment.trim().length} / ${minLength} caracteres min` : 'Motif obligatoire';

  async function handleConfirm() {
    if (!commentValid) {
      setError(
        minLength > 1
          ? `Le motif doit comporter au moins ${minLength} caracteres`
          : 'Le motif est obligatoire',
      );
      return;
    }
    setLoading(true);
    try {
      await onConfirm(comment.trim());
    } catch (err) {
      setError((err as Error)?.message ?? 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-fg/40 px-4">
      <div className="w-full max-w-md rounded-2xl bg-bg-raised p-6 shadow-2xl">
        <div className="mb-4 flex items-center gap-3">
          <div className="rounded-full bg-rose-100 p-2">
            <AlertTriangle className="h-5 w-5 text-rose-600" />
          </div>
          <h2 className="text-lg font-semibold text-fg">{title}</h2>
        </div>

        {step === 'confirm' && (
          <>
            <p className="text-sm text-fg-muted">
              {intro}
              {reference && (
                <>
                  {' '}
                  <span className="font-mono">{reference}</span>.
                </>
              )}
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="secondary" onClick={onClose}>
                Revenir
              </Button>
              <Button variant={variant} onClick={() => setStep('comment')}>
                Continuer
              </Button>
            </div>
          </>
        )}

        {step === 'comment' && (
          <>
            <p className="text-sm text-fg-muted">
              Indiquez le motif{minLength > 1 ? ` (${minLength} caracteres min)` : ''} :
            </p>
            <textarea
              value={comment}
              onChange={(e) => {
                setComment(e.target.value);
                setError(null);
              }}
              rows={4}
              className="mt-3 w-full rounded-lg border border-border-hi px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
              placeholder="Ex : Reprise demandee par le client suite a..."
            />
            <p className="mt-1 text-xs text-fg-subtle">{helper}</p>
            {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setStep('confirm')}>
                Retour
              </Button>
              <Button variant={variant} onClick={() => setStep('final')} disabled={!commentValid}>
                Suivant
              </Button>
            </div>
          </>
        )}

        {step === 'final' && (
          <>
            <p className="text-sm text-fg-muted">Confirmer definitivement cette action ?</p>
            <div className="mt-3 rounded-lg border border-border bg-bg-overlay p-3 text-xs italic text-fg-muted">
              "{comment}"
            </div>
            {error && <p className="mt-2 text-xs text-rose-600">{error}</p>}
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setStep('comment')}>
                Modifier
              </Button>
              <Button variant={variant} onClick={handleConfirm} loading={loading}>
                {confirmLabel}
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

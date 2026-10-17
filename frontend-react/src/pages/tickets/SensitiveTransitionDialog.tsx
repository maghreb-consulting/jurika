import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Message } from '../../components/ui/Message';
import { ZoneTexte } from '../../components/ui/TextField';

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
    minLength > 1 ? `${comment.trim().length} / ${minLength} caractères au minimum` : 'Motif obligatoire';

  async function handleConfirm() {
    if (!commentValid) {
      setError(
        minLength > 1
          ? `Le motif doit comporter au moins ${minLength} caractères.`
          : 'Le motif est obligatoire.',
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

  const pied =
    step === 'confirm' ? (
      <>
        <Button type="button" variant="secondary" onClick={onClose}>
          Revenir
        </Button>
        <Button type="button" variant={variant} onClick={() => setStep('comment')}>
          Continuer
        </Button>
      </>
    ) : step === 'comment' ? (
      <>
        <Button type="button" variant="secondary" onClick={() => setStep('confirm')}>
          Retour
        </Button>
        <Button type="button" variant={variant} onClick={() => setStep('final')} disabled={!commentValid}>
          Suivant
        </Button>
      </>
    ) : (
      <>
        <Button type="button" variant="secondary" onClick={() => setStep('comment')}>
          Modifier
        </Button>
        <Button type="button" variant={variant} onClick={handleConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </>
    );

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={
        <span className="flex items-center gap-2">
          <AlertTriangle className={variant === 'danger' ? 'h-5 w-5 text-danger' : 'h-5 w-5 text-warning'} aria-hidden="true" />
          {title}
        </span>
      }
      size="md"
      closeOnBackdrop={false}
      footer={<div className="flex justify-end gap-2">{pied}</div>}
    >
      {step === 'confirm' && (
        <p className="text-sm text-fg-muted">
          {intro}
          {reference && (
            <>
              {' '}
              <span className="font-mono">{reference}</span>.
            </>
          )}
        </p>
      )}

      {step === 'comment' && (
        <ZoneTexte
          label={`Motif${minLength > 1 ? ` (${minLength} caractères au minimum)` : ''}`}
          value={comment}
          onChange={(e) => {
            setComment(e.target.value);
            setError(null);
          }}
          rows={4}
          autoFocus
          placeholder="Exemple : reprise demandée par le client à la suite de…"
          hint={helper}
          error={error ?? undefined}
        />
      )}

      {step === 'final' && (
        <div className="space-y-3">
          <p className="text-sm text-fg-muted">Confirmer définitivement cette action ?</p>
          <blockquote className="rounded-lg border border-border bg-bg-overlay p-3 text-sm italic text-fg-muted">
            « {comment} »
          </blockquote>
          {error && <Message ton="erreur">{error}</Message>}
        </div>
      )}
    </Modal>
  );
}

import type { ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  /** Contenu additionnel (ex : recapitulatif de fichiers) affiche au-dessus des boutons. */
  children?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** 'danger' pour une action destructive (suppression), 'primary' sinon. */
  variant?: 'danger' | 'primary';
  loading?: boolean;
  onConfirm: () => void;
}

/**
 * Dialog de confirmation generique (consentement explicite avant une action).
 * Bati sur la primitive {@link Modal} (Radix : focus trap, Esc, click overlay).
 * Fermer sans confirmer = annuler (aucun effet). Reutilisable partout ou une
 * action a besoin d'un garde-fou (suppression, upload, etc.).
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  variant = 'primary',
  loading = false,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <Modal open={open} onOpenChange={onOpenChange} title={title} description={description} size="sm">
      {children}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button variant={variant} onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}

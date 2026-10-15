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
 * Confirmation explicite avant une action (suppression, envoi...). Batie sur {@link Modal}
 * (<dialog> natif). Fermer sans confirmer = annuler (aucun effet).
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
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button type="button" variant={variant} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children}
    </Modal>
  );
}

import { useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { useDialogueNatif } from './useDialogueNatif';

/**
 * Fenetre modale (lot L6b, E1) sur l'element natif <dialog> (showModal) : couche
 * superieure, page inerte derriere, Echap et clic sur le voile ferment, focus rendu a la
 * fermeture. Le contenu defile dans la fenetre ; la page derriere ne defile pas.
 *
 * API inchangee (ancienne version Radix) : `open`, `onOpenChange`, `title`, `description`,
 * `size` ('sm' 24rem, 'md' 28rem, 'lg' 42rem, 'xl' 56rem), `hideCloseButton`, `className`.
 * Ajout : `footer` (barre d'actions collee en bas, toujours visible).
 *
 *   <Modal open={open} onOpenChange={setOpen} title="Confirmation" footer={<Button>OK</Button>}>
 *     <p>Contenu</p>
 *   </Modal>
 */
export interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  hideCloseButton?: boolean;
  footer?: ReactNode;
  className?: string;
  /** Faux : un clic sur le voile ne ferme pas (saisie longue). */
  closeOnBackdrop?: boolean;
}

const TAILLES: Record<NonNullable<ModalProps['size']>, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export function Modal(props: ModalProps) {
  if (!props.open || typeof document === 'undefined') return null;
  return createPortal(<FenetreOuverte {...props} />, document.body);
}

function FenetreOuverte({
  onOpenChange,
  title,
  description,
  children,
  size = 'md',
  hideCloseButton = false,
  footer,
  className,
  closeOnBackdrop = true,
}: ModalProps) {
  const idTitre = useId();
  const idDesc = useId();
  const { ref, ...handlers } = useDialogueNatif(() => onOpenChange(false), closeOnBackdrop);
  return (
    <dialog
      ref={ref}
      aria-labelledby={idTitre}
      aria-describedby={description ? idDesc : undefined}
      aria-modal="true"
      className={cn(
        'fenetre fixed inset-0 z-50 m-auto flex h-fit max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] flex-col overflow-hidden',
        'rounded-xl border border-border bg-bg-raised p-0 text-fg shadow-card-lifted',
        TAILLES[size],
        className,
      )}
      {...handlers}
    >
      <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
        <div className="min-w-0 flex-1">
          <h2 id={idTitre} className="text-lg font-semibold text-heading">
            {title}
          </h2>
          {description && (
            <div id={idDesc} className="mt-1 text-sm text-fg-muted">
              {description}
            </div>
          )}
        </div>
        {!hideCloseButton && (
          <button
            type="button"
            aria-label="Fermer"
            onClick={() => onOpenChange(false)}
            className="-mr-2 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>
      {children ? <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-6 py-5">{children}</div> : null}
      {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-border bg-bg-overlay px-6 py-3">{footer}</div>}
    </dialog>
  );
}

/** Nom francais du meme composant. */
export const Fenetre = Modal;

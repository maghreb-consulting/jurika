import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';

interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  /** Largeur max — 'sm' (24rem), 'md' (32rem, default), 'lg' (48rem), 'xl' (64rem). */
  size?: 'sm' | 'md' | 'lg' | 'xl';
  /** Cacher la croix en haut à droite (rare ; ex : modal de confirmation forcée). */
  hideCloseButton?: boolean;
  className?: string;
}

const SIZE_CLASSES: Record<NonNullable<ModalProps['size']>, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

/**
 * Sprint 12.5 T5 — Modal primitive sur Radix Dialog + palette marketing.
 *
 * Accessibilité gérée par Radix : focus trap, role=dialog, aria-labelledby,
 * aria-describedby, Esc pour fermer, click overlay pour fermer.
 *
 * Style :
 * - overlay : navy 80% opacity + backdrop-blur subtle
 * - content : bg-overlay (navy le plus clair) + border-border-hi + shadow-card-lifted
 * - close button : ghost icon top-right, focus ring or
 * - title : font-heading (Playfair Display)
 *
 * Pattern d'usage :
 *   <Modal open={open} onOpenChange={setOpen} title="Confirmation">
 *     <p>Contenu…</p>
 *   </Modal>
 */
export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
  size = 'md',
  hideCloseButton = false,
  className,
}: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-bg/80 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <Dialog.Content
          className={twMerge(
            'fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-border-hi bg-bg-overlay p-6 shadow-card-lifted focus:outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
            SIZE_CLASSES[size],
            className,
          )}
        >
          <div className="mb-4 flex items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <Dialog.Title className="font-heading text-xl font-semibold text-fg">
                {title}
              </Dialog.Title>
              {description && (
                <Dialog.Description className="mt-1 text-sm text-fg-muted">
                  {description}
                </Dialog.Description>
              )}
            </div>
            {!hideCloseButton && (
              <Dialog.Close
                aria-label="Fermer"
                className="flex h-8 w-8 items-center justify-center rounded-md text-fg-subtle transition hover:bg-bg-raised hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
              >
                <X className="h-4 w-4" />
              </Dialog.Close>
            )}
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

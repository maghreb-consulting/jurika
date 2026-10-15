import { useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { useDialogueNatif } from './useDialogueNatif';

/**
 * Tiroir lateral (lot L6b, E1) : panneau a droite sur l'element natif <dialog>
 * (showModal), memes garanties que Modal (page inerte, Echap, voile, focus rendu).
 * Le corps defile a la molette ; en-tete et pied restent visibles.
 *
 * API inchangee : `open`, `onClose`, `title`, `subtitle`, `children`, `footer`,
 * `width` ('md' 28rem, 'lg' 42rem, 'xl' 56rem).
 */
export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children?: ReactNode;
  footer?: ReactNode;
  width?: 'md' | 'lg' | 'xl';
  /** Faux : un clic sur le voile ne ferme pas (saisie longue). */
  closeOnBackdrop?: boolean;
}

const LARGEURS = {
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export function Drawer(props: DrawerProps) {
  if (!props.open || typeof document === 'undefined') return null;
  return createPortal(<TiroirOuvert {...props} />, document.body);
}

function TiroirOuvert({ onClose, title, subtitle, children, footer, width = 'lg', closeOnBackdrop = true }: DrawerProps) {
  const idTitre = useId();
  const { ref, ...handlers } = useDialogueNatif(onClose, closeOnBackdrop);
  return (
    <dialog
      ref={ref}
      aria-labelledby={idTitre}
      aria-modal="true"
      className={cn(
        'fenetre tiroir fixed inset-y-0 right-0 left-auto z-50 m-0 ml-auto flex h-dvh max-h-dvh w-full flex-col',
        'border-l border-border bg-bg-raised p-0 text-fg shadow-card-lifted',
        LARGEURS[width],
      )}
      {...handlers}
    >
      <header className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
        <div className="min-w-0">
          <h2 id={idTitre} className="text-lg font-semibold text-heading">
            {title}
          </h2>
          {subtitle && <p className="text-sm text-fg-muted">{subtitle}</p>}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="-mr-2 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          aria-label="Fermer"
        >
          <X className="h-5 w-5" aria-hidden="true" />
        </button>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-6 py-5">{children}</div>
      {footer && <footer className="border-t border-border bg-bg-overlay px-6 py-3">{footer}</footer>}
    </dialog>
  );
}

/** Nom francais du meme composant. */
export const Tiroir = Drawer;

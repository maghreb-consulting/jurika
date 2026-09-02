import type { ReactNode } from 'react';
import { X } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
  width?: 'md' | 'lg' | 'xl';
}

const widthClasses = {
  md: 'max-w-md',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export function Drawer({ open, onClose, title, subtitle, children, footer, width = 'lg' }: Props) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex" role="dialog" aria-modal="true">
      <div className="flex-1 bg-fg/40 backdrop-blur-sm" onClick={onClose} />
      <div className={`flex w-full ${widthClasses[width]} flex-col bg-bg-raised shadow-2xl`}>
        <header className="flex items-start justify-between border-b border-border px-6 py-4">
          <div>
            <h2 className="text-lg font-semibold text-fg">{title}</h2>
            {subtitle && <p className="text-sm text-fg-subtle">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
            aria-label="Fermer"
          >
            <X className="h-5 w-5" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <footer className="border-t border-border bg-bg-overlay px-6 py-4">{footer}</footer>}
      </div>
    </div>
  );
}

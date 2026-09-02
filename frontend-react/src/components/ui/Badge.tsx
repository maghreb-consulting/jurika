import type { ReactNode } from 'react';
import { twMerge } from 'tailwind-merge';

type Variant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const variantClasses: Record<Variant, string> = {
  default: 'bg-accent/10 text-accent ring-indigo-200',
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  warning: 'bg-warning/10 text-amber-700 ring-amber-200',
  danger: 'bg-danger/10 text-danger ring-rose-200',
  info: 'bg-sky-50 text-sky-700 ring-sky-200',
  neutral: 'bg-bg-overlay text-fg-muted ring-slate-200',
};

export function Badge({
  variant = 'default',
  children,
  className,
}: {
  variant?: Variant;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={twMerge(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        variantClasses[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

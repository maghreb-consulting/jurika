import type { ReactNode } from "react";
import { cn } from "../../lib/utils";

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  cta?: ReactNode;
  secondary?: ReactNode;
  className?: string;
}

/**
 * Smart Empty State (Killer Feature §4.8).
 * Toujours fournir un icon, une description claire, et un CTA primaire.
 */
export function EmptyState({ icon, title, description, cta, secondary, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center px-6 py-12 text-center",
        className,
      )}
    >
      {icon && (
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-bg-overlay)] text-[var(--color-fg-muted)]">
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-[var(--color-fg)]">{title}</h3>
      {description && (
        <p className="mt-1.5 max-w-sm text-sm text-[var(--color-fg-muted)]">{description}</p>
      )}
      {(cta || secondary) && (
        <div className="mt-5 flex items-center gap-2">
          {cta}
          {secondary}
        </div>
      )}
    </div>
  );
}

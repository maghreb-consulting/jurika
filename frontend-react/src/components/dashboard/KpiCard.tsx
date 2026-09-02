import type { ReactNode } from 'react';

interface Props {
  label: string;
  value: string | number;
  hint?: string;
  icon?: ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'danger';
}

/**
 * Sprint 12.5 T7 — KpiCard repeinte sur palette marketing.
 *
 * AVANT : bg-bg-raised + slate borders (light theme). APRÈS : bg-bg-raised navy
 * avec hairline or pour 'default', tints status pour success/warning/danger.
 * Le label garde son traitement eyebrow (uppercase mono) signature edito.
 * La valeur est en font-heading (Playfair) pour signature numerique.
 */
const VARIANTS: Record<NonNullable<Props['variant']>, string> = {
  default: 'bg-bg-raised border-border border-t-2 border-t-accent',
  success: 'bg-success/10 border-success/30',
  warning: 'bg-warning/10 border-warning/30',
  danger: 'bg-danger/10 border-danger/30',
};

const VALUE_COLORS: Record<NonNullable<Props['variant']>, string> = {
  default: 'text-fg',
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
};

export function KpiCard({ label, value, hint, icon, variant = 'default' }: Props) {
  return (
    <div className={`rounded-xl border px-4 py-3 shadow-card ${VARIANTS[variant]}`}>
      <div className="flex items-start justify-between">
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.18em] text-fg-subtle">
          {label}
        </div>
        {icon && <div className="text-fg-subtle">{icon}</div>}
      </div>
      <div className={`mt-1 font-heading text-2xl font-semibold ${VALUE_COLORS[variant]}`}>
        {value}
      </div>
      {hint && <div className="mt-1 text-xs text-fg-subtle">{hint}</div>}
    </div>
  );
}

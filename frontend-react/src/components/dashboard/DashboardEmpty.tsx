import type { ReactNode } from 'react';
import { Sparkles } from 'lucide-react';

interface Props {
  title: string;
  message: string;
  cta?: ReactNode;
}

export function DashboardEmpty({ title, message, cta }: Props) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border-hi bg-bg-overlay px-6 py-8 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-amber-100">
        <Sparkles className="h-6 w-6 text-warning" />
      </div>
      <h4 className="text-sm font-semibold text-fg">{title}</h4>
      <p className="max-w-sm text-xs text-fg-subtle">{message}</p>
      {cta}
    </div>
  );
}

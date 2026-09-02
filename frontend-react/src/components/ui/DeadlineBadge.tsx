import { cn } from "../../lib/utils";
import { severityClass } from "../../lib/tokens";
import type { DeadlineSeverity } from "../../types/deadline";

interface DeadlineBadgeProps {
  severity: DeadlineSeverity;
  dueAt: string;
  className?: string;
}

const RULES_TZ = "Africa/Casablanca";

function formatDueAt(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleString("fr-FR", {
    timeZone: RULES_TZ,
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function relativeFromNow(iso: string): string {
  const due = new Date(iso).getTime();
  const now = Date.now();
  const days = Math.round((due - now) / 86_400_000);
  if (days < -1) return `il y a ${Math.abs(days)} jours`;
  if (days === -1) return "hier";
  if (days === 0) return "aujourd'hui";
  if (days === 1) return "demain";
  if (days < 30) return `dans ${days} jours`;
  return `dans ${Math.round(days / 30)} mois`;
}

export function DeadlineBadge({ severity, dueAt, className }: DeadlineBadgeProps) {
  return (
    <span
      title={formatDueAt(dueAt)}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium",
        severityClass[severity],
        className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-80" />
      {relativeFromNow(dueAt)}
    </span>
  );
}

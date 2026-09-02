import { useCallback, useEffect, useState } from "react";
import { Bell, CheckCircle2, Calendar, X } from "lucide-react";
import { deadlineService } from "../../services/deadline.service";
import type { Deadline } from "../../types/deadline";
import { DeadlineBadge } from "../ui/DeadlineBadge";
import { EmptyState } from "../ui/EmptyState";
import { ErrorState } from "../ui/ErrorState";
import { CardSkeleton } from "../ui/LoadingSkeleton";
import { cn } from "../../lib/utils";

interface DeadlinesPanelProps {
  className?: string;
  /** Limite le panneau a un ticket donne (sinon : workspace entier). */
  ticketId?: string;
  limit?: number;
}

type ViewState = "loading" | "ready" | "error";

/**
 * Panneau des deadlines (Killer Feature §4.2) avec les 4 etats UI
 * (loading / error / empty / success) conformes AGENT_BRIEF section 0.
 */
export function DeadlinesPanel({ className, ticketId, limit = 10 }: DeadlinesPanelProps) {
  const [items, setItems] = useState<Deadline[]>([]);
  const [state, setState] = useState<ViewState>("loading");

  const load = useCallback(async () => {
    setState("loading");
    try {
      const res = await deadlineService.list({ statut: "OUVERTE", limit });
      const filtered = ticketId ? res.items.filter((d) => d.ticketId === ticketId) : res.items;
      setItems(filtered);
      setState("ready");
    } catch {
      setState("error");
    }
  }, [ticketId, limit]);

  useEffect(() => {
    void load();
  }, [load]);

  const onComplete = async (id: string) => {
    const prev = items;
    setItems((curr) => curr.filter((d) => d.id !== id));
    try {
      await deadlineService.complete(id);
    } catch {
      setItems(prev);
    }
  };

  const onDismiss = async (id: string) => {
    const prev = items;
    setItems((curr) => curr.filter((d) => d.id !== id));
    try {
      await deadlineService.dismiss(id);
    } catch {
      setItems(prev);
    }
  };

  return (
    <section
      className={cn(
        "rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-raised)] p-4",
        className,
      )}
    >
      <header className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-[var(--color-fg)]">
          Echeances {ticketId ? "du ticket" : "ouvertes"}
        </h3>
        <span className="text-[11px] text-[var(--color-fg-muted)]">
          {state === "ready" && items.length > 0 ? `${items.length} ouverte${items.length > 1 ? "s" : ""}` : ""}
        </span>
      </header>

      {state === "loading" && (
        <div className="space-y-2">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      )}

      {state === "error" && (
        <ErrorState
          title="Echeances indisponibles"
          message="Impossible de charger les echeances. Reessayez ou contactez l'administrateur."
          onRetry={() => void load()}
        />
      )}

      {state === "ready" && items.length === 0 && (
        <EmptyState
          icon={<Calendar className="h-5 w-5" />}
          title="Aucune echeance ouverte"
          description="Tout est sous controle. Les echeances apparaitront ici des qu'une regle metier sera declenchee."
        />
      )}

      {state === "ready" && items.length > 0 && (
        <ul className="space-y-2">
          {items.map((d) => (
            <li
              key={d.id}
              className="flex items-start justify-between gap-3 rounded-md border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2.5 hover:border-[var(--color-border-hi)]"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <Bell className="h-3.5 w-3.5 text-[var(--color-fg-muted)]" />
                  <span className="truncate text-sm font-medium text-[var(--color-fg)]">{d.title}</span>
                  {d.source === "AUTO" && (
                    <span className="rounded bg-[var(--color-bg-overlay)] px-1.5 py-0.5 text-[10px] uppercase tracking-wider text-[var(--color-fg-muted)]">
                      auto
                    </span>
                  )}
                </div>
                {d.description && (
                  <p className="mt-1 truncate text-xs text-[var(--color-fg-muted)]">{d.description}</p>
                )}
                <div className="mt-1.5">
                  <DeadlineBadge severity={d.severity} dueAt={d.dueAt} />
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <button
                  type="button"
                  onClick={() => void onComplete(d.id)}
                  title="Marquer comme terminee"
                  className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-overlay)] hover:text-emerald-400"
                >
                  <CheckCircle2 className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => void onDismiss(d.id)}
                  title="Ignorer"
                  className="inline-flex h-7 w-7 items-center justify-center rounded-md text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-overlay)] hover:text-[var(--color-fg)]"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

import { useEffect, useState } from "react";
import { Calendar } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { deadlineService } from "../../services/deadline.service";

/**
 * Widget topbar : compteur des deadlines ouvertes pour le workspace courant.
 * Killer Feature §4.2 -- visibilite continue des echeances.
 *
 * BUG 9 (2026-06-08) — le widget est desormais cliquable et navigue vers
 * la page /calendar (vue mois interactive + actions complete/dismiss).
 */
export function DeadlineCountWidget() {
  const [count, setCount] = useState<number | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    const fetch = async () => {
      try {
        const n = await deadlineService.countOpen();
        if (!cancelled) setCount(n);
      } catch {
        if (!cancelled) setCount(null);
      }
    };
    void fetch();
    const t = setInterval(fetch, 60_000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, []);

  return (
    <button
      type="button"
      onClick={() => navigate("/calendar")}
      className="relative inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-bg-overlay hover:text-fg"
      aria-label={`${count ?? 0} échéance(s) ouverte(s) : ouvrir le calendrier`}
      title="Ouvrir le calendrier"
      data-testid="deadline-widget"
    >
      <Calendar className="h-5 w-5" aria-hidden="true" />
      {count !== null && count > 0 && (
        <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-warning px-1 text-[10px] font-bold text-warning-fg">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </button>
  );
}

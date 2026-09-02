import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Calendar as CalendarIcon,
  Check,
  ChevronLeft,
  ChevronRight,
  Loader2,
  X,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Drawer } from '../../components/ui/Drawer';
import { deadlineService } from '../../services/deadline.service';
import { ticketService } from '../../services/ticket.service';
import { extractError } from '../../lib/api';
import type { Deadline, DeadlineSeverity } from '../../types/deadline';
import type { Ticket } from '../../types/ticket';
import {
  PRIORITE_TOKENS,
  PRIORITE_ORDER,
  bucketTicketsByDay,
  dayKey,
  isTicketTermine,
  type DayBucket,
} from '../../lib/calendarBucketing';
import { STATUT_LABELS, TICKET_TYPE_LABELS } from '../../types/ticket';
import { TicketDetailDrawer } from '../tickets/TicketDetailDrawer';

/**
 * Page Calendrier (2026-06-25 — repurposing tickets).
 *
 * Vue par defaut = ECHEANCES DES TICKETS (source A : `ticket.deadline` +
 * `ticket.priorite` + `ticket.statut`). Chaque jour affiche les tickets dus,
 * separes par priorite (pastilles colorees + compteur). Clic sur un JOUR ->
 * panneau listant les tickets ; clic sur un TICKET -> meme detail que "Mes
 * tickets" (TicketDetailDrawer, gating role inchange).
 *
 *  - Scope employe/superviseur assure par le backend (GET /api/v1/tickets).
 *  - Toggle "Echeances metier" conserve l'ancienne vue Deadline (source B,
 *    severite) pour ne pas perdre l'existant (BUG 9 2026-06-08).
 *
 * Grille pur React + Tailwind (zero dependance externe).
 */

type ViewMode = 'month' | 'week';
type CalendarSource = 'tickets' | 'deadlines';

const SEVERITY_TOKENS: Record<DeadlineSeverity, { dot: string; chip: string; ring: string; label: string }> = {
  INFO: {
    dot: 'bg-sky-500',
    chip: 'bg-sky-500/15 text-sky-700 border-sky-200',
    ring: 'ring-sky-300',
    label: 'Info',
  },
  WARNING: {
    dot: 'bg-amber-500',
    chip: 'bg-amber-500/15 text-amber-800 border-amber-200',
    ring: 'ring-amber-300',
    label: 'Attention',
  },
  CRITICAL: {
    dot: 'bg-rose-600',
    chip: 'bg-rose-600/15 text-rose-700 border-rose-200',
    ring: 'ring-rose-300',
    label: 'Critique',
  },
};

const DAY_LABELS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

function startOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}
function endOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0, 23, 59, 59, 999);
}
function startOfWeek(d: Date): Date {
  // Lundi en jour 1 (ISO). getDay() : 0=dim, 1=lun, ...
  const dow = (d.getDay() + 6) % 7;
  const r = new Date(d);
  r.setDate(d.getDate() - dow);
  r.setHours(0, 0, 0, 0);
  return r;
}
function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setDate(d.getDate() + n);
  return r;
}
function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
function formatMonthYear(d: Date): string {
  return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
}

export function CalendarPage() {
  const [source, setSource] = useState<CalendarSource>('tickets');
  const [view, setView] = useState<ViewMode>('month');
  const [cursor, setCursor] = useState<Date>(() => new Date());
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [items, setItems] = useState<Deadline[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Vue tickets : jour selectionne (panneau liste) + ticket selectionne (drawer detail).
  const [selectedDay, setSelectedDay] = useState<Date | null>(null);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  // Vue echeances metier : detail Deadline (legacy).
  const [selectedDeadline, setSelectedDeadline] = useState<Deadline | null>(null);
  const [acting, setActing] = useState(false);

  const range = useMemo(() => {
    if (view === 'week') {
      const from = startOfWeek(cursor);
      const to = addDays(from, 6);
      to.setHours(23, 59, 59, 999);
      return { from, to };
    }
    // Mois : on cale sur la grille (lundi avant le 1er -> dimanche apres le dernier)
    const from = startOfWeek(startOfMonth(cursor));
    const last = endOfMonth(cursor);
    const dowLast = (last.getDay() + 6) % 7;
    const to = addDays(last, 6 - dowLast);
    to.setHours(23, 59, 59, 999);
    return { from, to };
  }, [view, cursor]);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (source === 'tickets') {
        // Scope own-vs-all gere cote serveur. Groupement client-side par jour.
        const r = await ticketService.list({ limit: 500 });
        setTickets(r.items);
      } else {
        const r = await deadlineService.list({
          from: range.from.toISOString(),
          to: range.to.toISOString(),
          limit: 500,
        });
        setItems(r.items);
      }
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [source, range.from, range.to]);

  useEffect(() => {
    void reload();
  }, [reload]);

  // --- Vue tickets : groupement par jour + priorite ---
  const ticketsByDay = useMemo(() => bucketTicketsByDay(tickets), [tickets]);

  // --- Vue echeances metier : groupement par jour + severite (legacy) ---
  const deadlinesByDay = useMemo(() => {
    const map = new Map<string, Deadline[]>();
    for (const it of items) {
      const d = new Date(it.dueAt);
      const key = dayKey(d);
      const arr = map.get(key) ?? [];
      arr.push(it);
      map.set(key, arr);
    }
    for (const arr of map.values()) {
      arr.sort((a, b) => {
        const sev = (x: DeadlineSeverity) => (x === 'CRITICAL' ? 0 : x === 'WARNING' ? 1 : 2);
        const s = sev(a.severity) - sev(b.severity);
        if (s !== 0) return s;
        return new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime();
      });
    }
    return map;
  }, [items]);

  const days = useMemo(() => {
    const out: Date[] = [];
    let d = new Date(range.from);
    while (d <= range.to) {
      out.push(new Date(d));
      d = addDays(d, 1);
    }
    return out;
  }, [range.from, range.to]);

  function navMonth(delta: number) {
    const next = new Date(cursor);
    next.setMonth(cursor.getMonth() + delta);
    setCursor(next);
  }
  function navWeek(delta: number) {
    setCursor(addDays(cursor, 7 * delta));
  }

  async function handleComplete(d: Deadline) {
    setActing(true);
    try {
      const r = await deadlineService.complete(d.id);
      setItems((prev) => prev.map((x) => (x.id === r.id ? r : x)));
      setSelectedDeadline(r);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setActing(false);
    }
  }
  async function handleDismiss(d: Deadline) {
    setActing(true);
    try {
      const r = await deadlineService.dismiss(d.id);
      setItems((prev) => prev.map((x) => (x.id === r.id ? r : x)));
      setSelectedDeadline(r);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setActing(false);
    }
  }

  const today = new Date();
  const selectedDayBucket: DayBucket | null =
    selectedDay != null ? ticketsByDay.get(dayKey(selectedDay)) ?? null : null;

  return (
    <div className="space-y-4" data-testid="calendar-page">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
            <CalendarIcon className="h-6 w-6 text-accent" /> Calendrier
          </h1>
          <p className="text-sm text-fg-muted">
            {source === 'tickets'
              ? 'Echeances des tickets — separees par priorite. Cliquez un jour pour la liste, un ticket pour le detail.'
              : 'Echeances metier ouvertes du workspace — cliquez une carte pour la traiter ou l ignorer.'}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Toggle source : tickets (defaut) vs echeances metier (legacy) */}
          <div className="flex items-center gap-1 rounded-lg border border-border bg-bg-raised p-1">
            <button
              type="button"
              onClick={() => setSource('tickets')}
              className={`rounded px-3 py-1 text-xs font-semibold ${
                source === 'tickets' ? 'bg-accent text-bg' : 'text-fg-muted hover:bg-bg-overlay'
              }`}
              data-testid="cal-source-tickets"
            >
              Tickets
            </button>
            <button
              type="button"
              onClick={() => setSource('deadlines')}
              className={`rounded px-3 py-1 text-xs font-semibold ${
                source === 'deadlines' ? 'bg-accent text-bg' : 'text-fg-muted hover:bg-bg-overlay'
              }`}
              data-testid="cal-source-deadlines"
            >
              Echeances metier
            </button>
          </div>

          <div className="flex items-center gap-1 rounded-lg border border-border bg-bg-raised p-1">
            <button
              type="button"
              onClick={() => setView('month')}
              className={`rounded px-3 py-1 text-xs font-semibold ${
                view === 'month' ? 'bg-accent text-bg' : 'text-fg-muted hover:bg-bg-overlay'
              }`}
              data-testid="cal-view-month"
            >
              Mois
            </button>
            <button
              type="button"
              onClick={() => setView('week')}
              className={`rounded px-3 py-1 text-xs font-semibold ${
                view === 'week' ? 'bg-accent text-bg' : 'text-fg-muted hover:bg-bg-overlay'
              }`}
              data-testid="cal-view-week"
            >
              Semaine
            </button>
          </div>

          <div className="flex items-center gap-1">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => (view === 'month' ? navMonth(-1) : navWeek(-1))}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setCursor(new Date())}>
              Aujourd'hui
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => (view === 'month' ? navMonth(1) : navWeek(1))}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
          <span className="rounded-lg border border-border bg-bg-raised px-3 py-1 text-sm font-semibold capitalize text-fg">
            {formatMonthYear(cursor)}
          </span>
        </div>
      </header>

      {/* Legende */}
      <div className="flex flex-wrap items-center gap-3 text-xs text-fg-muted">
        {source === 'tickets'
          ? PRIORITE_ORDER.map((p) => (
              <span key={p} className="flex items-center gap-1.5">
                <span className={`h-2 w-2 rounded-full ${PRIORITE_TOKENS[p].dot}`} />
                {PRIORITE_TOKENS[p].label}
              </span>
            ))
          : (Object.keys(SEVERITY_TOKENS) as DeadlineSeverity[]).map((s) => (
              <span key={s} className="flex items-center gap-1.5">
                <span className={`h-2 w-2 rounded-full ${SEVERITY_TOKENS[s].dot}`} />
                {SEVERITY_TOKENS[s].label}
              </span>
            ))}
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="h-4 w-4" /> {error}
        </div>
      )}

      {/* Grille */}
      <div className="overflow-hidden rounded-2xl border border-border bg-bg-raised">
        <div className="grid grid-cols-7 bg-bg-overlay">
          {DAY_LABELS.map((l) => (
            <div
              key={l}
              className="px-2 py-2 text-center text-[10px] font-bold uppercase tracking-wider text-fg-subtle"
            >
              {l}
            </div>
          ))}
        </div>

        {loading && tickets.length === 0 && items.length === 0 ? (
          <div className="flex h-64 items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-accent" />
          </div>
        ) : (
          <div className="grid grid-cols-7">
            {days.map((d, idx) => {
              const key = dayKey(d);
              const offMonth = view === 'month' && d.getMonth() !== cursor.getMonth();
              const isToday = sameDay(d, today);
              return (
                <div
                  key={idx}
                  data-testid="cal-day-cell"
                  className={`relative min-h-[110px] border border-border/40 p-1.5 text-xs ${
                    offMonth ? 'bg-bg-overlay/40 text-fg-subtle' : 'bg-bg-raised text-fg'
                  } ${isToday ? 'ring-2 ring-accent/40' : ''}`}
                >
                  {source === 'tickets'
                    ? renderTicketCell(d, key, isToday)
                    : renderDeadlineCell(d, key, isToday)}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Panneau liste des tickets d'un jour (vue tickets) */}
      <Drawer
        open={selectedDay != null}
        onClose={() => setSelectedDay(null)}
        title="Echeances du jour"
        subtitle={selectedDay ? selectedDay.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }) : undefined}
        width="md"
      >
        {selectedDayBucket && selectedDayBucket.tickets.length > 0 ? (
          <div className="space-y-2" data-testid="cal-day-panel">
            {selectedDayBucket.tickets.map((t) => {
              const tok = PRIORITE_TOKENS[t.priorite];
              const done = isTicketTermine(t);
              return (
                <button
                  key={t.id}
                  type="button"
                  data-testid="cal-day-ticket"
                  onClick={() => setSelectedTicketId(t.id)}
                  className={`flex w-full items-start gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-left transition hover:bg-bg-overlay ${
                    done ? 'opacity-60' : ''
                  }`}
                >
                  <span className={`mt-1 h-2 w-2 flex-shrink-0 rounded-full ${tok.dot}`} />
                  <span className="min-w-0 flex-1">
                    <span className={`block truncate text-sm font-medium text-fg ${done ? 'line-through' : ''}`}>
                      {t.titre}
                    </span>
                    <span className="mt-0.5 flex flex-wrap items-center gap-2 text-[11px] text-fg-subtle">
                      <span className="font-mono">{t.reference}</span>
                      <span>· {TICKET_TYPE_LABELS[t.type]}</span>
                      <span
                        className={`rounded-full border px-1.5 py-0.5 font-semibold ${tok.chip}`}
                      >
                        {tok.label}
                      </span>
                      <span>· {STATUT_LABELS[t.statut]}</span>
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <p className="py-8 text-center text-sm text-fg-subtle">Aucun ticket ce jour.</p>
        )}
      </Drawer>

      {/* Detail ticket (identique a "Mes tickets" — gating role inchange) */}
      {selectedTicketId && (
        <TicketDetailDrawer
          ticketId={selectedTicketId}
          onClose={() => setSelectedTicketId(null)}
          onChanged={reload}
        />
      )}

      {/* Drawer detail echeance metier (legacy) */}
      <Drawer
        open={!!selectedDeadline}
        onClose={() => setSelectedDeadline(null)}
        title="Detail de l'echeance"
        subtitle={selectedDeadline ? new Date(selectedDeadline.dueAt).toLocaleString('fr-FR') : undefined}
        width="md"
      >
        {selectedDeadline && (
          <div className="space-y-4">
            <div>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-semibold ${
                  SEVERITY_TOKENS[selectedDeadline.severity].chip
                }`}
              >
                <span className={`h-1.5 w-1.5 rounded-full ${SEVERITY_TOKENS[selectedDeadline.severity].dot}`} />
                {SEVERITY_TOKENS[selectedDeadline.severity].label}
              </span>
            </div>

            <h3 className="text-lg font-semibold text-fg">{selectedDeadline.title}</h3>
            {selectedDeadline.description && (
              <p className="whitespace-pre-wrap text-sm text-fg-muted">{selectedDeadline.description}</p>
            )}

            <dl className="space-y-2 rounded-lg border border-border bg-bg-overlay p-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-fg-subtle">Statut</dt>
                <dd className="font-medium text-fg">{selectedDeadline.statut}</dd>
              </div>
              {selectedDeadline.ruleKey && (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-subtle">Regle</dt>
                  <dd className="font-mono text-xs text-fg">{selectedDeadline.ruleKey}</dd>
                </div>
              )}
              <div className="flex justify-between gap-3">
                <dt className="text-fg-subtle">Source</dt>
                <dd className="text-fg">{selectedDeadline.source}</dd>
              </div>
              {selectedDeadline.ticketId && (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-subtle">Ticket</dt>
                  <dd className="font-mono text-xs text-fg">{selectedDeadline.ticketId.slice(0, 8)}</dd>
                </div>
              )}
            </dl>

            {selectedDeadline.statut === 'OUVERTE' && (
              <div className="flex gap-2">
                <Button
                  onClick={() => handleComplete(selectedDeadline)}
                  loading={acting}
                  disabled={acting}
                  data-testid="cal-action-complete"
                >
                  <Check className="mr-1 h-4 w-4" /> Marquer traitee
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => handleDismiss(selectedDeadline)}
                  loading={acting}
                  disabled={acting}
                  data-testid="cal-action-dismiss"
                >
                  <X className="mr-1 h-4 w-4" /> Ignorer
                </Button>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </div>
  );

  // --- Rendu d'une cellule jour (vue tickets) ---
  function renderTicketCell(d: Date, key: string, isToday: boolean) {
    const bucket = ticketsByDay.get(key);
    const dayTickets = bucket?.tickets ?? [];
    return (
      <>
        <button
          type="button"
          onClick={() => dayTickets.length > 0 && setSelectedDay(d)}
          disabled={dayTickets.length === 0}
          className="mb-1 flex w-full items-center justify-between disabled:cursor-default"
        >
          <span className={`text-[11px] font-semibold ${isToday ? 'text-accent' : ''}`}>
            {d.getDate()}
          </span>
          {dayTickets.length > 0 && (
            <span className="rounded-full bg-bg-overlay px-1.5 text-[9px] font-semibold text-fg-subtle">
              {dayTickets.length}
            </span>
          )}
        </button>

        {/* Repartition par priorite (pastilles + compteur) */}
        {bucket && bucket.total > 0 && (
          <div className="mb-1 flex flex-wrap items-center gap-1">
            {PRIORITE_ORDER.filter((p) => bucket.counts[p] > 0).map((p) => (
              <span
                key={p}
                className="flex items-center gap-0.5 text-[9px] text-fg-subtle"
                title={`${bucket.counts[p]} ${PRIORITE_TOKENS[p].label}`}
              >
                <span className={`h-1.5 w-1.5 rounded-full ${PRIORITE_TOKENS[p].dot}`} />
                {bucket.counts[p]}
              </span>
            ))}
          </div>
        )}

        <div className="flex flex-col gap-1">
          {dayTickets.slice(0, 3).map((t) => {
            const tok = PRIORITE_TOKENS[t.priorite];
            const done = isTicketTermine(t);
            return (
              <button
                key={t.id}
                type="button"
                data-testid="cal-ticket-chip"
                onClick={() => setSelectedTicketId(t.id)}
                className={`flex items-center gap-1.5 truncate rounded border px-1.5 py-0.5 text-[10px] font-medium ${tok.chip} ${
                  done ? 'opacity-50 line-through' : 'hover:ring-1'
                } ${tok.ring}`}
                title={`${t.reference} — ${t.titre}`}
              >
                <span className={`h-1.5 w-1.5 flex-shrink-0 rounded-full ${tok.dot}`} />
                <span className="truncate">{t.titre}</span>
              </button>
            );
          })}
          {dayTickets.length > 3 && (
            <button
              type="button"
              onClick={() => setSelectedDay(d)}
              className="text-[10px] text-fg-subtle hover:text-accent"
            >
              +{dayTickets.length - 3} autres
            </button>
          )}
        </div>
      </>
    );
  }

  // --- Rendu d'une cellule jour (vue echeances metier — legacy) ---
  function renderDeadlineCell(_d: Date, key: string, isToday: boolean) {
    const dayItems = deadlinesByDay.get(key) ?? [];
    return (
      <>
        <div className="mb-1 flex items-center justify-between">
          <span className={`text-[11px] font-semibold ${isToday ? 'text-accent' : ''}`}>
            {_d.getDate()}
          </span>
          {dayItems.length > 0 && <span className="text-[9px] text-fg-subtle">{dayItems.length}</span>}
        </div>
        <div className="flex flex-col gap-1">
          {dayItems.slice(0, 3).map((it) => {
            const tok = SEVERITY_TOKENS[it.severity];
            const done = it.statut !== 'OUVERTE';
            return (
              <button
                key={it.id}
                type="button"
                data-testid="cal-deadline-chip"
                onClick={() => setSelectedDeadline(it)}
                className={`flex items-center gap-1.5 truncate rounded border px-1.5 py-0.5 text-[10px] font-medium ${tok.chip} ${
                  done ? 'opacity-50 line-through' : 'hover:ring-1'
                } ${tok.ring}`}
                title={it.title}
              >
                <span className={`h-1.5 w-1.5 flex-shrink-0 rounded-full ${tok.dot}`} />
                <span className="truncate">{it.title}</span>
              </button>
            );
          })}
          {dayItems.length > 3 && (
            <button
              type="button"
              onClick={() => setSelectedDeadline(dayItems[3])}
              className="text-[10px] text-fg-subtle hover:text-accent"
            >
              +{dayItems.length - 3} autres
            </button>
          )}
        </div>
      </>
    );
  }
}

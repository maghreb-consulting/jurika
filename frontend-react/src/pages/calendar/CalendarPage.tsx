import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarX, Check, ChevronLeft, ChevronRight, X } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Drawer } from '../../components/ui/Drawer';
import { deadlineService } from '../../services/deadline.service';
import { ticketService } from '../../services/ticket.service';
import { extractError } from '../../lib/api';
import type { Deadline, DeadlineSeverity } from '../../types/deadline';
import type { Ticket, TicketPriorite } from '../../types/ticket';
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
import { TexteAide } from '../../components/ui/Aide';
import { useCurrentUser } from '../../store/authStore';
import { Page } from '../../components/layout/Page';
import { Message } from '../../components/ui/Message';
import { Lien } from '../../components/ui/Lien';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { ChoixSegmente } from '../../components/tickets/ChoixSegmente';

/** Lot L6 (E9) : libelles des codes d'une echeance (jamais le code brut a l'ecran). */
const STATUT_ECHEANCE: Record<string, string> = { OUVERTE: 'Ouverte', TERMINEE: 'Traitée', IGNOREE: 'Ignorée' };
const SOURCE_ECHEANCE: Record<string, string> = { AUTO: 'Automatique (règle du cabinet)', MANUAL: 'Saisie manuelle' };
const REGLE_ECHEANCE: Record<string, string> = {
  CN_EXPIRY_90D: 'Expiration du certificat négatif (90 jours)',
  RC_DEPOT_3M: 'Immatriculation au registre du commerce (3 mois)',
  CNSS_DECL_30D: 'Affiliation à la CNSS (30 jours)',
  CLOTURE_COMPTABLE_6M: 'Approbation des comptes annuels (6 mois)',
  LIQUIDATION_PUBLI_16J: 'Publication de la liquidation (16 jours)',
  STEP_STALE_7D: 'Ticket sans progression depuis 7 jours',
};

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

// Lot L6b (E7) : teintes des jetons (paires texte / fond des badges, contraste AA dans les
// deux themes) ; le libelle accompagne toujours la couleur.
const SEVERITY_TOKENS: Record<DeadlineSeverity, { chip: string; label: string }> = {
  INFO: { chip: 'bg-info-soft text-info border-info/30', label: 'Information' },
  WARNING: { chip: 'bg-warning-soft text-warning border-warning/30', label: 'Attention' },
  CRITICAL: { chip: 'bg-danger-soft text-danger border-danger/30', label: 'Critique' },
};

/** Teinte d'un ticket selon sa priorite (les libelles restent ceux de calendarBucketing). */
const PUCES_PRIORITE: Record<TicketPriorite, string> = {
  URGENTE: 'bg-danger-soft text-danger border-danger/30',
  HAUTE: 'bg-warning-soft text-warning border-warning/30',
  NORMALE: 'bg-info-soft text-info border-info/30',
  BASSE: 'bg-bg-overlay text-fg-muted border-border',
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
  // Lot L6b (E7, backlog L0) : traiter ou ignorer une echeance est refuse au superviseur par
  // le serveur (DeadlineController, EMPLOYE seul) : les boutons ne lui sont pas montres.
  const peutAgir = useCurrentUser()?.role === 'EMPLOYE';

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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(reload);
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
    <Page
      titre="Calendrier"
      description={
        source === 'tickets'
          ? 'Les échéances des tickets, par priorité. Cliquez sur un jour pour en voir la liste, sur un ticket pour son détail.'
          : 'Les échéances ouvertes du cabinet (délais légaux, formalités). Cliquez sur une échéance pour la marquer traitée ou l’ignorer.'
      }
      aide={
        <TexteAide cle="calendrier" titre="Lire le calendrier">
          <p>
            La vue « Tickets » place chaque ticket à sa date d’échéance, coloré selon sa priorité ; un
            ticket clos ou annulé est barré. La vue « Échéances du cabinet » réunit les délais à
            surveiller : marquez une échéance traitée une fois la formalité accomplie. Les flèches
            changent de mois ou de semaine ; « Aujourd’hui » y revient.
          </p>
        </TexteAide>
      }
    >
    <div className="space-y-4" data-testid="calendar-page">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <ChoixSegmente
            libelle="Ce que montre le calendrier"
            valeur={source}
            onChange={setSource}
            options={[
              { valeur: 'tickets', libelle: 'Tickets', testId: 'cal-source-tickets' },
              { valeur: 'deadlines', libelle: 'Échéances du cabinet', testId: 'cal-source-deadlines' },
            ]}
          />
          <ChoixSegmente
            libelle="Période affichée"
            valeur={view}
            onChange={setView}
            options={[
              { valeur: 'month', libelle: 'Mois', testId: 'cal-view-month' },
              { valeur: 'week', libelle: 'Semaine', testId: 'cal-view-week' },
            ]}
          />
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            iconOnly
            onClick={() => (view === 'month' ? navMonth(-1) : navWeek(-1))}
            aria-label="Période précédente"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={() => setCursor(new Date())}>
            Aujourd’hui
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            iconOnly
            onClick={() => (view === 'month' ? navMonth(1) : navWeek(1))}
            aria-label="Période suivante"
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </Button>
          <h2 className="min-w-[10rem] text-base font-semibold capitalize text-heading" aria-live="polite">
            {formatMonthYear(cursor)}
          </h2>
        </div>
      </div>

      {/* Legende : la couleur est toujours accompagnee de son libelle. */}
      <ul className="flex flex-wrap items-center gap-2 text-xs" aria-label="Légende">
        {source === 'tickets'
          ? PRIORITE_ORDER.map((p) => (
              <li key={p} className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 font-medium ${PUCES_PRIORITE[p]}`}>
                <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                Priorité {PRIORITE_TOKENS[p].label.toLowerCase()}
              </li>
            ))
          : (Object.keys(SEVERITY_TOKENS) as DeadlineSeverity[]).map((s) => (
              <li key={s} className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 font-medium ${SEVERITY_TOKENS[s].chip}`}>
                <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                {SEVERITY_TOKENS[s].label}
              </li>
            ))}
      </ul>

      {error && (
        <Message ton="erreur" onFermer={() => setError(null)}>
          {error}
        </Message>
      )}

      {/* Grille */}
      <div className="overflow-hidden rounded-xl border border-border bg-bg-raised shadow-card">
        <div className="grid grid-cols-7 border-b border-border bg-bg-overlay" aria-hidden="true">
          {DAY_LABELS.map((l) => (
            <div key={l} className="px-2 py-2 text-center text-xs font-semibold text-fg-muted">
              {l}
            </div>
          ))}
        </div>

        {loading && tickets.length === 0 && items.length === 0 ? (
          <SqueletteZone libelle="Chargement du calendrier…">
            <div className="grid grid-cols-7">
              {Array.from({ length: view === 'week' ? 7 : 35 }).map((_, i) => (
                <div key={i} className="min-h-[110px] border-b border-r border-border p-1.5">
                  <Skeleton className="h-3 w-5" />
                </div>
              ))}
            </div>
          </SqueletteZone>
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
                  aria-current={isToday ? 'date' : undefined}
                  className={`relative min-h-[110px] min-w-0 border-b border-r border-border p-1.5 text-xs ${
                    offMonth ? 'bg-bg-overlay text-fg-muted' : 'bg-bg-raised text-fg'
                  } ${isToday ? 'outline-2 -outline-offset-2 outline-accent' : ''}`}
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
        title="Échéances du jour"
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
                  className="flex w-full items-start gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-left transition-colors duration-150 hover:bg-bg-overlay"
                >
                  <span className="min-w-0 flex-1">
                    <span className={`block truncate text-sm font-medium text-fg ${done ? 'line-through' : ''}`}>
                      {t.titre}
                    </span>
                    <span className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-fg-muted">
                      <span className="font-mono">{t.reference}</span>
                      <span>· {TICKET_TYPE_LABELS[t.type]}</span>
                      <span
                        className={`rounded-full border px-1.5 py-0.5 font-semibold ${PUCES_PRIORITE[t.priorite]}`}
                      >
                        {tok.label}
                      </span>
                      <span>· {STATUT_LABELS[t.statut]}{done ? ' (terminé)' : ''}</span>
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <EmptyState compact icon={<CalendarX />} title="Aucun ticket ce jour" description="Aucun ticket n’arrive à échéance ce jour." />
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
        title="Détail de l’échéance"
        subtitle={selectedDeadline ? new Date(selectedDeadline.dueAt).toLocaleString('fr-FR') : undefined}
        width="md"
      >
        {selectedDeadline && (
          <div className="space-y-4">
            <div>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-semibold ${
                  SEVERITY_TOKENS[selectedDeadline.severity].chip
                }`}
              >
                <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                {SEVERITY_TOKENS[selectedDeadline.severity].label}
              </span>
            </div>

            <h3 className="text-lg font-semibold text-heading">{selectedDeadline.title}</h3>
            {selectedDeadline.description && (
              <p className="whitespace-pre-wrap text-sm text-fg-muted">{selectedDeadline.description}</p>
            )}

            <dl className="space-y-2 rounded-lg border border-border bg-bg-overlay p-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Statut</dt>
                <dd className="font-medium text-fg">{STATUT_ECHEANCE[selectedDeadline.statut] ?? selectedDeadline.statut}</dd>
              </div>
              {selectedDeadline.ruleKey && (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-muted">Règle</dt>
                  <dd className="text-xs text-fg">{REGLE_ECHEANCE[selectedDeadline.ruleKey] ?? selectedDeadline.ruleKey}</dd>
                </div>
              )}
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Source</dt>
                <dd className="text-fg">{SOURCE_ECHEANCE[selectedDeadline.source] ?? selectedDeadline.source}</dd>
              </div>
              {selectedDeadline.ticketId && (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-muted">Ticket</dt>
                  <dd className="text-fg">
                    <Lien to={`/tickets/${selectedDeadline.ticketId}`}>Ouvrir la page du ticket</Lien>
                  </dd>
                </div>
              )}
            </dl>

            {selectedDeadline.statut === 'OUVERTE' && peutAgir && (
              <div className="flex gap-2">
                <Button
                  type="button"
                  icon={<Check />}
                  onClick={() => handleComplete(selectedDeadline)}
                  loading={acting}
                  disabled={acting}
                  data-testid="cal-action-complete"
                >
                  Marquer comme traitée
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  icon={<X />}
                  onClick={() => handleDismiss(selectedDeadline)}
                  loading={acting}
                  disabled={acting}
                  data-testid="cal-action-dismiss"
                >
                  Ignorer
                </Button>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </div>
    </Page>
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
          aria-label={dayTickets.length > 0 ? `${d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })} : ${dayTickets.length} ticket(s), voir la liste` : undefined}
          className="mb-1 flex w-full items-center justify-between rounded disabled:cursor-default"
        >
          <span className={`text-xs font-semibold ${isToday ? 'text-accent' : ''}`}>
            {d.getDate()}
          </span>
          {dayTickets.length > 0 && (
            <span className="rounded-full bg-bg-overlay px-1.5 text-[11px] font-semibold text-fg-muted">
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
                className={`inline-flex items-center gap-0.5 rounded-full border px-1 text-[10px] font-semibold ${PUCES_PRIORITE[p]}`}
                title={`${bucket.counts[p]} ${PRIORITE_TOKENS[p].label}`}
              >
                <span className="sr-only">{PRIORITE_TOKENS[p].label} : </span>
                <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
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
                className={`flex min-w-0 items-center gap-1.5 rounded border px-1.5 py-0.5 text-left text-[11px] font-medium ${PUCES_PRIORITE[t.priorite]} ${
                  done ? 'line-through' : 'hover:border-current'
                }`}
                title={`${t.reference} — ${t.titre} (priorité ${tok.label.toLowerCase()}${done ? ', terminé' : ''})`}
              >
                <span aria-hidden="true" className="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-current" />
                <span className="truncate">{t.titre}</span>
                <span className="sr-only"> (priorité {tok.label.toLowerCase()}{done ? ', terminé' : ''})</span>
              </button>
            );
          })}
          {dayTickets.length > 3 && (
            <button
              type="button"
              onClick={() => setSelectedDay(d)}
              className="rounded text-left text-[11px] font-medium text-fg-muted underline underline-offset-2 hover:text-fg"
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
          <span className={`text-xs font-semibold ${isToday ? 'text-accent' : ''}`}>
            {_d.getDate()}
          </span>
          {dayItems.length > 0 && <span className="text-[11px] text-fg-muted">{dayItems.length}</span>}
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
                className={`flex min-w-0 items-center gap-1.5 rounded border px-1.5 py-0.5 text-left text-[11px] font-medium ${tok.chip} ${
                  done ? 'line-through' : 'hover:border-current'
                }`}
                title={`${it.title} (${tok.label.toLowerCase()})`}
              >
                <span aria-hidden="true" className="h-1.5 w-1.5 flex-shrink-0 rounded-full bg-current" />
                <span className="truncate">{it.title}</span>
                <span className="sr-only"> ({tok.label.toLowerCase()})</span>
              </button>
            );
          })}
          {dayItems.length > 3 && (
            <button
              type="button"
              onClick={() => setSelectedDeadline(dayItems[3])}
              className="rounded text-left text-[11px] font-medium text-fg-muted underline underline-offset-2 hover:text-fg"
            >
              +{dayItems.length - 3} autres
            </button>
          )}
        </div>
      </>
    );
  }
}

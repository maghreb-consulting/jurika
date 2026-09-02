import { useMemo, useState, type ReactNode } from 'react';
import { ChevronDown, ChevronRight, FileText, History } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import type { DocumentSummary, TicketHistoryEntry } from '../../../types/dataroom';

/**
 * Sprint 7 / TASK 2 -- Frise chronologique des operations cloturees,
 * groupee par mois (YYYY-MM) avec rail vertical et markers.
 *
 * Contrat :
 *   - entries TRIES par clotureAt DESC (responsabilite du backend)
 *   - groupage stable par mois calendaire ('YYYY-MM')
 *   - entries sans clotureAt -> bucket 'Sans date'
 *
 * Empty state autonome ; les filtres sont externes (TimelineFiltersDrawer).
 */
export interface OperationsTimelineProps {
  entries: TicketHistoryEntry[];
  renderDocumentRow: (doc: DocumentSummary) => ReactNode;
  emptyTitle?: string;
  emptyHint?: string;
  /**
   * Fix DR6 (2026-08-16) — révélation progressive de l'historique.
   *
   * Toute la collection était rendue d'un coup : sur un dossier ayant vécu une
   * dizaine d'opérations, la fiche devenait un mur à faire défiler, et les
   * documents en vigueur — l'information la plus utile — se retrouvaient repoussés
   * hors de l'écran. On affiche les N plus récentes, un bouton révélant la suite
   * par paliers jusqu'à tout afficher. RIEN n'est masqué définitivement.
   */
  initialCount?: number;
  /** Nombre d'opérations révélées à chaque clic sur « Voir plus ». */
  stepCount?: number;
}

const MONTH_LABEL_FORMATTER = new Intl.DateTimeFormat('fr-FR', {
  month: 'long',
  year: 'numeric',
});

/** Groupage helper exporte pour test unitaire. */
export function groupByMonth(
  entries: TicketHistoryEntry[],
): { monthKey: string; label: string; items: TicketHistoryEntry[] }[] {
  const buckets = new Map<string, TicketHistoryEntry[]>();
  const NO_DATE = '0000-00';
  for (const e of entries) {
    let key = NO_DATE;
    if (e.clotureAt) {
      const d = new Date(e.clotureAt);
      if (!Number.isNaN(d.getTime())) {
        const y = d.getUTCFullYear();
        const m = String(d.getUTCMonth() + 1).padStart(2, '0');
        key = `${y}-${m}`;
      }
    }
    const arr = buckets.get(key);
    if (arr) arr.push(e);
    else buckets.set(key, [e]);
  }
  // Sort keys DESC -- NO_DATE bucket goes to the end
  const sortedKeys = Array.from(buckets.keys()).sort((a, b) => {
    if (a === NO_DATE) return 1;
    if (b === NO_DATE) return -1;
    return b.localeCompare(a);
  });
  return sortedKeys.map((monthKey) => {
    let label = 'Sans date';
    if (monthKey !== NO_DATE) {
      const [y, m] = monthKey.split('-');
      const dateForLabel = new Date(Date.UTC(Number(y), Number(m) - 1, 1));
      label = MONTH_LABEL_FORMATTER.format(dateForLabel);
      // Capitalize first letter ("mai 2026" -> "Mai 2026")
      label = label.charAt(0).toUpperCase() + label.slice(1);
    }
    return { monthKey, label, items: buckets.get(monthKey) ?? [] };
  });
}

const TYPE_BADGE_VARIANTS: Record<string, 'success' | 'warning' | 'neutral'> = {
  CREATION: 'success',
  MODIFICATION: 'warning',
  DISSOLUTION: 'warning',
  LIQUIDATION: 'warning',
  AGO: 'neutral',
};

function typeBadgeVariant(type?: string | null): 'success' | 'warning' | 'neutral' {
  if (!type) return 'neutral';
  return TYPE_BADGE_VARIANTS[type.toUpperCase()] ?? 'neutral';
}

export function OperationsTimeline({
  entries,
  renderDocumentRow,
  emptyTitle = 'Aucune operation a afficher',
  emptyHint = 'Les operations cloturees apparaitront ici, groupees par mois.',
  initialCount = 5,
  stepCount = 5,
}: OperationsTimelineProps) {
  // Fix DR6 — `entries` arrive déjà triée du plus récent au plus ancien
  // (responsabilité backend) : tronquer en tête garde bien les plus récentes.
  const [visibles, setVisibles] = useState(initialCount);
  const affichees = useMemo(() => entries.slice(0, visibles), [entries, visibles]);
  const restants = entries.length - affichees.length;
  const grouped = useMemo(() => groupByMonth(affichees), [affichees]);

  if (entries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 px-5 py-10 text-center">
        <History className="h-8 w-8 text-fg-subtle" />
        <p className="text-sm font-medium text-fg-muted">{emptyTitle}</p>
        <p className="max-w-sm text-xs text-fg-subtle">{emptyHint}</p>
      </div>
    );
  }

  return (
    <div className="px-5 py-4">
      {grouped.map((group) => (
        <section key={group.monthKey} className="mb-6 last:mb-0">
          <h4 className="mb-3 text-xs font-semibold uppercase tracking-wider text-fg-subtle">
            {group.label}
            <span className="ml-2 rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-bold text-fg-muted">
              {group.items.length}
            </span>
          </h4>
          <ol className="relative ml-2 space-y-3 border-l-2 border-border pl-5">
            {group.items.map((entry) => (
              <TimelineEvent
                key={entry.ticketId}
                entry={entry}
                renderDocumentRow={renderDocumentRow}
              />
            ))}
          </ol>
        </section>
      ))}
      {/* Fix DR6 — le reste de l'historique reste toujours atteignable. */}
      {restants > 0 && (
        <div className="pt-1">
          <button
            type="button"
            data-testid="timeline-voir-plus"
            onClick={() => setVisibles((n) => n + stepCount)}
            className="w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-xs font-medium text-fg-muted transition hover:border-accent hover:text-fg"
          >
            Voir plus (+{Math.min(restants, stepCount)}) — {restants} operation
            {restants > 1 ? 's' : ''} plus ancienne{restants > 1 ? 's' : ''}
          </button>
        </div>
      )}
    </div>
  );
}

function TimelineEvent({
  entry,
  renderDocumentRow,
}: {
  entry: TicketHistoryEntry;
  renderDocumentRow: (doc: DocumentSummary) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const replacedCount = entry.replacedDocuments.length;
  const generatedCount = entry.generatedDocuments.length;
  const dateLabel = entry.clotureAt
    ? new Date(entry.clotureAt).toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: 'short',
      })
    : '—';

  return (
    <li className="relative">
      {/* Marker (cercle) sur le rail */}
      <span
        className="absolute -left-[27px] top-2 inline-block h-3 w-3 rounded-full border-2 border-white bg-accent shadow-sm"
        aria-hidden
      />
      <div className="rounded-lg border border-border bg-bg-raised">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="flex w-full items-start justify-between gap-3 px-3 py-2.5 text-left"
        >
          <div className="flex items-start gap-2">
            {open ? (
              <ChevronDown className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" />
            ) : (
              <ChevronRight className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" />
            )}
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <p className="truncate text-sm font-medium text-fg">
                  {entry.titre}
                </p>
                <Badge variant={typeBadgeVariant(entry.type)} className="text-[10px]">
                  {entry.type ?? '—'}
                </Badge>
                <Badge variant="neutral" className="text-[10px]">
                  {entry.reference}
                </Badge>
              </div>
              <p className="text-xs text-fg-subtle">
                Cloture le {dateLabel}
                {entry.description ? ` • ${entry.description}` : ''}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            {generatedCount > 0 && (
              <Badge variant="success" className="text-[10px]">
                +{generatedCount} genere{generatedCount > 1 ? 's' : ''}
              </Badge>
            )}
            {replacedCount > 0 && (
              <Badge variant="warning" className="text-[10px]">
                {replacedCount} ancien{replacedCount > 1 ? 's' : ''}
              </Badge>
            )}
          </div>
        </button>
        {open && (
          <div className="space-y-3 border-t border-border px-3 pb-3 pt-2">
            {generatedCount === 0 && replacedCount === 0 && (
              <p className="rounded-md border border-border bg-bg-overlay px-3 py-2 text-xs italic text-fg-subtle">
                Operation sans modification de document.
              </p>
            )}
            {generatedCount > 0 && (
              <div>
                <p className="mb-1 flex items-center gap-1 text-xs font-semibold uppercase tracking-wider text-success">
                  <FileText className="h-3.5 w-3.5" /> Documents generes
                </p>
                <ul className="divide-y divide-border rounded-md border border-border">
                  {entry.generatedDocuments.map((d) => renderDocumentRow(d))}
                </ul>
              </div>
            )}
            {replacedCount > 0 && (
              <div>
                <p className="mb-1 flex items-center gap-1 text-xs font-semibold uppercase tracking-wider text-warning">
                  <History className="h-3.5 w-3.5" /> Anciennes versions remplacees
                </p>
                <ul className="divide-y divide-border rounded-md border border-border">
                  {entry.replacedDocuments.map((d) => renderDocumentRow(d))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

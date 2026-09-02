import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Download, FileText, RefreshCcw } from 'lucide-react';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import {
  generateDocument,
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../services/workflowDocumentService';

interface Props {
  /**
   * Liste des codes workflows pour lesquels charger les templates.
   * Le panneau merge la liste totale et deduplique par templateCode.
   */
  workflowCodes: string[];
  /**
   * Builder du payload appele a la volee au clic Generer.
   * Le parent est responsable de la validation/transformation metier.
   */
  buildPayload: () => Record<string, unknown>;
  /**
   * Callback optionnel post-generation (audit UI, tracking, etc.).
   */
  onGenerated?: (templateCode: string, blob: Blob) => void;
  /**
   * Transmis au backend dans le payload pour enrichir l'identite societe depuis la
   * BD (capital, siege, RC, ville du greffe, parts...). La BD gagne uniquement sur
   * l'identite ; le reste du payload metier reste source de verite.
   */
  dossierId?: string;
}

interface MergedTemplate extends TemplateInfo {
  workflowCode: string;
}

function originBadgeClasses(origin: string, deprecated: boolean): string {
  if (deprecated) {
    return 'bg-amber-100 text-amber-800 border border-amber-300';
  }
  const normalized = (origin ?? '').toLowerCase();
  if (normalized === 'directeur' || normalized === 'director') {
    return 'bg-emerald-100 text-emerald-800 border border-emerald-300';
  }
  if (normalized === 'herite' || normalized === 'hérité' || normalized === 'inherited') {
    return 'bg-amber-100 text-amber-800 border border-amber-300';
  }
  return 'bg-sky-100 text-sky-800 border border-sky-300';
}

function triggerBrowserDownload(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Defer revoke to next tick — IE/Edge legacy used to lose the download
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

function SkeletonCard() {
  return (
    <div className="rounded-2xl border border-border bg-bg-raised p-5 shadow-card">
      <div className="h-4 w-2/3 animate-pulse rounded bg-bg-overlay" />
      <div className="mt-3 h-3 w-1/3 animate-pulse rounded bg-bg-overlay" />
      <div className="mt-6 h-9 w-full animate-pulse rounded bg-bg-overlay" />
    </div>
  );
}

/**
 * GenerateDocumentPanel — composant 100% generique pour rendre la liste des
 * documents disponibles d'un (ou plusieurs) workflow(s), et declencher leur
 * generation backend (DOCX). AUCUNE logique metier ici : le parent passe
 * `buildPayload`. Style aligne sur le design system existant (Card / Button
 * tokens). Accessible (aria-label sur boutons d'action).
 */
export function GenerateDocumentPanel({
  workflowCodes,
  buildPayload,
  onGenerated,
  dossierId,
}: Props) {
  const [templates, setTemplates] = useState<MergedTemplate[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [generating, setGenerating] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  const [successAt, setSuccessAt] = useState<Record<string, number>>({});

  const successTimers = useRef<Record<string, number>>({});

  const codesKey = useMemo(() => [...workflowCodes].sort().join('|'), [workflowCodes]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);

    const codes = workflowCodes ?? [];
    if (codes.length === 0) {
      setTemplates([]);
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }

    Promise.allSettled(
      codes.map((code) =>
        listTemplatesForWorkflow(code).then((items) =>
          items.map<MergedTemplate>((t) => ({ ...t, workflowCode: code })),
        ),
      ),
    )
      .then((results) => {
        if (cancelled) return;
        const merged: MergedTemplate[] = [];
        const seen = new Set<string>();
        let firstError: string | null = null;
        results.forEach((r) => {
          if (r.status === 'fulfilled') {
            r.value.forEach((tpl) => {
              if (!seen.has(tpl.code)) {
                seen.add(tpl.code);
                merged.push(tpl);
              }
            });
          } else if (!firstError) {
            firstError = 'Impossible de charger la liste des documents.';
          }
        });
        setTemplates(merged);
        if (merged.length === 0 && firstError) setLoadError(firstError);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [codesKey, workflowCodes]);

  useEffect(() => {
    const timers = successTimers.current;
    return () => {
      Object.values(timers).forEach((id) => window.clearTimeout(id));
    };
  }, []);

  const handleGenerate = useCallback(
    async (tpl: MergedTemplate) => {
      setGenerating((prev) => ({ ...prev, [tpl.code]: true }));
      setErrors((prev) => ({ ...prev, [tpl.code]: null }));
      try {
        const payload = buildPayload();
        // dossierId → enrichissement identite societe depuis la BD cote backend.
        if (dossierId && payload.dossierId == null) payload.dossierId = dossierId;
        const { blob, filename } = await generateDocument(
          tpl.workflowCode,
          tpl.code,
          payload,
        );
        triggerBrowserDownload(blob, filename);
        onGenerated?.(tpl.code, blob);
        const ts = Date.now();
        setSuccessAt((prev) => ({ ...prev, [tpl.code]: ts }));
        const previousTimer = successTimers.current[tpl.code];
        if (previousTimer) window.clearTimeout(previousTimer);
        successTimers.current[tpl.code] = window.setTimeout(() => {
          setSuccessAt((prev) => {
            if (prev[tpl.code] !== ts) return prev;
            const next = { ...prev };
            delete next[tpl.code];
            return next;
          });
        }, 3000);
      } catch (err) {
        const message =
          err instanceof Error ? err.message : 'Erreur lors de la generation';
        setErrors((prev) => ({ ...prev, [tpl.code]: message }));
      } finally {
        setGenerating((prev) => ({ ...prev, [tpl.code]: false }));
      }
    },
    [buildPayload, onGenerated, dossierId],
  );

  if (loading) {
    return (
      <div
        className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
        data-testid="generate-document-panel-loading"
      >
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    );
  }

  if (templates.length === 0) {
    return (
      <EmptyState
        icon={<FileText className="h-5 w-5" aria-hidden />}
        title="Aucun document disponible"
        description={
          loadError ??
          'Aucun document disponible pour ce workflow (manifest vide).'
        }
      />
    );
  }

  return (
    <div
      className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
      data-testid="generate-document-panel"
    >
      {templates.map((tpl) => {
        const isGenerating = !!generating[tpl.code];
        const error = errors[tpl.code];
        const success = !!successAt[tpl.code];
        const title = tpl.documentKind || tpl.code;
        return (
          <Card key={tpl.code} className="flex flex-col gap-3 p-5">
            <div className="flex items-start justify-between gap-2">
              <h3
                className="text-sm font-semibold text-fg"
                title={tpl.code}
              >
                {title}
              </h3>
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${originBadgeClasses(tpl.origin, tpl.deprecated)}`}
              >
                {tpl.deprecated ? 'hérité' : tpl.origin || 'interne'}
              </span>
            </div>
            <p className="text-xs text-fg-subtle">{tpl.code}</p>

            {error ? (
              <div
                className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-2 text-xs text-danger"
                role="alert"
              >
                <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                <span className="flex-1">{error}</span>
              </div>
            ) : null}

            <div className="mt-auto flex items-center gap-2">
              <Button
                variant={success ? 'secondary' : 'primary'}
                size="sm"
                className="flex-1"
                onClick={() => handleGenerate(tpl)}
                loading={isGenerating}
                disabled={isGenerating}
                aria-label={`Générer le document ${title}`}
                data-testid={`generate-btn-${tpl.code}`}
              >
                {success ? (
                  <>
                    <CheckCircle2 className="mr-1.5 h-4 w-4" aria-hidden />
                    Téléchargé
                  </>
                ) : error ? (
                  <>
                    <RefreshCcw className="mr-1.5 h-4 w-4" aria-hidden />
                    Réessayer
                  </>
                ) : (
                  <>
                    <Download className="mr-1.5 h-4 w-4" aria-hidden />
                    Générer le document
                  </>
                )}
              </Button>
            </div>
          </Card>
        );
      })}
    </div>
  );
}

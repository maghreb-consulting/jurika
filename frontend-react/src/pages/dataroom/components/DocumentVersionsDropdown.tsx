import { useCallback, useState } from 'react';
import { AlertCircle, ChevronDown, ChevronRight, Download, Eye, History, Loader2, RotateCcw } from 'lucide-react';
import { dataroomService } from '../../../services/dataroom.service';
import { PdfPreviewModal } from '../../../components/ui/PdfPreviewModal';
import { PromptDialog } from '../../../components/ui/PromptDialog';
import type { DocumentSummary } from '../../../types/dataroom';

/**
 * Sprint 2026-06-23 — Disclosure des anciennes versions d'un Document logique.
 *
 * <p>Lazy-load à la 1re ouverture (appelle {@link dataroomService.listVersions}).
 * Pour chaque version <b>NON courante</b> : date + motif + Télécharger + Restaurer.
 * La version active est exclue de la liste (déjà visible dans la rangée parent).
 *
 * <p>Restauration : confirm prompt + motif libre → {@link dataroomService.restoreVersion}.
 * Émet {@code onChanged} pour permettre au parent de recharger la liste des
 * documents actifs (la version restaurée prend la place de l'ancienne actuelle).
 */
export interface DocumentVersionsDropdownProps {
  /** Une des versions du Document logique (typiquement la version active). */
  documentId: string;
  /** Permission EMPLOYE : autorise la restauration. CLIENT/SUPERVISEUR = lecture seule. */
  canRestore?: boolean;
  /** Callback après restauration : le parent doit recharger la liste des docs. */
  onChanged?: () => void | Promise<void>;
}

export function DocumentVersionsDropdown({
  documentId,
  canRestore = false,
  onChanged,
}: DocumentVersionsDropdownProps) {
  const [open, setOpen] = useState(false);
  const [versions, setVersions] = useState<DocumentSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restoringId, setRestoringId] = useState<string | null>(null);
  // Version ciblée par le dialog de saisie du motif (remplace window.prompt).
  const [restoreTarget, setRestoreTarget] = useState<DocumentSummary | null>(null);
  /**
   * Fix DR4 (2026-08-16) — les anciennes versions n'avaient QUE « Télécharger ».
   * Relire un statut remplacé — le geste même du contrôle juridique — imposait de
   * sortir de l'application. Elles sont désormais consultables sur place, y compris
   * les `.docx` (convertis en PDF à la volée côté serveur).
   */
  const [previewTarget, setPreviewTarget] = useState<DocumentSummary | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await dataroomService.listVersions(documentId);
      setVersions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Echec du chargement des versions');
    } finally {
      setLoading(false);
    }
  }, [documentId]);

  async function toggle() {
    if (!open && versions === null) {
      await load();
    }
    setOpen((o) => !o);
  }

  async function download(v: DocumentSummary) {
    try {
      await dataroomService.downloadVersion(documentId, v.id, v.filename);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Echec du téléchargement');
    }
  }

  // Ouvre le dialog de saisie du motif (le motif reste facultatif : chaîne vide
  // acceptée, comme l'ancien window.prompt qui n'interdisait que l'annulation).
  function restore(v: DocumentSummary) {
    if (!canRestore) return;
    setRestoreTarget(v);
  }

  async function submitRestore(reason: string) {
    const v = restoreTarget;
    if (!v) return;
    setRestoreTarget(null);
    setRestoringId(v.id);
    setError(null);
    try {
      await dataroomService.restoreVersion(documentId, v.id, reason || undefined);
      await load();
      await onChanged?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Echec de la restauration');
    } finally {
      setRestoringId(null);
    }
  }

  const history = (versions ?? []).filter((v) => !v.current);

  return (
    <div className="mt-1" data-testid={`versions-dropdown-${documentId}`}>
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        data-testid="versions-toggle"
        className="flex items-center gap-1 rounded px-2 py-1 text-xs text-fg-subtle hover:bg-bg-overlay"
      >
        {open ? (
          <ChevronDown className="h-3.5 w-3.5" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5" />
        )}
        <History className="h-3.5 w-3.5" />
        Anciennes versions
      </button>

      {open && (
        <div className="ml-5 mt-1 rounded-lg border border-border bg-bg-overlay p-2">
          {loading && (
            <p className="flex items-center gap-2 text-xs text-fg-subtle">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Chargement…
            </p>
          )}
          {error && (
            <p role="alert" className="flex items-start gap-1 text-xs text-danger">
              <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" /> {error}
            </p>
          )}
          {!loading && !error && history.length === 0 && (
            <p className="text-xs text-fg-subtle" data-testid="versions-empty">
              Aucune ancienne version pour ce document.
            </p>
          )}
          {!loading && !error && history.length > 0 && (
            <ul className="divide-y divide-border">
              {history.map((v) => (
                <li
                  key={v.id}
                  data-testid={`version-row-${v.id}`}
                  className="flex items-center justify-between gap-2 py-2"
                >
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-2 text-xs font-medium text-fg">
                      v{v.version}
                      <span className="text-[10px] font-normal text-fg-subtle">
                        {v.replacedAt
                          ? new Date(v.replacedAt).toLocaleString('fr-FR')
                          : new Date(v.createdAt).toLocaleString('fr-FR')}
                      </span>
                    </p>
                    {v.motif && (
                      <p className="mt-0.5 truncate text-[11px] text-fg-muted" title={v.motif}>
                        {v.motif}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setPreviewTarget(v)}
                      data-testid={`preview-version-${v.id}`}
                      title="Apercu de cette version"
                      className="rounded p-1 text-fg-subtle hover:bg-bg-raised hover:text-fg"
                    >
                      <Eye className="h-3.5 w-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => download(v)}
                      data-testid={`download-version-${v.id}`}
                      title="Télécharger cette version"
                      className="rounded p-1 text-fg-subtle hover:bg-bg-raised hover:text-fg"
                    >
                      <Download className="h-3.5 w-3.5" />
                    </button>
                    {canRestore && (
                      <button
                        type="button"
                        onClick={() => restore(v)}
                        disabled={restoringId === v.id}
                        data-testid={`restore-version-${v.id}`}
                        title="Restaurer comme version active"
                        className={`rounded p-1 ${
                          restoringId === v.id
                            ? 'cursor-wait text-fg-subtle'
                            : 'text-accent hover:bg-bg-raised'
                        }`}
                      >
                        {restoringId === v.id ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <RotateCcw className="h-3.5 w-3.5" />
                        )}
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Fix DR4 — apercu inline d'une ancienne version (Office converti en PDF). */}
      <PdfPreviewModal
        open={!!previewTarget}
        documentId={previewTarget?.id ?? null}
        filename={previewTarget?.filename ?? previewTarget?.title ?? 'document'}
        canDownload
        onClose={() => setPreviewTarget(null)}
        onDownload={() => {
          if (previewTarget) void download(previewTarget);
        }}
        fetchPreview={(versionId) => dataroomService.previewVersion(documentId, versionId)}
      />

      {restoreTarget && (
        <PromptDialog
          open
          onOpenChange={(o) => {
            if (!o) setRestoreTarget(null);
          }}
          title={`Restaurer la version v${restoreTarget.version} ?`}
          label="Motif (facultatif) :"
          defaultValue={`Restauration de la version ${restoreTarget.version}`}
          multiline
          confirmLabel="Restaurer"
          loading={restoringId === restoreTarget.id}
          onConfirm={(reason) => void submitRestore(reason)}
        />
      )}
    </div>
  );
}

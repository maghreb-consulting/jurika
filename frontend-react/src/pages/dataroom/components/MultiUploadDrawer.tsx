import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, Check, Loader2, Trash2, Upload } from 'lucide-react';
import { Drawer } from '../../../components/ui/Drawer';
import { Button } from '../../../components/ui/Button';
import { extractError } from '../../../lib/api';
import {
  isMotifValid,
  MOTIF_MIN_LENGTH,
  stripExtension,
  uploadOrReplace,
} from '../../../lib/dataroomUpload';
import type { DocumentSummary } from '../../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../../types/dataroom';

/**
 * Sprint 2026-06-24 — Upload MULTIPLE aligné sur l'upload SIMPLE.
 *
 * <p>Pour CHAQUE fichier ajouté, l'utilisateur choisit :
 * <ul>
 *   <li><b>« Nouveau document »</b> (défaut) — crée un Document logique distinct
 *       (type + titre dérivé du nom de fichier) ;</li>
 *   <li><b>« Nouvelle version de … »</b> — remplace un Document ACTIF du dossier
 *       (motif obligatoire ≥ {@link MOTIF_MIN_LENGTH} caractères).</li>
 * </ul>
 *
 * <p>La soumission est BLOQUÉE tant qu'un fichier « version » n'a pas de cible
 * ou un motif valide. Le traitement est SÉQUENTIEL (pattern du projet, cf.
 * {@code uploadJuridiqueBatch} historique) et chaque ligne reçoit son propre
 * statut succès/erreur, suivi d'un récapitulatif.
 *
 * <p>Aucune logique de versioning n'est dupliquée : tout passe par
 * {@link uploadOrReplace} (même helper que {@code UploadDocumentDialog}).
 */

const NEW_DOCUMENT_VALUE = '__NEW__';

const DOCUMENT_TYPE_OPTIONS = Object.entries(DOCUMENT_TYPE_LABELS).map(
  ([value, label]) => ({ value, label }),
);

type RowStatus = 'PENDING' | 'UPLOADING' | 'DONE' | 'ERROR';

interface UploadRow {
  id: string;
  file: File;
  mode: 'new' | 'version';
  /** mode 'new' : type du nouveau document. */
  documentType: string;
  /** mode 'version' : id du Document logique à remplacer. */
  targetDocId: string;
  /** mode 'version' : motif du remplacement. */
  motif: string;
  status: RowStatus;
  error?: string;
}

export interface MultiUploadDrawerProps {
  open: boolean;
  dossierId: string;
  /** Documents en vigueur du dossier (cibles possibles pour « Nouvelle version »). */
  activeDocuments: DocumentSummary[];
  onClose: () => void;
  onUploaded: () => Promise<void> | void;
}

function rowUid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `row-${Date.now()}-${Math.round(Math.random() * 1e9)}`;
}

export function MultiUploadDrawer({
  open,
  dossierId,
  activeDocuments,
  onClose,
  onUploaded,
}: MultiUploadDrawerProps) {
  const [rows, setRows] = useState<UploadRow[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [recap, setRecap] = useState<{ ok: number; ko: number } | null>(null);

  useEffect(() => {
    if (open) {
      setRows([]);
      setSubmitting(false);
      setRecap(null);
    }
  }, [open]);

  function addFiles(list: FileList | null) {
    if (!list) return;
    const next: UploadRow[] = Array.from(list).map((file) => ({
      id: rowUid(),
      file,
      mode: 'new',
      documentType: 'AUTRE',
      targetDocId: '',
      motif: '',
      status: 'PENDING',
    }));
    setRows((prev) => [...prev, ...next]);
    setRecap(null);
  }

  function patchRow(id: string, patch: Partial<UploadRow>) {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  }

  function removeRow(id: string) {
    setRows((prev) => prev.filter((r) => r.id !== id));
  }

  function onActionChange(id: string, value: string) {
    if (value === NEW_DOCUMENT_VALUE) {
      patchRow(id, { mode: 'new', targetDocId: '', motif: '' });
    } else {
      patchRow(id, { mode: 'version', targetDocId: value });
    }
  }

  /** Une ligne « version » est valide si elle a une cible + un motif suffisant. */
  function rowReady(r: UploadRow): boolean {
    if (r.mode === 'new') return true;
    return r.targetDocId !== '' && isMotifValid(r.motif);
  }

  const pendingRows = useMemo(
    () => rows.filter((r) => r.status !== 'DONE'),
    [rows],
  );

  const canSubmit = useMemo(() => {
    if (submitting) return false;
    if (pendingRows.length === 0) return false;
    return pendingRows.every(rowReady);
  }, [submitting, pendingRows]);

  async function submit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setRecap(null);
    let ok = 0;
    let ko = 0;
    // Traitement séquentiel : on relit l'état courant des lignes à chaque tour.
    const snapshot = rows.filter((r) => r.status !== 'DONE');
    for (const row of snapshot) {
      patchRow(row.id, { status: 'UPLOADING', error: undefined });
      try {
        await uploadOrReplace(
          row.file,
          row.mode === 'version'
            ? {
                dossierId,
                mode: 'version',
                targetDocId: row.targetDocId,
                motif: row.motif,
              }
            : {
                dossierId,
                mode: 'new',
                documentType: row.documentType,
                title: stripExtension(row.file.name),
              },
        );
        patchRow(row.id, { status: 'DONE' });
        ok += 1;
      } catch (err) {
        patchRow(row.id, { status: 'ERROR', error: extractError(err).message });
        ko += 1;
      }
    }
    setSubmitting(false);
    setRecap({ ok, ko });
    // Rafraîchit la liste sous-jacente même en cas d'échec partiel.
    await onUploaded();
  }

  const allDone = rows.length > 0 && rows.every((r) => r.status === 'DONE');

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Upload multiple"
      subtitle="Pour chaque fichier : nouveau document, ou nouvelle version d'un document existant"
      width="lg"
    >
      <div className="space-y-4">
        {/* Zone d'ajout */}
        <label
          className="flex h-11 cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border bg-bg-overlay px-3 text-sm font-medium text-accent transition hover:border-accent"
          data-testid="multi-add-files"
        >
          <Upload className="h-4 w-4" /> Ajouter des fichiers
          <input
            type="file"
            multiple
            data-testid="multi-file-input"
            onChange={(e) => {
              addFiles(e.target.files);
              e.target.value = '';
            }}
            className="hidden"
          />
        </label>

        {recap && (
          <div
            role="status"
            data-testid="multi-recap"
            className={`flex items-start gap-2 rounded-lg border p-3 text-sm ${
              recap.ko === 0
                ? 'border-success/30 bg-success/10 text-success'
                : 'border-danger/30 bg-danger/10 text-danger'
            }`}
          >
            {recap.ko === 0 ? (
              <Check className="mt-0.5 h-4 w-4 flex-shrink-0" />
            ) : (
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            )}
            <span>
              {recap.ok} fichier(s) déposé(s) avec succès
              {recap.ko > 0 && <> — {recap.ko} échec(s) (voir détails ci-dessous)</>}.
            </span>
          </div>
        )}

        {/* Liste des fichiers */}
        {rows.length === 0 ? (
          <p className="rounded-lg border border-border/60 bg-bg-overlay px-3 py-6 text-center text-xs text-fg-subtle">
            Aucun fichier ajouté. Sélectionnez un ou plusieurs fichiers à déposer.
          </p>
        ) : (
          <ul className="space-y-2">
            {rows.map((r) => {
              const motifInvalid =
                r.mode === 'version' && r.motif.length > 0 && !isMotifValid(r.motif);
              return (
                <li
                  key={r.id}
                  data-testid={`multi-row-${r.id}`}
                  className="rounded-lg border border-border p-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-fg" title={r.file.name}>
                        {r.file.name}
                      </p>
                      <p className="text-[11px] text-fg-subtle">
                        {(r.file.size / 1024).toFixed(1)} Ko
                      </p>
                    </div>
                    <RowStatusBadge status={r.status} error={r.error} />
                    {r.status !== 'UPLOADING' && r.status !== 'DONE' && (
                      <button
                        type="button"
                        onClick={() => removeRow(r.id)}
                        className="rounded p-1 text-danger hover:bg-danger/10"
                        aria-label="Retirer"
                        data-testid={`multi-remove-${r.id}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    )}
                  </div>

                  <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
                    {/* Action : nouveau document OU nouvelle version de… */}
                    <div>
                      <label
                        htmlFor={`action-${r.id}`}
                        className="mb-0.5 block text-[11px] text-fg-subtle"
                      >
                        Action
                      </label>
                      <select
                        id={`action-${r.id}`}
                        data-testid={`multi-action-${r.id}`}
                        value={r.mode === 'new' ? NEW_DOCUMENT_VALUE : r.targetDocId}
                        disabled={r.status === 'UPLOADING' || r.status === 'DONE'}
                        onChange={(e) => onActionChange(r.id, e.target.value)}
                        className="h-8 w-full rounded-lg border border-border-hi bg-bg-raised px-2 text-xs"
                      >
                        <option value={NEW_DOCUMENT_VALUE}>Nouveau document</option>
                        {activeDocuments.length > 0 && (
                          <optgroup label="Nouvelle version de…">
                            {activeDocuments.map((d) => (
                              <option key={d.id} value={d.id}>
                                {(d.documentType ?? 'AUTRE')} — {d.title} (v{d.version})
                              </option>
                            ))}
                          </optgroup>
                        )}
                      </select>
                    </div>

                    {/* mode 'new' : type du document */}
                    {r.mode === 'new' && (
                      <div>
                        <label
                          htmlFor={`type-${r.id}`}
                          className="mb-0.5 block text-[11px] text-fg-subtle"
                        >
                          Type
                        </label>
                        <select
                          id={`type-${r.id}`}
                          data-testid={`multi-type-${r.id}`}
                          value={r.documentType}
                          disabled={r.status === 'UPLOADING' || r.status === 'DONE'}
                          onChange={(e) => patchRow(r.id, { documentType: e.target.value })}
                          className="h-8 w-full rounded-lg border border-border-hi bg-bg-raised px-2 text-xs"
                        >
                          {DOCUMENT_TYPE_OPTIONS.map((t) => (
                            <option key={t.value} value={t.value}>
                              {t.label}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* mode 'version' : motif obligatoire */}
                    {r.mode === 'version' && (
                      <div className="md:col-span-1">
                        <label
                          htmlFor={`motif-${r.id}`}
                          className="mb-0.5 block text-[11px] text-fg-subtle"
                        >
                          Motif du remplacement * (≥ {MOTIF_MIN_LENGTH} car.)
                        </label>
                        <input
                          id={`motif-${r.id}`}
                          data-testid={`multi-motif-${r.id}`}
                          type="text"
                          value={r.motif}
                          disabled={r.status === 'UPLOADING' || r.status === 'DONE'}
                          onChange={(e) => patchRow(r.id, { motif: e.target.value })}
                          placeholder="Ex. Version corrigée des statuts"
                          className={`h-8 w-full rounded-lg border bg-bg-raised px-2 text-xs ${
                            motifInvalid
                              ? 'border-danger focus:border-danger'
                              : 'border-border-hi focus:border-indigo-500'
                          }`}
                        />
                      </div>
                    )}
                  </div>

                  {motifInvalid && (
                    <p className="mt-1 text-[11px] text-danger">
                      Le motif doit faire au moins {MOTIF_MIN_LENGTH} caractères.
                    </p>
                  )}
                  {r.status === 'ERROR' && r.error && (
                    <p
                      role="alert"
                      data-testid={`multi-error-${r.id}`}
                      className="mt-1 flex items-start gap-1 text-[11px] text-danger"
                    >
                      <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" /> {r.error}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        <div className="flex items-center justify-end gap-2 border-t border-border pt-3">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            {allDone ? 'Fermer' : 'Annuler'}
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={submit}
            loading={submitting}
            disabled={!canSubmit}
            data-testid="multi-submit"
          >
            {submitting ? (
              <Loader2 className="mr-1 h-4 w-4 animate-spin" />
            ) : (
              <Upload className="mr-1 h-4 w-4" />
            )}
            Déposer {pendingRows.length > 0 ? `(${pendingRows.length})` : ''}
          </Button>
        </div>
      </div>
    </Drawer>
  );
}

function RowStatusBadge({ status, error }: { status: RowStatus; error?: string }) {
  if (status === 'DONE')
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
        <Check className="h-3 w-3" /> Déposé
      </span>
    );
  if (status === 'UPLOADING')
    return (
      <span className="inline-flex items-center gap-1 text-[10px] font-medium text-fg-subtle">
        <Loader2 className="h-3 w-3 animate-spin" /> En cours
      </span>
    );
  if (status === 'ERROR')
    return (
      <span
        className="inline-flex items-center gap-1 rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-danger"
        title={error}
      >
        <AlertCircle className="h-3 w-3" /> Échec
      </span>
    );
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-semibold uppercase text-fg-subtle">
      En attente
    </span>
  );
}

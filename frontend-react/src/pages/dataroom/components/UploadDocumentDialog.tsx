import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, FileUp, Loader2, X } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { uploadOrReplace } from '../../../lib/dataroomUpload';
import type { DocumentSummary, DocumentType } from '../../../types/dataroom';

/**
 * Sprint 2026-06-23 — Modale d'upload avec versioning explicite.
 *
 * <p>Présente l'utilisateur avec un choix clair :
 * <ul>
 *   <li><b>« Nouveau document »</b> — crée un Document logique distinct
 *       (appelle {@link dataroomService.uploadJuridique}).</li>
 *   <li><b>« Nouvelle version de … »</b> — remplace un Document ACTIF :
 *       l'ancienne version bascule en historique, la nouvelle devient ACTIVE
 *       (appelle {@link dataroomService.replaceAsNewVersion}).</li>
 * </ul>
 *
 * Le champ <b>motif</b> est requis seulement quand on choisit
 * "Nouvelle version de …" (traçabilité du remplacement).
 */
export interface UploadDocumentDialogProps {
  open: boolean;
  dossierId: string;
  activeDocuments: DocumentSummary[];
  onClose: () => void;
  onUploaded: () => Promise<void> | void;
  /** Liste des types autorisés pour un nouveau Document (par défaut : toutes). */
  documentTypes?: ReadonlyArray<DocumentType | string>;
}

const NEW_DOCUMENT_VALUE = '__NEW__';
const DEFAULT_TYPES: ReadonlyArray<DocumentType | string> = [
  'STATUTS', 'PV_AGE', 'PV_AGO', 'PV_MODIFICATION', 'PV_DISSOLUTION', 'PV_LIQUIDATION',
  'ACTE_NOMINATION', 'CONTRAT_BAIL', 'CNIE_GERANT', 'ANNONCE_JAL',
  'RC', 'ICE', 'TP', 'CNSS', 'APOSTILLE', 'AUTRE',
];

export function UploadDocumentDialog({
  open,
  dossierId,
  activeDocuments,
  onClose,
  onUploaded,
  documentTypes = DEFAULT_TYPES,
}: UploadDocumentDialogProps) {
  const [target, setTarget] = useState<string>(NEW_DOCUMENT_VALUE);
  const [file, setFile] = useState<File | null>(null);
  const [motif, setMotif] = useState('');
  const [newType, setNewType] = useState<string>('AUTRE');
  const [newTitle, setNewTitle] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setTarget(NEW_DOCUMENT_VALUE);
      setFile(null);
      setMotif('');
      setNewType('AUTRE');
      setNewTitle('');
      setError(null);
      setSubmitting(false);
    }
  }, [open]);

  const isReplaceMode = target !== NEW_DOCUMENT_VALUE;
  const selectedDoc = useMemo(
    () => activeDocuments.find((d) => d.id === target) ?? null,
    [activeDocuments, target],
  );
  const motifTrimmed = motif.trim();
  const canSubmit = (() => {
    if (!file) return false;
    if (isReplaceMode) {
      if (motifTrimmed.length < 5) return false;
      if (!selectedDoc) return false;
      return true;
    }
    if (newTitle.trim().length === 0) return false;
    return true;
  })();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit || !file) return;
    setSubmitting(true);
    setError(null);
    try {
      if (isReplaceMode && selectedDoc) {
        await uploadOrReplace(file, {
          dossierId,
          mode: 'version',
          targetDocId: selectedDoc.id,
          motif: motifTrimmed,
        });
      } else {
        await uploadOrReplace(file, {
          dossierId,
          mode: 'new',
          documentType: newType,
          title: newTitle.trim(),
        });
      }
      await onUploaded();
      onClose();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Echec de l\'upload';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="upload-dialog-title"
      data-testid="upload-document-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="relative w-full max-w-lg rounded-xl bg-bg-raised shadow-xl">
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <h2 id="upload-dialog-title" className="text-base font-semibold text-fg">
            Uploader un document
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fermer"
            className="rounded p-1 text-fg-subtle hover:bg-bg-overlay"
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        <form onSubmit={submit} noValidate className="space-y-4 p-5">
          {/* SELECT : nouveau vs nouvelle version d'un document actif */}
          <div>
            <label htmlFor="upload-target" className="mb-1 block text-sm font-medium text-fg-muted">
              Destination
            </label>
            <select
              id="upload-target"
              data-testid="upload-target-select"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
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

          {/* Champs spécifiques au mode "Nouveau document" */}
          {!isReplaceMode && (
            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <label htmlFor="upload-new-type" className="mb-1 block text-sm font-medium text-fg-muted">
                  Type *
                </label>
                <select
                  id="upload-new-type"
                  data-testid="upload-new-type"
                  value={newType}
                  onChange={(e) => setNewType(e.target.value)}
                  className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm"
                >
                  {documentTypes.map((t) => (
                    <option key={String(t)} value={String(t)}>
                      {String(t)}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="upload-new-title" className="mb-1 block text-sm font-medium text-fg-muted">
                  Titre *
                </label>
                <input
                  id="upload-new-title"
                  data-testid="upload-new-title"
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="Ex. Statuts SARL refondus"
                  className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm"
                />
              </div>
            </div>
          )}

          {/* Champ motif : requis seulement en mode "Nouvelle version" */}
          {isReplaceMode && (
            <div>
              <label htmlFor="upload-motif" className="mb-1 block text-sm font-medium text-fg-muted">
                Motif du remplacement *
                <span className="ml-1 text-xs text-fg-subtle">(≥ 5 caractères, traçabilité)</span>
              </label>
              <textarea
                id="upload-motif"
                data-testid="upload-motif"
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                rows={2}
                placeholder="Ex. Correction d'une coquille dans l'objet social"
                className={`w-full rounded-lg border bg-bg-raised px-3 py-2 text-sm ${
                  motif.length > 0 && motifTrimmed.length < 5
                    ? 'border-danger focus:border-danger'
                    : 'border-border-hi focus:border-indigo-500'
                }`}
              />
              {motif.length > 0 && motifTrimmed.length < 5 && (
                <p className="mt-1 text-xs text-danger">
                  Le motif doit faire au moins 5 caractères.
                </p>
              )}
            </div>
          )}

          {/* Fichier */}
          <div>
            <label htmlFor="upload-file" className="mb-1 block text-sm font-medium text-fg-muted">
              Fichier *
            </label>
            <input
              id="upload-file"
              data-testid="upload-file-input"
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-fg-muted file:mr-3 file:rounded-lg file:border-0 file:bg-accent/10 file:px-3 file:py-2 file:text-sm file:font-medium file:text-accent hover:file:bg-accent/20"
            />
            {file && (
              <p className="mt-1 text-xs text-fg-subtle">
                {file.name} — {Math.round(file.size / 1024)} Ko
              </p>
            )}
          </div>

          {error && (
            <div role="alert" className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 p-2 text-xs text-danger">
              <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex items-center justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="secondary" size="sm" onClick={onClose}>
              Annuler
            </Button>
            <Button
              type="submit"
              size="sm"
              loading={submitting}
              disabled={!canSubmit || submitting}
              data-testid="upload-submit"
            >
              {submitting ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <FileUp className="mr-1 h-4 w-4" />
              )}
              {isReplaceMode ? 'Remplacer comme nouvelle version' : 'Créer le document'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

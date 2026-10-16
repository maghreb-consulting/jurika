import { useMemo, useState } from 'react';
import { AlertCircle, Check, Loader2, Trash2, Upload } from 'lucide-react';
import { Drawer } from '../../../components/ui/Drawer';
import { Button } from '../../../components/ui/Button';
import { Badge } from '../../../components/ui/Badge';
import { Message } from '../../../components/ui/Message';
import { EmptyState } from '../../../components/ui/EmptyState';
import { Select } from '../../../components/ui/Select';
import { TextField } from '../../../components/ui/TextField';
import { extractError } from '../../../lib/api';
import {
  isMotifValid,
  MOTIF_MIN_LENGTH,
  stripExtension,
  uploadOrReplace,
} from '../../../lib/dataroomUpload';
import type { DocumentSummary } from '../../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../../types/dataroom';
import { identifiantUnique } from '../../../lib/identifiant';

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
  return identifiantUnique();
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

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertVu, setOuvertVu] = useState<boolean | null>(null);
  if (ouvertVu !== open) {
    setOuvertVu(open);
    if (open) {
      setRows([]);
      setSubmitting(false);
      setRecap(null);
    }
  }

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
  const cibles = activeDocuments.map((d) => ({
    value: d.id,
    label: `${(DOCUMENT_TYPE_LABELS as Record<string, string>)[d.documentType ?? 'AUTRE'] ?? d.documentType ?? 'Autre'} — ${d.title} (version ${d.version})`,
  }));

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Ajouter plusieurs documents"
      subtitle="Pour chaque fichier : nouveau document, ou nouvelle version d’un document existant"
      width="lg"
      closeOnBackdrop={false}
      footer={
        <div className="flex items-center justify-end gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            {allDone ? 'Fermer' : 'Annuler'}
          </Button>
          <Button
            type="button"
            size="sm"
            icon={<Upload />}
            onClick={submit}
            loading={submitting}
            disabled={!canSubmit}
            data-testid="multi-submit"
          >
            Déposer {pendingRows.length > 0 ? `(${pendingRows.length})` : ''}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-fg-muted">
          Ajoutez les fichiers, choisissez pour chacun son type ou le document qu’il remplace (un motif
          est alors demandé), puis cliquez sur « Déposer ». Chaque fichier reçoit son propre résultat.
        </p>
        {/* Zone d'ajout */}
        <label
          className="flex h-12 cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border-input bg-bg-overlay px-3 text-sm font-medium text-fg transition-colors duration-150 hover:border-accent focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-focus"
          data-testid="multi-add-files"
        >
          <Upload aria-hidden="true" className="h-4 w-4" /> Ajouter des fichiers
          <input
            type="file"
            multiple
            data-testid="multi-file-input"
            onChange={(e) => {
              addFiles(e.target.files);
              e.target.value = '';
            }}
            className="sr-only"
          />
        </label>

        {recap && (
          <div data-testid="multi-recap">
            <Message ton={recap.ko === 0 ? 'succes' : 'erreur'}>
              {recap.ok} fichier(s) déposé(s) avec succès
              {recap.ko > 0 && <> — {recap.ko} échec(s) (voir détails ci-dessous)</>}.
            </Message>
          </div>
        )}

        {/* Liste des fichiers */}
        {rows.length === 0 ? (
          <EmptyState
            compact
            icon={<Upload />}
            title="Aucun fichier ajouté"
            description="Sélectionnez un ou plusieurs fichiers à déposer avec « Ajouter des fichiers »."
          />
        ) : (
          <ul className="space-y-2">
            {rows.map((r) => {
              const motifInvalid =
                r.mode === 'version' && r.motif.length > 0 && !isMotifValid(r.motif);
              const fige = r.status === 'UPLOADING' || r.status === 'DONE';
              return (
                <li
                  key={r.id}
                  data-testid={`multi-row-${r.id}`}
                  className="rounded-lg border border-border bg-bg-raised p-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-fg" title={r.file.name}>
                        {r.file.name}
                      </p>
                      <p className="text-xs text-fg-muted">{(r.file.size / 1024).toFixed(1)} Ko</p>
                    </div>
                    <RowStatusBadge status={r.status} error={r.error} />
                    {!fige && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        iconOnly
                        icon={<Trash2 className="text-danger" />}
                        onClick={() => removeRow(r.id)}
                        aria-label={`Retirer ${r.file.name}`}
                        data-testid={`multi-remove-${r.id}`}
                      />
                    )}
                  </div>

                  <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
                    {/* Action : nouveau document OU nouvelle version de… */}
                    <Select
                      id={`action-${r.id}`}
                      label="Action"
                      data-testid={`multi-action-${r.id}`}
                      value={r.mode === 'new' ? NEW_DOCUMENT_VALUE : r.targetDocId}
                      disabled={fige}
                      onChange={(e) => onActionChange(r.id, e.target.value)}
                      options={[{ value: NEW_DOCUMENT_VALUE, label: 'Nouveau document' }]}
                      groups={cibles.length > 0 ? [{ label: 'Nouvelle version de…', options: cibles }] : undefined}
                    />

                    {/* mode 'new' : type du document */}
                    {r.mode === 'new' && (
                      <Select
                        id={`type-${r.id}`}
                        label="Type"
                        data-testid={`multi-type-${r.id}`}
                        value={r.documentType}
                        disabled={fige}
                        onChange={(e) => patchRow(r.id, { documentType: e.target.value })}
                        options={DOCUMENT_TYPE_OPTIONS}
                      />
                    )}

                    {/* mode 'version' : motif obligatoire */}
                    {r.mode === 'version' && (
                      <TextField
                        id={`motif-${r.id}`}
                        label={`Motif du remplacement (${MOTIF_MIN_LENGTH} caractères au moins)`}
                        data-testid={`multi-motif-${r.id}`}
                        value={r.motif}
                        disabled={fige}
                        onChange={(e) => patchRow(r.id, { motif: e.target.value })}
                        placeholder="Exemple : version corrigée des statuts"
                        error={motifInvalid ? `Le motif doit faire au moins ${MOTIF_MIN_LENGTH} caractères.` : undefined}
                      />
                    )}
                  </div>

                  {r.status === 'ERROR' && r.error && (
                    <p
                      role="alert"
                      data-testid={`multi-error-${r.id}`}
                      className="mt-2 flex items-start gap-1 text-xs text-danger"
                    >
                      <AlertCircle aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" /> {r.error}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Drawer>
  );
}

function RowStatusBadge({ status, error }: { status: RowStatus; error?: string }) {
  if (status === 'DONE')
    return (
      <Badge variant="success">
        <Check aria-hidden="true" className="h-3 w-3" /> Déposé
      </Badge>
    );
  if (status === 'UPLOADING')
    return (
      <Badge variant="info">
        <Loader2 aria-hidden="true" className="h-3 w-3 animate-spin motion-reduce:animate-none" /> En cours
      </Badge>
    );
  if (status === 'ERROR')
    return (
      <Badge variant="danger" title={error}>
        <AlertCircle aria-hidden="true" className="h-3 w-3" /> Échec
      </Badge>
    );
  return <Badge variant="neutral">En attente</Badge>;
}

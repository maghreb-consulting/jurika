import { useMemo, useState } from 'react';
import { FileUp } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Modal } from '../../../components/ui/Modal';
import { Message } from '../../../components/ui/Message';
import { Select } from '../../../components/ui/Select';
import { TextField, ZoneTexte } from '../../../components/ui/TextField';
import { uploadOrReplace } from '../../../lib/dataroomUpload';
import type { DocumentSummary, DocumentType } from '../../../types/dataroom';
import { DOCUMENT_TYPE_LABELS } from '../../../types/dataroom';
import { useDocumentTypes } from '../useDocumentTypes';

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
  /**
   * Types autorisés pour un nouveau Document. Sans override, la liste vient du
   * backend (lot 2) : elle vivait ici en dur et ignorait les types introduits
   * par le lot 1, si bien qu'un certificat négatif déposé à la main tombait
   * sous « AUTRE ».
   */
  documentTypes?: ReadonlyArray<DocumentType | string>;
}

const NEW_DOCUMENT_VALUE = '__NEW__';

export function UploadDocumentDialog({
  open,
  dossierId,
  activeDocuments,
  onClose,
  onUploaded,
  documentTypes,
}: UploadDocumentDialogProps) {
  const { types: catalogue } = useDocumentTypes();
  const options = useMemo(
    () =>
      documentTypes
        ? documentTypes.map((t) => ({ code: String(t), libelle: String(t) }))
        : catalogue.map((t) => ({ code: t.code, libelle: t.libelle })),
    [documentTypes, catalogue],
  );
  const [target, setTarget] = useState<string>(NEW_DOCUMENT_VALUE);
  const [file, setFile] = useState<File | null>(null);
  const [motif, setMotif] = useState('');
  const [newType, setNewType] = useState<string>('AUTRE');
  const [newTitle, setNewTitle] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Remise a zero a l'ouverture, ajustee pendant le rendu (pas d'etat pose dans un effet).
  const [ouvertVu, setOuvertVu] = useState<boolean | null>(null);
  if (ouvertVu !== open) {
    setOuvertVu(open);
    if (open) {
      setTarget(NEW_DOCUMENT_VALUE);
      setFile(null);
      setMotif('');
      setNewType('AUTRE');
      setNewTitle('');
      setError(null);
      setSubmitting(false);
    }
  }

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
      const msg = err instanceof Error ? err.message : 'Échec du dépôt du document. Vérifiez le fichier, puis réessayez.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  const libelleType = (code: string | null | undefined) =>
    (DOCUMENT_TYPE_LABELS as Record<string, string>)[code ?? 'AUTRE'] ?? code ?? 'Autre';
  const motifTropCourt = motif.length > 0 && motifTrimmed.length < 5;

  return (
    <Modal
      open={open}
      onOpenChange={(o) => {
        if (!o) onClose();
      }}
      title="Ajouter un document"
      description="Un nouveau document, ou une nouvelle version d’un document en vigueur (l’ancienne reste consultable)."
      size="lg"
      closeOnBackdrop={false}
    >
      <form onSubmit={submit} noValidate className="space-y-4" data-testid="upload-document-dialog">
        {/* Choix : nouveau document ou nouvelle version d'un document actif */}
        <Select
          id="upload-target"
          label="Destination"
          data-testid="upload-target-select"
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          hint="Choisissez « Nouvelle version de… » pour remplacer un document : un motif est alors demandé."
          options={[{ value: NEW_DOCUMENT_VALUE, label: 'Nouveau document' }]}
          groups={
            activeDocuments.length > 0
              ? [
                  {
                    label: 'Nouvelle version de…',
                    options: activeDocuments.map((d) => ({
                      value: d.id,
                      label: `${libelleType(d.documentType)} — ${d.title} (version ${d.version})`,
                    })),
                  },
                ]
              : undefined
          }
        />

        {/* Champs du mode "Nouveau document" */}
        {!isReplaceMode && (
          <div className="grid gap-3 md:grid-cols-2">
            <Select
              id="upload-new-type"
              label="Type"
              data-testid="upload-new-type"
              value={newType}
              onChange={(e) => setNewType(e.target.value)}
              options={options.map((t) => ({ value: t.code, label: t.libelle }))}
            />
            <TextField
              id="upload-new-title"
              label="Titre"
              data-testid="upload-new-title"
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="Exemple : statuts refondus de la SARL"
              required
            />
          </div>
        )}

        {/* Motif : demande seulement en mode "Nouvelle version" */}
        {isReplaceMode && (
          <ZoneTexte
            id="upload-motif"
            label="Motif du remplacement"
            data-testid="upload-motif"
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            rows={2}
            placeholder="Exemple : correction d’une coquille dans l’objet social"
            hint="Au moins 5 caractères : le motif est conservé avec l’ancienne version."
            error={motifTropCourt ? 'Le motif doit faire au moins 5 caractères.' : undefined}
            required
          />
        )}

        {/* Fichier */}
        <div className="space-y-1.5">
          <label htmlFor="upload-file" className="text-sm font-medium text-fg">
            Fichier
          </label>
          <input
            id="upload-file"
            data-testid="upload-file-input"
            type="file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-fg-muted file:mr-3 file:h-9 file:cursor-pointer file:rounded-lg file:border file:border-border-input file:bg-bg-raised file:px-3 file:text-sm file:font-medium file:text-fg hover:file:bg-bg-overlay"
          />
          {file && (
            <p className="text-xs text-fg-muted">
              {file.name} — {Math.round(file.size / 1024)} Ko
            </p>
          )}
        </div>

        {error && <Message ton="erreur">{error}</Message>}

        <div className="flex items-center justify-end gap-2 border-t border-border pt-4">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Annuler
          </Button>
          <Button
            type="submit"
            size="sm"
            icon={<FileUp />}
            loading={submitting}
            disabled={!canSubmit || submitting}
            data-testid="upload-submit"
          >
            {isReplaceMode ? 'Remplacer comme nouvelle version' : 'Créer le document'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

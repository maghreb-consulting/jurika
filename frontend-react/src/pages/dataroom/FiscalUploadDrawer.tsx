import { useEffect, useState } from 'react';
import { Loader2, X } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  CATEGORIES_FISCALES_ORDER,
  CATEGORIE_CGI_LABELS,
  SOUS_CLASSIFICATION_LABELS,
  type CategorieFiscale,
  type SubClassificationDef,
} from '../../types/dataroom';
import type { Role } from '../../types/auth';

interface Props {
  dossierId: string;
  exerciceId: string;
  initialCategorie: CategorieFiscale;
  role: Role | null;
  onClose: () => void;
  onUploaded: () => void;
}

export function FiscalUploadDrawer({
  dossierId,
  exerciceId,
  initialCategorie,
  role,
  onClose,
  onUploaded,
}: Props) {
  const [categorie, setCategorie] = useState<CategorieFiscale>(initialCategorie);
  const [subDefs, setSubDefs] = useState<SubClassificationDef[]>([]);
  const [sousClassification, setSousClassification] = useState('');
  const [title, setTitle] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [periodeDeclaree, setPeriodeDeclaree] = useState('');
  const [numeroDeclaration, setNumeroDeclaration] = useState('');
  const [tifMetadata, setTifMetadata] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dataroomService
      .getSubClassifications()
      .then(setSubDefs)
      .catch((err) => setError(extractError(err).message));
  }, []);

  useEffect(() => {
    const def = subDefs.find((d) => d.categorie === categorie);
    setSousClassification(def && def.values.length > 0 ? def.values[0] : '');
  }, [categorie, subDefs]);

  const isContentieux = categorie === 'CONTENTIEUX';
  const isClientForbidden = isContentieux && role === 'CLIENT';
  const commentaireRequired = isContentieux;
  const commentaireValid =
    !commentaireRequired || (commentaire.trim().length >= 20);

  const currentSubValues =
    subDefs.find((d) => d.categorie === categorie)?.values ?? [];

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!file) {
      setError('Selectionnez un fichier.');
      return;
    }
    if (!sousClassification) {
      setError('Sous-classification requise.');
      return;
    }
    if (!commentaireValid) {
      setError('Commentaire obligatoire (>= 20 caracteres) pour CONTENTIEUX.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await dataroomService.uploadFiscal(dossierId, {
        file,
        exerciceId,
        categorie,
        sousClassification,
        title: title || undefined,
        commentaire: commentaire || undefined,
        tifMetadata: tifMetadata || undefined,
        numeroDeclaration: numeroDeclaration || undefined,
        periodeDeclaree: periodeDeclaree || undefined,
      });
      onUploaded();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-bg/40">
      <div className="flex h-full w-full max-w-md flex-col bg-bg-raised shadow-xl">
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <h3 className="text-sm font-semibold text-fg">Upload document fiscal</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
          >
            <X className="h-4 w-4" />
          </button>
        </header>
        <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col overflow-y-auto">
          <div className="space-y-4 px-5 py-4">
            {/* Step 1: categorie */}
            <div>
              <label className="text-xs font-semibold uppercase text-fg-subtle">
                Categorie CGI
              </label>
              <select
                value={categorie}
                onChange={(e) => setCategorie(e.target.value as CategorieFiscale)}
                className="mt-1 w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm"
              >
                {CATEGORIES_FISCALES_ORDER.map((c) => (
                  <option key={c} value={c}>
                    {CATEGORIE_CGI_LABELS[c] ?? c}
                  </option>
                ))}
              </select>
            </div>

            {isClientForbidden && (
              <p className="rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">
                RG-DF04 : seul un employe du cabinet peut uploader un document CONTENTIEUX.
              </p>
            )}

            {/* Step 2: sous-classification */}
            <div>
              <label className="text-xs font-semibold uppercase text-fg-subtle">
                Sous-classification
              </label>
              <select
                value={sousClassification}
                onChange={(e) => setSousClassification(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm"
              >
                {currentSubValues.map((s) => (
                  <option key={s} value={s}>
                    {SOUS_CLASSIFICATION_LABELS[s] ?? s}
                  </option>
                ))}
              </select>
            </div>

            {/* Step 3: metadata */}
            <div>
              <label className="text-xs font-semibold uppercase text-fg-subtle">Titre</label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Ex: Declaration TVA mars 2026"
                className="mt-1 w-full rounded-lg border border-border-hi px-3 py-2 text-sm"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-semibold uppercase text-fg-subtle">
                  Periode declaree
                </label>
                <input
                  type="text"
                  value={periodeDeclaree}
                  onChange={(e) => setPeriodeDeclaree(e.target.value)}
                  placeholder="2026-03 ou 2026-T1"
                  className="mt-1 w-full rounded-lg border border-border-hi px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-semibold uppercase text-fg-subtle">
                  N° declaration
                </label>
                <input
                  type="text"
                  value={numeroDeclaration}
                  onChange={(e) => setNumeroDeclaration(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border-hi px-3 py-2 text-sm"
                />
              </div>
            </div>
            <div>
              <label className="text-xs font-semibold uppercase text-fg-subtle">
                Identifiant TIF / SIMPL
              </label>
              <input
                type="text"
                value={tifMetadata}
                onChange={(e) => setTifMetadata(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border-hi px-3 py-2 text-sm"
              />
            </div>

            {/* Step 4: commentaire (obligatoire CONTENTIEUX) */}
            {commentaireRequired && (
              <div>
                <label className="text-xs font-semibold uppercase text-fg-subtle">
                  Commentaire ({commentaire.length}/20+ chars, RG-DF23)
                </label>
                <textarea
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                  rows={3}
                  className={`mt-1 w-full rounded-lg border px-3 py-2 text-sm ${
                    commentaireValid ? 'border-border-hi' : 'border-rose-300'
                  }`}
                />
              </div>
            )}

            {/* Step 5: fichier */}
            <div>
              <label className="text-xs font-semibold uppercase text-fg-subtle">
                Fichier (PDF / JPG / PNG / XLS / DOC, max 15 Mo)
              </label>
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,.xls,.xlsx,.doc,.docx"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mt-1 w-full text-sm"
              />
            </div>

            {error && (
              <p className="rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">{error}</p>
            )}
          </div>

          <footer className="flex items-center justify-end gap-2 border-t border-border bg-bg-overlay px-5 py-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-xs font-medium text-fg-muted hover:bg-bg-overlay"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={submitting || isClientForbidden || !file || !commentaireValid}
              className="inline-flex items-center gap-1 rounded-lg bg-warning px-3 py-2 text-xs font-semibold text-bg-raised hover:bg-warning/85 disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              Uploader
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}

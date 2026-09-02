import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Calculator,
  Check,
  Download,
  Eye,
  FileText,
  Plus,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import type {
  CategorieComptable,
  ComptableDocumentSummary,
  DossierComptableView,
  ExerciceFiscalSummary,
} from '../../types/dataroom';
import {
  CATEGORIE_COMPTABLE_LABELS,
  CATEGORIE_COMPTABLE_ORDER,
} from '../../types/dataroom';
import type { Role } from '../../types/auth';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';

interface Props {
  dossierId: string;
  role: Role | null;
  /**
   * 2026-06-30 — Le CLIENT ne peut DEPOSER que si perm_depot est accorde.
   * Defaut true : cote EMPLOYE (et anciens appels) le depot reste libre.
   * Le backend applique la meme garde (defense en profondeur).
   */
  canDepot?: boolean;
  /**
   * Lot AA (2026-07-05) — Permissions transmises au PdfPreviewModal (bouton
   * Telecharger/Imprimer). Defaut true : cote EMPLOYE le download reste libre.
   * Le bouton « Voir » reste TOUJOURS actif (visionnage inconditionnel). Le
   * backend gate le download par perm_download (defense en profondeur).
   */
  canDownload?: boolean;
  canPrint?: boolean;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : archive
   * legale en lecture seule (RG-DC25). Neutralise depot et suppression quel que
   * soit le role ; le backend (`DossierArchiveGuard`) re-verifie.
   */
  readOnly?: boolean;
  /** Statut de la societe, pour le libelle de l'encart. */
  readOnlyStatut?: string | null;
}

export function DossierComptableTab({
  dossierId,
  role,
  canDepot = true,
  canDownload = true,
  canPrint = true,
  readOnly = false,
  readOnlyStatut = null,
}: Props) {
  const [view, setView] = useState<DossierComptableView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [selectedCategorie, setSelectedCategorie] =
    useState<CategorieComptable>('ACHATS');
  const [documents, setDocuments] = useState<ComptableDocumentSummary[]>([]);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  // Lot AA — document en cours d'apercu (null = modal fermee).
  const [previewDoc, setPreviewDoc] = useState<ComptableDocumentSummary | null>(null);
  // Confirmation de suppression (remplace confirm() natif).
  const [toDelete, setToDelete] = useState<ComptableDocumentSummary | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [exercices, setExercices] = useState<ExerciceFiscalSummary[]>([]);
  // Sprint 2026-06-23 — années ajoutées à la volée (anciens exercices à reprendre).
  // Le backend accepte n'importe quelle valeur d'`annee` et crée l'exercice
  // automatiquement au 1er upload. On garde donc une liste locale fusionnée.
  const [extraYears, setExtraYears] = useState<number[]>([]);
  const [showAddYear, setShowAddYear] = useState(false);
  const [newYearInput, setNewYearInput] = useState('');
  const [addYearError, setAddYearError] = useState<string | null>(null);
  // 2026-06-30 — Depot : EMPLOYE toujours ; CLIENT uniquement si perm_depot.
  // Suppression : EMPLOYE seul (le CLIENT ne supprime plus -- faille corrigee,
  // le backend renvoie 403).
  const canDepotRole = role === 'EMPLOYE' || (role === 'CLIENT' && canDepot);
  const canDeleteRole = role === 'EMPLOYE';

  const YEAR_MIN = 1990;
  const YEAR_MAX = new Date().getFullYear() + 1;

  // Sprint 8 -- upload bloque si l'exercice de l'annee selectionnee est VERROUILLE
  const currentExercice = useMemo(
    () => exercices.find((e) => e.annee === selectedYear) ?? null,
    [exercices, selectedYear],
  );
  const isLocked = currentExercice?.statut === 'VERROUILLE';
  // §A — l'archive legale prime sur le role ET sur le verrouillage d'exercice.
  const canUpload = canDepotRole && !isLocked && !readOnly;
  const canDelete = canDeleteRole && !isLocked && !readOnly;

  const loadView = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await dataroomService.getComptable(dossierId);
      setView(data);
      const currentYear = new Date().getFullYear();
      const years = data.annees.length > 0 ? data.annees : [data.anneeCourante ?? currentYear];
      setSelectedYear((prev) => (prev && years.includes(prev) ? prev : data.anneeCourante ?? years[0]));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    loadView();
  }, [loadView]);

  // Sprint 8 -- charge la liste des exercices pour afficher le statut a cote de l'annee
  useEffect(() => {
    dataroomService
      .listExercices(dossierId)
      .then(setExercices)
      .catch(() => {
        /* non bloquant : si Sprint 8 backend pas dispo, garder UX legacy */
      });
  }, [dossierId]);

  const loadDocuments = useCallback(async () => {
    if (!selectedYear) return;
    setDocumentsLoading(true);
    try {
      const docs = await dataroomService.listComptableDocuments(
        dossierId,
        selectedYear,
        selectedCategorie,
      );
      setDocuments(docs);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setDocumentsLoading(false);
    }
  }, [dossierId, selectedYear, selectedCategorie]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  const totals = useMemo(() => {
    const map = new Map<string, number>();
    view?.totauxParCategorie.forEach((t) => map.set(String(t.categorie), t.total));
    return map;
  }, [view]);

  const years = useMemo(() => {
    const set = new Set<number>(view?.annees ?? []);
    const currentYear = new Date().getFullYear();
    set.add(currentYear);
    if (view?.anneeCourante) set.add(view.anneeCourante);
    extraYears.forEach((y) => set.add(y));
    return Array.from(set).sort((a, b) => b - a);
  }, [view, extraYears]);

  function tryAddYear() {
    setAddYearError(null);
    const raw = newYearInput.trim();
    if (!/^\d{4}$/.test(raw)) {
      setAddYearError(`Annee invalide : 4 chiffres attendus (${YEAR_MIN}-${YEAR_MAX}).`);
      return;
    }
    const y = parseInt(raw, 10);
    if (Number.isNaN(y) || y < YEAR_MIN || y > YEAR_MAX) {
      setAddYearError(`Annee hors bornes (${YEAR_MIN}-${YEAR_MAX}).`);
      return;
    }
    setExtraYears((prev) => (prev.includes(y) || years.includes(y) ? prev : [...prev, y]));
    setSelectedYear(y);
    setNewYearInput('');
    setShowAddYear(false);
  }

  async function handleFilesUpload(files: FileList | File[] | null) {
    if (!selectedYear || !files) return;
    const arr = Array.from(files);
    if (arr.length === 0) return;
    setUploading(true);
    setError(null);
    try {
      if (arr.length === 1) {
        await dataroomService.uploadComptable(dossierId, {
          file: arr[0],
          annee: selectedYear,
          categorie: selectedCategorie,
          title: arr[0].name,
        });
      } else {
        await dataroomService.uploadComptableBatch(dossierId, arr, {
          annee: selectedYear,
          categorie: selectedCategorie,
        });
      }
      await loadView();
      await loadDocuments();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function confirmDelete() {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await dataroomService.deleteComptableDocument(toDelete.id);
      setToDelete(null);
      await loadView();
      await loadDocuments();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setDeleting(false);
    }
  }

  async function handleDownload(doc: ComptableDocumentSummary) {
    try {
      await dataroomService.downloadComptableDocument(doc.id, doc.filename);
    } catch (err) {
      setError(extractError(err).message);
    }
  }

  if (loading && !view) {
    return (
      <Card className="flex h-48 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      <Card className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Calculator className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold text-fg">
              Dossier comptable
            </h3>
          </div>
          <div className="relative flex items-center gap-2">
            <label className="text-xs text-fg-subtle">Annee</label>
            <select
              value={selectedYear ?? ''}
              onChange={(e) => setSelectedYear(parseInt(e.target.value, 10))}
              data-testid="comptable-year-select"
              className="rounded-lg border border-border-hi bg-bg-raised px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            >
              {years.map((y) => {
                const ex = exercices.find((e) => e.annee === y);
                const label = ex ? `${y} (${ex.statut})` : String(y);
                return (
                  <option key={y} value={y}>
                    {label}
                  </option>
                );
              })}
            </select>
            <button
              type="button"
              onClick={() => {
                setShowAddYear((v) => !v);
                setAddYearError(null);
              }}
              data-testid="comptable-add-year-toggle"
              className="inline-flex items-center gap-1 rounded-lg border border-border-hi bg-bg-raised px-2 py-1.5 text-xs text-fg-muted hover:border-indigo-400 hover:text-fg"
              title="Ajouter une année (ancien exercice)"
            >
              <Plus className="h-3.5 w-3.5" />
              Ajouter une année
            </button>
            {showAddYear && (
              <div
                role="dialog"
                aria-label="Ajouter une année"
                data-testid="comptable-add-year-popover"
                className="absolute right-0 top-full z-10 mt-1 flex flex-col gap-2 rounded-lg border border-border bg-bg-raised p-3 shadow-lg"
              >
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    min={YEAR_MIN}
                    max={YEAR_MAX}
                    inputMode="numeric"
                    value={newYearInput}
                    onChange={(e) => setNewYearInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        tryAddYear();
                      }
                    }}
                    placeholder="ex. 2022"
                    autoFocus
                    data-testid="comptable-add-year-input"
                    className="w-24 rounded-lg border border-border-hi bg-bg-raised px-2 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  />
                  <button
                    type="button"
                    onClick={tryAddYear}
                    data-testid="comptable-add-year-submit"
                    className="inline-flex items-center gap-1 rounded-lg bg-accent px-2 py-1 text-xs font-medium text-bg-raised hover:bg-accent-hover"
                  >
                    <Check className="h-3.5 w-3.5" />
                    Ajouter
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setShowAddYear(false);
                      setNewYearInput('');
                      setAddYearError(null);
                    }}
                    aria-label="Annuler"
                    className="rounded p-1 text-fg-subtle hover:bg-bg-overlay"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
                <p className="text-[11px] text-fg-subtle">
                  Plage {YEAR_MIN}–{YEAR_MAX}. L'exercice sera créé au 1er upload.
                </p>
                {addYearError && (
                  <p
                    role="alert"
                    data-testid="comptable-add-year-error"
                    className="text-xs text-danger"
                  >
                    {addYearError}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-2 md:grid-cols-3 lg:grid-cols-6">
          {CATEGORIE_COMPTABLE_ORDER.map((cat) => {
            const active = cat === selectedCategorie;
            const total = totals.get(cat) ?? 0;
            return (
              <button
                key={cat}
                type="button"
                onClick={() => setSelectedCategorie(cat)}
                className={`flex flex-col items-start gap-1 rounded-xl border px-3 py-3 text-left transition ${
                  active
                    ? 'border-indigo-300 bg-accent/10 ring-2 ring-indigo-200'
                    : 'border-border bg-bg-raised hover:border-border-hi'
                }`}
              >
                <span
                  className={`text-xs font-semibold uppercase tracking-wider ${
                    active ? 'text-accent' : 'text-fg-subtle'
                  }`}
                >
                  {CATEGORIE_COMPTABLE_LABELS[cat]}
                </span>
                <span
                  className={`text-lg font-bold ${
                    active ? 'text-accent' : 'text-fg'
                  }`}
                >
                  {total}
                </span>
                <span className="text-[10px] text-fg-subtle">
                  document{total > 1 ? 's' : ''}
                </span>
              </button>
            );
          })}
        </div>
      </Card>

      <Card>
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-accent" />
            <h3 className="text-sm font-semibold text-fg">
              {CATEGORIE_COMPTABLE_LABELS[selectedCategorie]} — {selectedYear ?? '—'}
            </h3>
            <Badge variant="neutral">{documents.length}</Badge>
          </div>
        </header>

        {readOnly && (
          <div className="px-5 pt-4">
            <DataroomReadOnlyHint
              statut={readOnlyStatut}
              testId="comptable-readonly-hint"
            />
          </div>
        )}

        {canUpload && (
          <div className="px-5 pt-4">
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                handleFilesUpload(e.dataTransfer.files);
              }}
              className={`flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 transition ${
                dragOver
                  ? 'border-indigo-400 bg-accent/10'
                  : 'border-border-hi bg-bg-overlay'
              }`}
            >
              <Upload className="h-6 w-6 text-fg-subtle" />
              <p className="text-sm text-fg-muted">
                Glissez-deposez un ou plusieurs fichiers ou
              </p>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                onChange={(e) => handleFilesUpload(e.target.files)}
                className="hidden"
              />
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => fileInputRef.current?.click()}
                loading={uploading}
              >
                <Upload className="mr-1 h-3.5 w-3.5" />
                Selectionner des fichiers
              </Button>
              <p className="text-xs text-fg-subtle">
                Categorie : {CATEGORIE_COMPTABLE_LABELS[selectedCategorie]} •
                Annee : {selectedYear ?? '—'}
              </p>
            </div>
          </div>
        )}

        <ul className="divide-y divide-border px-1 py-2">
          {documentsLoading && documents.length === 0 && (
            <li className="flex justify-center py-6">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-border border-t-indigo-600" />
            </li>
          )}
          {!documentsLoading && documents.length === 0 && (
            <li className="px-5 py-8 text-center text-sm text-fg-subtle">
              Aucun document pour cette categorie.
            </li>
          )}
          {documents.map((doc) => (
            <li
              key={doc.id}
              className="flex items-center justify-between gap-3 px-4 py-3"
            >
              <div className="flex min-w-0 items-center gap-3">
                <div className="rounded-lg bg-bg-overlay p-2">
                  <FileText className="h-4 w-4 text-fg-subtle" />
                </div>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">
                    {doc.title}
                  </p>
                  <p className="text-xs text-fg-subtle">
                    {doc.filename} •{' '}
                    {new Date(doc.createdAt).toLocaleDateString('fr-FR')} •{' '}
                    {Math.round(doc.sizeBytes / 1024)} Ko
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                {/* Lot AA — « Voir » TOUJOURS actif (visionnage inconditionnel). */}
                <button
                  type="button"
                  onClick={() => setPreviewDoc(doc)}
                  className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
                  aria-label="Voir"
                  title="Voir"
                >
                  <Eye className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => handleDownload(doc)}
                  className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
                  aria-label="Telecharger"
                  title="Telecharger"
                >
                  <Download className="h-4 w-4" />
                </button>
                {canDelete && (
                  <button
                    type="button"
                    onClick={() => setToDelete(doc)}
                    className="rounded-lg p-1.5 text-danger hover:bg-danger/10 hover:text-danger"
                    aria-label="Supprimer"
                    title="Supprimer"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {/* Lot AA — Apercu inline (PDF ou image) du document comptable. Le bouton
          Telecharger dans la modal suit perm_download (canDownload) ; le
          visionnage lui-meme est inconditionnel. */}
      {previewDoc && (
        <PdfPreviewModal
          open
          documentId={previewDoc.id}
          filename={previewDoc.filename}
          canDownload={canDownload}
          canPrint={canPrint}
          onClose={() => setPreviewDoc(null)}
          onDownload={() => handleDownload(previewDoc)}
          fetchPreview={dataroomService.previewComptable}
        />
      )}

      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(o) => !o && setToDelete(null)}
        title="Supprimer le document"
        description={toDelete ? `Supprimer "${toDelete.title}" ?` : ''}
        variant="danger"
        confirmLabel="Supprimer"
        loading={deleting}
        onConfirm={confirmDelete}
      />
    </div>
  );
}

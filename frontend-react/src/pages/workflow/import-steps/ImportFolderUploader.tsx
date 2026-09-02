import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Check,
  ChevronRight,
  Loader,
  Plus,
  Square,
  Trash2,
} from 'lucide-react';
import {
  extensionOf,
  forComptable,
  forFiscal,
  forJuridique,
  slugDenomination,
} from '../../../lib/namingConvention';

/**
 * Prompt G (2026-06-23) — Composant partagé utilisé pour les 3 dossiers
 * d'import (JURIDIQUE, COMPTABLE, FISCAL).
 *
 * <p>Chaque fichier ajouté reçoit ses métadonnées AU MOMENT de l'upload :
 * - JURIDIQUE : DocumentType (obligatoire) + date (optionnelle).
 * - COMPTABLE / FISCAL : Catégorie + Année (obligatoires).
 *
 * <p>Tant qu'un fichier a une métadonnée obligatoire manquante, son upload reste
 * en attente — mais cela n'empêche pas d'en ajouter d'autres.
 *
 * <p>Le guide de complétude affiche, pour chaque valeur de l'enum, ✔ si au moins
 * un fichier a été enregistré dans cette catégorie, ⬜ sinon. Non bloquant.
 *
 * <p>Le renommage suit la convention {@code namingConvention.ts}.
 */

export type FolderKind = 'JURIDIQUE' | 'COMPTABLE' | 'FISCAL';

export interface FolderCategoryOption {
  value: string;
  label: string;
}

export interface ImportFolderUploaderProps {
  kind: FolderKind;
  /** Liste des catégories proposées dans le sélecteur + guide de complétude. */
  categories: FolderCategoryOption[];
  denomination: string;
  /** Si true, demande une année par fichier (comptable / fiscal). */
  yearRequired?: boolean;
  /** Min/Max année (par défaut 1990–anneeCourante+1). */
  yearMin?: number;
  yearMax?: number;
  /** Liste initiale (rehydration depuis le workflow_progress). */
  initial?: ImportFile[];
  /** Fonction d'upload effective : reçoit le fichier renommé + ses métadonnées. */
  onUpload: (file: File, meta: ImportFileMeta) => Promise<void>;
  /** Callback invoqué quand la liste de docs change (pour persistance step). */
  onChange?: (files: ImportFile[]) => void;
  /** Désactive l'ajout (ex. pas de dossier rattaché côté caller). */
  disabled?: boolean;
}

export interface ImportFileMeta {
  /** Type/catégorie (DocumentType pour juridique, CategorieComptable/Fiscale sinon). */
  category: string;
  /** Année (comptable/fiscal) ou null (juridique). */
  annee: number | null;
  /** Date optionnelle (juridique, ISO YYYY-MM-DD). */
  dateIso?: string | null;
  /** Nom canonique après renommage. */
  canonicalName: string;
}

export interface ImportFile {
  id: string;
  originalName: string;
  sizeBytes: number;
  category: string;
  annee: number | null;
  dateIso: string | null;
  canonicalName: string | null;
  state: 'PENDING_META' | 'UPLOADING' | 'UPLOADED' | 'ERROR';
  error?: string;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `imp-${Date.now()}-${Math.random()}`;
}

export function ImportFolderUploader({
  kind,
  categories,
  denomination,
  yearRequired = false,
  yearMin,
  yearMax,
  initial = [],
  onUpload,
  onChange,
  disabled = false,
}: ImportFolderUploaderProps) {
  const currentYear = new Date().getFullYear();
  const yMin = yearMin ?? 1990;
  const yMax = yearMax ?? currentYear + 1;

  const [files, setFiles] = useState<ImportFile[]>(initial);

  useEffect(() => {
    onChange?.(files);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [files]);

  const denominationSlug = useMemo(() => slugDenomination(denomination), [denomination]);

  /** Validation d'une entrée : retourne null si OK, un message sinon. */
  function validateMeta(f: ImportFile): string | null {
    if (!f.category) return 'Catégorie obligatoire';
    if (yearRequired && (f.annee == null || Number.isNaN(f.annee))) {
      return 'Année obligatoire';
    }
    if (
      yearRequired &&
      f.annee != null &&
      (f.annee < yMin || f.annee > yMax)
    ) {
      return `Année hors bornes (${yMin}-${yMax})`;
    }
    return null;
  }

  function computeCanonical(f: ImportFile, originalName: string): string {
    const ext = extensionOf(originalName);
    if (kind === 'JURIDIQUE') {
      const dateOrYear = f.dateIso || (f.annee ? String(f.annee) : null);
      return forJuridique({
        documentType: f.category,
        denominationSlug,
        dateOrYear,
        extension: ext,
      });
    }
    if (kind === 'COMPTABLE') {
      return forComptable({
        annee: f.annee,
        categorie: f.category,
        denominationSlug,
        extension: ext,
      });
    }
    return forFiscal({
      annee: f.annee,
      categorie: f.category,
      denominationSlug,
      extension: ext,
    });
  }

  function addFile(raw: File) {
    const entry: ImportFile = {
      id: uuid(),
      originalName: raw.name,
      sizeBytes: raw.size,
      category: '',
      /*
       * Fix C3 (2026-08-16) — L'ANNÉE DOIT ÊTRE CHOISIE, PAS SUBIE.
       *
       * L'année était pré-remplie à l'année courante (2026). Comme `validateMeta`
       * n'attendait plus que la catégorie, le dépôt partait DÈS le choix de la
       * catégorie : une pièce d'un exercice antérieur était classée en 2026, et la
       * corriger ensuite n'y changeait rien — le fichier était déjà parti sous le
       * mauvais exercice. On laisse donc l'année vide : la validation bloque tant
       * qu'elle n'est pas renseignée, et le dépôt ne peut plus précéder le choix.
       */
      annee: null,
      dateIso: null,
      canonicalName: null,
      state: 'PENDING_META',
    };
    // On stocke le File hors state (perte sur reload — acceptable, l'utilisateur
    // re-uploade s'il refresh la page).
    pendingBlobs.current.set(entry.id, raw);
    setFiles((prev) => [...prev, entry]);
  }

  function patchFile(id: string, patch: Partial<ImportFile>) {
    setFiles((prev) => prev.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  }

  function removeFile(id: string) {
    pendingBlobs.current.delete(id);
    setFiles((prev) => prev.filter((f) => f.id !== id));
  }

  // Conserve les Blob hors React state.
  const pendingBlobs = useRefBlobs();

  const tryUpload = useCallback(
    async (entry: ImportFile) => {
      if (entry.state === 'UPLOADING' || entry.state === 'UPLOADED') return;
      const blob = pendingBlobs.current.get(entry.id);
      if (!blob) return; // déjà uploadé (rehydration) ou Blob perdu — silencieux.
      const err = validateMeta(entry);
      if (err) return; // metadata incomplète → on attend.
      const canonical = computeCanonical(entry, entry.originalName);
      const renamed = new File([blob], canonical.split('/').pop() || canonical, {
        type: blob.type,
      });
      patchFile(entry.id, { state: 'UPLOADING', error: undefined, canonicalName: canonical });
      try {
        await onUpload(renamed, {
          category: entry.category,
          annee: entry.annee,
          dateIso: entry.dateIso,
          canonicalName: canonical,
        });
        patchFile(entry.id, { state: 'UPLOADED' });
        pendingBlobs.current.delete(entry.id);
      } catch (ex) {
        const msg = ex instanceof Error ? ex.message : 'Echec de l\'upload';
        patchFile(entry.id, { state: 'ERROR', error: msg });
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [denominationSlug, kind, yearRequired],
  );

  // Auto-trigger upload dès que les métadonnées d'un fichier passent à VALIDES.
  useEffect(() => {
    files.forEach((f) => {
      if (
        f.state === 'PENDING_META' &&
        validateMeta(f) === null &&
        pendingBlobs.current.has(f.id)
      ) {
        void tryUpload(f);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [files, tryUpload]);

  // Guide de complétude : pour chaque catégorie, vrai si ≥1 fichier UPLOADED.
  const completeness = useMemo(() => {
    const set = new Set<string>();
    files.forEach((f) => {
      if (f.state === 'UPLOADED') set.add(f.category);
    });
    return categories.map((c) => ({ ...c, done: set.has(c.value) }));
  }, [files, categories]);

  const completedCount = completeness.filter((c) => c.done).length;
  const pendingMeta = files.filter((f) => f.state === 'PENDING_META').length;
  const errors = files.filter((f) => f.state === 'ERROR').length;

  return (
    <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
      <header className="mb-3 flex items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-fg">Dossier {kind.toLowerCase()}</h3>
          <p className="text-xs text-fg-subtle">
            {kind === 'JURIDIQUE'
              ? 'Statuts, PV, RC, CIN, JAL, etc. — tous formats acceptés.'
              : `Choisissez la catégorie + l'année ${yearRequired ? '(obligatoires)' : ''} pour chaque fichier.`}
          </p>
        </div>
        <label
          className={`inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg border-2 border-dashed border-border bg-bg-overlay px-3 text-sm transition ${
            disabled
              ? 'cursor-not-allowed opacity-50'
              : 'text-accent hover:border-accent'
          }`}
          data-testid={`import-add-${kind}`}
        >
          <Plus className="h-4 w-4" /> Ajouter des fichiers
          <input
            type="file"
            multiple
            accept="*/*"
            disabled={disabled}
            onChange={(e) => {
              if (!e.target.files) return;
              Array.from(e.target.files).forEach((f) => addFile(f));
              e.target.value = '';
            }}
            className="hidden"
            data-testid={`import-file-input-${kind}`}
          />
        </label>
      </header>

      {/* Guide de complétude */}
      <div className="mb-4 rounded-lg border border-border bg-bg-overlay p-3">
        <p className="mb-2 flex items-center gap-2 text-xs font-semibold text-fg">
          Guide de complétude
          <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-medium text-accent">
            {completedCount} / {categories.length}
          </span>
        </p>
        <ul
          className="grid grid-cols-1 gap-1 sm:grid-cols-2 lg:grid-cols-3"
          data-testid={`completeness-${kind}`}
        >
          {completeness.map((c) => (
            <li
              key={c.value}
              data-testid={`completeness-item-${kind}-${c.value}`}
              data-done={c.done}
              className="flex items-center gap-1 text-xs text-fg-muted"
            >
              {c.done ? (
                <Check className="h-3.5 w-3.5 text-success" />
              ) : (
                <Square className="h-3.5 w-3.5 text-fg-subtle" />
              )}
              {c.label}
            </li>
          ))}
        </ul>
        <p className="mt-2 text-[11px] text-fg-subtle">
          La liste est indicative : aucun blocage si des catégories restent vides.
        </p>
      </div>

      {/* Liste des fichiers + édition métadonnées */}
      {files.length === 0 ? (
        <p className="rounded-lg border border-border/60 bg-bg-overlay px-3 py-6 text-center text-xs text-fg-subtle">
          Aucun fichier ajouté. Vous pouvez finaliser sans en déposer ici.
        </p>
      ) : (
        <ul className="space-y-2">
          {files.map((f) => (
            <li
              key={f.id}
              data-testid={`import-row-${kind}-${f.id}`}
              className="rounded-lg border border-border p-3"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-fg" title={f.originalName}>
                    {f.originalName}
                  </p>
                  <p className="text-[11px] text-fg-subtle">
                    {(f.sizeBytes / 1024).toFixed(1)} Ko
                    {f.canonicalName && (
                      <>
                        {' • '}
                        <span className="font-mono text-[10px]" title={f.canonicalName}>
                          → {f.canonicalName}
                        </span>
                      </>
                    )}
                  </p>
                </div>
                <StatusBadge state={f.state} error={f.error} />
                <button
                  type="button"
                  onClick={() => removeFile(f.id)}
                  className="rounded p-1 text-danger hover:bg-danger/10"
                  aria-label="Retirer"
                  data-testid={`import-remove-${f.id}`}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>

              <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-3">
                <div>
                  <label
                    htmlFor={`cat-${f.id}`}
                    className="mb-0.5 block text-[11px] text-fg-subtle"
                  >
                    Catégorie *
                  </label>
                  <select
                    id={`cat-${f.id}`}
                    value={f.category}
                    onChange={(e) => patchFile(f.id, { category: e.target.value })}
                    disabled={f.state === 'UPLOADED' || f.state === 'UPLOADING'}
                    data-testid={`import-cat-${f.id}`}
                    className="h-8 w-full rounded-lg border border-border-hi bg-bg-raised px-2 text-xs"
                  >
                    <option value="">Sélectionner…</option>
                    {categories.map((c) => (
                      <option key={c.value} value={c.value}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </div>
                {yearRequired && (
                  <div>
                    <label
                      htmlFor={`year-${f.id}`}
                      className="mb-0.5 block text-[11px] text-fg-subtle"
                    >
                      Année *
                    </label>
                    <input
                      id={`year-${f.id}`}
                      type="number"
                      min={yMin}
                      max={yMax}
                      value={f.annee ?? ''}
                      onChange={(e) =>
                        patchFile(f.id, {
                          annee: e.target.value ? parseInt(e.target.value, 10) : null,
                        })
                      }
                      disabled={f.state === 'UPLOADED' || f.state === 'UPLOADING'}
                      data-testid={`import-year-${f.id}`}
                      className="h-8 w-full rounded-lg border border-border-hi bg-bg-raised px-2 text-xs"
                    />
                  </div>
                )}
                {kind === 'JURIDIQUE' && (
                  <div>
                    <label
                      htmlFor={`date-${f.id}`}
                      className="mb-0.5 block text-[11px] text-fg-subtle"
                    >
                      Date (optionnel)
                    </label>
                    <input
                      id={`date-${f.id}`}
                      type="date"
                      value={f.dateIso ?? ''}
                      onChange={(e) =>
                        patchFile(f.id, { dateIso: e.target.value || null })
                      }
                      disabled={f.state === 'UPLOADED' || f.state === 'UPLOADING'}
                      data-testid={`import-date-${f.id}`}
                      className="h-8 w-full rounded-lg border border-border-hi bg-bg-raised px-2 text-xs"
                    />
                  </div>
                )}
              </div>

              {f.state === 'ERROR' && f.error && (
                <p
                  role="alert"
                  className="mt-2 flex items-start gap-1 text-[11px] text-danger"
                  data-testid={`import-error-${f.id}`}
                >
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" /> {f.error}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {(pendingMeta > 0 || errors > 0) && (
        <p className="mt-3 text-[11px] text-fg-subtle">
          {pendingMeta > 0 && <>{pendingMeta} fichier(s) en attente de métadonnées. </>}
          {errors > 0 && <>{errors} échec(s) d'upload — corrigez et réessayez. </>}
        </p>
      )}
    </div>
  );
}

function StatusBadge({ state, error }: { state: ImportFile['state']; error?: string }) {
  if (state === 'UPLOADED')
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
        <Check className="h-3 w-3" /> Déposé
      </span>
    );
  if (state === 'UPLOADING')
    return (
      <span className="inline-flex items-center gap-1 text-[10px] font-medium text-fg-subtle">
        <Loader className="h-3 w-3 animate-spin" /> En cours
      </span>
    );
  if (state === 'ERROR')
    return (
      <span
        className="inline-flex items-center gap-1 rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-danger"
        title={error}
      >
        <AlertCircle className="h-3 w-3" /> Echec
      </span>
    );
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-bg-overlay px-2 py-0.5 text-[10px] font-semibold uppercase text-fg-subtle">
      <ChevronRight className="h-3 w-3" /> En attente
    </span>
  );
}

import { useRef } from 'react';
function useRefBlobs() {
  // Conserve les Blob hors React state pour éviter de les sérialiser dans onChange.
  return useRef(new Map<string, File>());
}

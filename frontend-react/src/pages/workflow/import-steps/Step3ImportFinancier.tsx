import { useCallback, useMemo, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { dataroomService } from '../../../services/dataroom.service';
import {
  CATEGORIES_FISCALES_LABELS,
  CATEGORIES_FISCALES_ORDER,
  CATEGORIE_COMPTABLE_LABELS,
  CATEGORIE_COMPTABLE_ORDER,
  type CategorieComptable,
  type CategorieFiscale,
} from '../../../types/dataroom';
import {
  ImportFolderUploader,
  type FolderCategoryOption,
  type ImportFile,
  type ImportFileMeta,
} from './ImportFolderUploader';

/**
 * Prompt G/H (2026-06-23) — IMPORT étape 3 (financier).
 *
 * <p>Refonte autour de {@link ImportFolderUploader} : 2 panneaux (COMPTABLE +
 * FISCAL). Métadonnées par fichier (catégorie + année obligatoires). Renommage
 * normalisé via {@code namingConvention.ts}. Guide de complétude affiché.
 *
 * <p>Le back accepte désormais une {@code annee} brute pour l'upload fiscal
 * (l'exercice est créé à la volée). Cf {@code DataroomFiscalService.uploadByAnnee}.
 *
 * <p>L'étape reste OPTIONNELLE : un dossier sans documents NE BLOQUE PAS.
 */
interface Props {
  existing?: Record<string, unknown>;
  /** Dossier auto-créé au départ du ticket IMPORT (RG-IM01). */
  dossierId?: string | null;
  /** Dénomination société pour les slugs (depuis step1.info.raisonSociale). */
  denomination?: string | null;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

const COMPTABLE_OPTIONS: FolderCategoryOption[] = CATEGORIE_COMPTABLE_ORDER.map(
  (c: CategorieComptable) => ({ value: c, label: CATEGORIE_COMPTABLE_LABELS[c] }),
);
const FISCAL_OPTIONS: FolderCategoryOption[] = CATEGORIES_FISCALES_ORDER
  // CONTENTIEUX exclu en import : exige un commentaire ≥ 20 chars (RG-DF23) qui
  // n'est pas capturé par cette UI. L'employé peut ajouter du contentieux via la
  // Data Room fiscale dédiée après l'import.
  .filter((c) => c !== 'CONTENTIEUX')
  .map((c: CategorieFiscale) => ({ value: c, label: CATEGORIES_FISCALES_LABELS[c] }));

/**
 * Sous-classification fiscale par défaut, par catégorie. Le back exige une
 * sous-classification non vide (RG-DF16). En import on choisit la 1re entrée
 * cohérente pour chaque catégorie (l'employé peut affiner plus tard via la
 * Data Room fiscale).
 */
const FISCAL_DEFAULT_SOUS_CLASSIFICATION: Record<string, string> = {
  TVA: 'DECLARATION_MENSUELLE',
  IS: 'DECLARATION_ANNUELLE',
  IR: 'DECLARATION_IR_PRO',
  TP_TSC: 'DECLARATION_EXISTENCE',
  RAS: 'HONORAIRES_10',
  ATTESTATIONS: 'ATTESTATION_TVA',
  CONTENTIEUX: 'NOTIFICATION_DGI',
  AUTRE: 'AUTRE',
};

export function Step3ImportFinancier({
  existing,
  dossierId,
  denomination,
  saving,
  onSubmit,
}: Props) {
  // Rehydration depuis le workflow_progress : pas de Blob, juste les métadonnées.
  const initialComptable =
    (existing?.documentsFinanciers as ImportFile[] | undefined) ?? [];
  const initialFiscal =
    (existing?.documentsFiscaux as ImportFile[] | undefined) ?? [];

  const [comptableDocs, setComptableDocs] = useState<ImportFile[]>(initialComptable);
  const [fiscalDocs, setFiscalDocs] = useState<ImportFile[]>(initialFiscal);
  const [globalError, setGlobalError] = useState<string | null>(null);

  const denominationStr = denomination || 'Société';
  const noDossier = !dossierId;

  const onUploadComptable = useCallback(
    async (file: File, meta: ImportFileMeta) => {
      if (!dossierId) throw new Error('Dossier non rattaché au ticket');
      await dataroomService.uploadComptable(dossierId, {
        file,
        annee: meta.annee ?? new Date().getFullYear(),
        categorie: meta.category,
        title: meta.canonicalName,
      });
    },
    [dossierId],
  );

  const onUploadFiscal = useCallback(
    async (file: File, meta: ImportFileMeta) => {
      if (!dossierId) throw new Error('Dossier non rattaché au ticket');
      const sousClassif =
        FISCAL_DEFAULT_SOUS_CLASSIFICATION[meta.category] ?? 'AUTRE';
      await dataroomService.uploadFiscal(dossierId, {
        file,
        annee: meta.annee ?? new Date().getFullYear(),
        categorie: meta.category,
        sousClassification: sousClassif,
        title: meta.canonicalName,
      });
    },
    [dossierId],
  );

  const uploadedComptable = useMemo(
    () => comptableDocs.filter((f) => f.state === 'UPLOADED').length,
    [comptableDocs],
  );
  const uploadedFiscal = useMemo(
    () => fiscalDocs.filter((f) => f.state === 'UPLOADED').length,
    [fiscalDocs],
  );

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        setGlobalError(null);
        // Persistance : on conserve les métadonnées de chaque doc (sans Blob).
        const stripBlobsFlag = (arr: ImportFile[]) =>
          arr.map((f) => ({
            id: f.id,
            originalName: f.originalName,
            sizeBytes: f.sizeBytes,
            category: f.category,
            annee: f.annee,
            dateIso: f.dateIso,
            canonicalName: f.canonicalName,
            state: f.state,
          }));
        void onSubmit({
          financier: {
            documentsFinanciers: stripBlobsFlag(comptableDocs),
            documentsFiscaux: stripBlobsFlag(fiscalDocs),
          },
        });
      }}
      className="mx-auto max-w-[1000px] space-y-5"
    >
      <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-xs text-fg">
        Étape optionnelle — importez les documents comptables et fiscaux par
        exercice. Chaque fichier est renommé suivant la convention de la Data
        Room et rangé automatiquement (
        {uploadedComptable} comptable + {uploadedFiscal} fiscal déposés).
      </div>

      {noDossier && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          Le ticket n'a pas de dossier rattaché — les uploads sont désactivés.
        </div>
      )}

      <ImportFolderUploader
        kind="COMPTABLE"
        categories={COMPTABLE_OPTIONS}
        denomination={denominationStr}
        yearRequired
        initial={initialComptable}
        disabled={noDossier}
        onUpload={onUploadComptable}
        onChange={setComptableDocs}
      />

      <ImportFolderUploader
        kind="FISCAL"
        categories={FISCAL_OPTIONS}
        denomination={denominationStr}
        yearRequired
        initial={initialFiscal}
        disabled={noDossier}
        onUpload={onUploadFiscal}
        onChange={setFiscalDocs}
      />

      {globalError && (
        <p className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
          {globalError}
        </p>
      )}

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={saving}
          data-testid="step3-submit"
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            !saving
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Validation en cours...' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

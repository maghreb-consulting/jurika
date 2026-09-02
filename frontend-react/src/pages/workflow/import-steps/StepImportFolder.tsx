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
 * Refonte IMPORT 2026-06-25 — Etape d'upload d'UN dossier financier (COMPTABLE
 * OU FISCAL). Extrait de {@link Step3ImportFinancier} pour permettre 2 etapes
 * distinctes (etape 8 = comptable, etape 9 = fiscal), conformement a la cible
 * « 3 etapes d'upload typees ». Depot direct en Data Room via les endpoints
 * existants {@code uploadComptable} / {@code uploadFiscal} (cf F1) ; categorie +
 * annee choisies PAR FICHIER avant upload (aucun defaut qui ecrase).
 *
 * <p>Etape OPTIONNELLE : un dossier sans documents NE BLOQUE PAS la progression.
 */
interface Props {
  kind: 'COMPTABLE' | 'FISCAL';
  /** Dossier auto-cree au depart du ticket IMPORT (RG-IM01). */
  dossierId?: string | null;
  /** Denomination societe pour les slugs de renommage. */
  denomination?: string | null;
  existing?: Record<string, unknown>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

const COMPTABLE_OPTIONS: FolderCategoryOption[] = CATEGORIE_COMPTABLE_ORDER.map(
  (c: CategorieComptable) => ({ value: c, label: CATEGORIE_COMPTABLE_LABELS[c] }),
);
const FISCAL_OPTIONS: FolderCategoryOption[] = CATEGORIES_FISCALES_ORDER
  // CONTENTIEUX exclu en import : exige un commentaire >= 20 chars (RG-DF23) non
  // capture par cette UI. L'employe l'ajoutera via la Data Room fiscale dediee.
  .filter((c) => c !== 'CONTENTIEUX')
  .map((c: CategorieFiscale) => ({ value: c, label: CATEGORIES_FISCALES_LABELS[c] }));

/** Sous-classification fiscale par defaut, par categorie (le back exige RG-DF16). */
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

function stripBlobsFlag(arr: ImportFile[]) {
  return arr.map((f) => ({
    id: f.id,
    originalName: f.originalName,
    sizeBytes: f.sizeBytes,
    category: f.category,
    annee: f.annee,
    dateIso: f.dateIso,
    canonicalName: f.canonicalName,
    state: f.state,
  }));
}

export function StepImportFolder({
  kind,
  dossierId,
  denomination,
  existing,
  saving,
  onSubmit,
}: Props) {
  const isComptable = kind === 'COMPTABLE';
  const payloadKey = isComptable ? 'documentsFinanciers' : 'documentsFiscaux';
  const wrapKey = isComptable ? 'comptable' : 'fiscal';

  const initial = (existing?.[payloadKey] as ImportFile[] | undefined) ?? [];
  const [docs, setDocs] = useState<ImportFile[]>(initial);

  const denominationStr = denomination || 'Société';
  const noDossier = !dossierId;

  const onUpload = useCallback(
    async (file: File, meta: ImportFileMeta) => {
      if (!dossierId) throw new Error('Dossier non rattaché au ticket');
      if (isComptable) {
        await dataroomService.uploadComptable(dossierId, {
          file,
          annee: meta.annee ?? new Date().getFullYear(),
          categorie: meta.category,
          title: meta.canonicalName,
        });
      } else {
        const sousClassif = FISCAL_DEFAULT_SOUS_CLASSIFICATION[meta.category] ?? 'AUTRE';
        await dataroomService.uploadFiscal(dossierId, {
          file,
          annee: meta.annee ?? new Date().getFullYear(),
          categorie: meta.category,
          sousClassification: sousClassif,
          title: meta.canonicalName,
        });
      }
    },
    [dossierId, isComptable],
  );

  const uploadedCount = useMemo(
    () => docs.filter((f) => f.state === 'UPLOADED').length,
    [docs],
  );

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        const stripped = stripBlobsFlag(docs);
        void onSubmit({
          [payloadKey]: stripped,
          // Miroir niche pour la rehydratation a la reouverture du wizard.
          [wrapKey]: { [payloadKey]: stripped },
        });
      }}
      className="mx-auto max-w-[1000px] space-y-5"
    >
      <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-xs text-fg">
        Etape optionnelle — importez les documents{' '}
        {isComptable ? 'comptables' : 'fiscaux'} par exercice. Choisissez la
        catégorie + l'année <strong>par fichier</strong> : le dépôt en Data Room
        ne se déclenche qu'une fois ces deux informations renseignées (
        {uploadedCount} déposé{uploadedCount > 1 ? 's' : ''}).
      </div>

      {noDossier && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          Le ticket n'a pas de dossier rattaché — les uploads sont désactivés.
        </div>
      )}

      <ImportFolderUploader
        kind={kind}
        categories={isComptable ? COMPTABLE_OPTIONS : FISCAL_OPTIONS}
        denomination={denominationStr}
        yearRequired
        initial={initial}
        disabled={noDossier}
        onUpload={onUpload}
        onChange={setDocs}
      />

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={saving}
          data-testid={`step-import-${kind.toLowerCase()}-submit`}
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

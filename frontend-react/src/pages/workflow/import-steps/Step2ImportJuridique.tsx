import { useRef, useState } from 'react';
import { useStepAutosave, type RegisterDirty } from '../useStepAutosave';
import {
  CheckCircle2,
  ChevronRight,
  FileText,
  Loader,
  Sparkles,
  Trash2,
  Upload,
} from 'lucide-react';
import { aiService } from '../../../services/workflow.service';
import { dataroomService } from '../../../services/dataroom.service';
import {
  extensionOf,
  forJuridique,
  slugDenomination,
} from '../../../lib/namingConvention';
import {
  OcrSuggestionsPanel,
  type OcrSuggestion,
} from '../../../components/workflow/OcrSuggestionsPanel';
import { PublicitesSociete } from '../../../components/workflow/PublicitesSociete';
import { manquePublicite, type Publicite } from '../../../lib/publicites';

interface Props {
  existing?: Record<string, unknown>;
  /** Dossier deja cree au depart du ticket IMPORT (auto-create cote ticket-service). */
  dossierId?: string | null;
  /**
   * Prompt H (2026-06-23) — dénomination société (step1.info.raisonSociale)
   * utilisée pour appliquer la convention de renommage normalisée avant upload.
   * Fallback "Société" si absente.
   */
  denomination?: string | null;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  /** Lot L7 (E2) : enregistrement automatique de la saisie (voir useStepAutosave). */
  registerDirty?: RegisterDirty;
}

type UiDocType =
  | 'STATUTS'
  | 'BAIL'
  | 'DOMICILIATION'
  | 'CNIE'
  | 'CN'
  | 'RC'
  | 'IF'
  | 'AUTRE';

const DOC_TYPE_LABELS: Record<UiDocType, string> = {
  STATUTS: 'Statuts',
  BAIL: 'Contrat de bail',
  DOMICILIATION: 'Contrat de domiciliation',
  CNIE: 'CNIE / Carte nationale',
  CN: 'Certificat négatif',
  RC: 'Registre du commerce',
  IF: 'Identifiant fiscal',
  AUTRE: 'Autre document',
};

/**
 * Mapping UI -> backend.
 *  - {@code aiSchema}    : code attendu par {@code /ai/extract?type=...}
 *    (cf DocumentSchemaRegistry cote ai-service). Si {@code null}, on saute
 *    l'extraction LLM (la suggestion n'aurait pas de schema declaratif).
 *  - {@code dataroomType}: documentType persiste cote dataroom-service
 *    (cf DocumentType cote frontend / DataroomJuridiqueService cote backend).
 *
 * Fix 2026-06-06 : avant ce mapping, le front envoyait directement "STATUTS"
 * ou "RC" a {@code /ai/extract}, ce qui declenchait une 400 silencieuse
 * (le registry n'a que STATUTS_SARL, RC_IMMATRICULATION, etc.). Les blobs
 * etaient ensuite jetes -- aucun upload reel dans la dataroom.
 */
const DOC_TYPE_BINDING: Record<
  UiDocType,
  { aiSchema: string | null; dataroomType: string }
> = {
  STATUTS: { aiSchema: 'STATUTS_SARL', dataroomType: 'STATUTS' },
  BAIL: { aiSchema: 'JUSTIFICATIF_SIEGE', dataroomType: 'CONTRAT_BAIL' },
  DOMICILIATION: { aiSchema: 'JUSTIFICATIF_SIEGE', dataroomType: 'CONTRAT_BAIL' },
  CNIE: { aiSchema: 'CIN', dataroomType: 'CNIE_GERANT' },
  CN: { aiSchema: 'CERTIFICAT_NEGATIF', dataroomType: 'AUTRE' },
  RC: { aiSchema: 'RC_IMMATRICULATION', dataroomType: 'RC' },
  IF: { aiSchema: 'IF_DECLARATION', dataroomType: 'AUTRE' },
  AUTRE: { aiSchema: null, dataroomType: 'AUTRE' },
};

/**
 * Mapping champ extrait par LLM -> champ form Step1.
 * Cle = nom de champ tel que renvoye par /ai/extract (depend du schema). On
 * tolere plusieurs alias par champ Step1 puisque differents schemas peuvent
 * fournir la meme donnee (raisonSociale via STATUTS_SARL OU via RC).
 */
const LLM_FIELD_TO_INFO: Record<string, string> = {
  raisonSociale: 'raisonSociale',
  denomination: 'raisonSociale',
  ice: 'ice',
  rcNumero: 'rcNumero',
  ifNumero: 'ifNumero',
  capitalSocial: 'capital',
  formeJuridique: 'formeJuridique',
  siegeSocial: 'siegeAdresse',
  adresse: 'siegeAdresse',
  ville: 'siegeVille',
};

interface UploadedDoc {
  /** UUID local UI. */
  id: string;
  filename: string;
  sizeBytes: number;
  /**
   * Fix 2026-06-25 — Type CHOISI PAR FICHIER. La valeur vide `''` signifie
   * "type non encore defini" : dans cet etat le fichier reste EN ATTENTE et
   * n'est JAMAIS uploade (plus de defaut 'STATUTS' qui forcait le type errone).
   */
  uiType: UiDocType | '';
  /** True quand le fichier est persiste dans la dataroom (RG-IM03). */
  uploaded: boolean;
  /** True pendant l'upload dataroom (UI non bloquante). */
  busy: boolean;
  /**
   * Fix 2026-06-07 (BUG 5 finition) — Etat OCR strictement separe de
   * busy/uploaded. L'OCR n'est PLUS auto-declenchee : elle reste
   * 100 % a la demande via le bouton "Extraire" par document.
   */
  extracting: boolean;
  /** ID du document Dataroom apres upload. */
  documentId?: string;
  /** Champs extraits par /ai/extract -- consommes par Step1 en suggestions. */
  fields?: Record<string, string>;
  source?: string;
  extractionMode?: string;
  confidence?: number;
  warnings?: string[];
  manualFallback?: boolean;
  /** Erreur d'extraction (OCR), non bloquante. */
  extractError?: string;
  /** Erreur d'upload dataroom, non bloquante. */
  error?: string;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `doc-${Date.now()}-${Math.random()}`;
}

/**
 * IMPORT -- Etape 2 : depot des documents juridiques.
 *
 * Pipeline (fix 2026-06-07 BUG 5 finition) :
 *  1. Drag-drop / file picker -> on conserve le {@code File} dans une ref locale
 *     {@code blobsRef} (la state React ne serialise pas les blobs).
 *  2. Upload IMMEDIAT dans la dataroom juridique du dossier du ticket
 *     (auto-create cote ticket-service IMPORT -> dossierId disponible).
 *     Le user peut valider l'etape des que les uploads sont termines, SANS
 *     attendre d'extraction.
 *  3. OCR/Extraction LLM : strictement OPTIONNELLE et A LA DEMANDE -- un
 *     bouton "Extraire" par document declenche {@code aiService.extract}
 *     avec le bon schema (STATUTS_SARL, RC_IMMATRICULATION, etc.). Le user
 *     n'a JAMAIS besoin d'attendre l'OCR pour avancer.
 *  4. Les champs extraits sont accumules au niveau du form puis remontes a
 *     l'orchestrateur ({@code onSubmit}) pour que Step1 puisse les recuperer
 *     en SUGGESTIONS (le composant {@link OcrSuggestionsPanel} reste
 *     non destructif -- l'utilisateur applique champ par champ).
 *
 * Resultat : les documents sont REELLEMENT presents dans la dataroom
 * (visibles dans /app/dataroom/dossiers/<id>/juridique). L'OCR n'est plus
 * un goulot d'etranglement.
 */
export function Step2ImportJuridique({ existing, dossierId, denomination, saving, onSubmit, registerDirty }: Props) {
  // Fix 2026-06-25 — Rehydration tolerante : les docs persistes utilisent la
  // cle `type` (cf payload onSubmit) ; on la remappe vers `uiType` et on
  // neutralise les flags transients. Plus aucun defaut 'STATUTS' force.
  const initial: UploadedDoc[] = (
    (existing?.documents as Array<Record<string, unknown>> | undefined) ?? []
  ).map((d) => ({
    id: (d.id as string) ?? uuid(),
    filename: (d.filename as string) ?? 'document',
    sizeBytes: (d.sizeBytes as number) ?? 0,
    uiType: ((d.uiType ?? d.type) as UiDocType | undefined) ?? '',
    uploaded: !!d.uploaded,
    busy: false,
    extracting: false,
    documentId: d.documentId as string | undefined,
  }));
  const [docs, setDocs] = useState<UploadedDoc[]>(initial);
  // Lot L4d (E8) : publicites de la societe (annonces JAL et Bulletin officiel).
  const [publicites, setPublicites] = useState<Publicite[]>(
    (existing?.publicites as Publicite[] | undefined) ?? [],
  );
  const [dragOver, setDragOver] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  /** Refs vers les blobs originaux -- pas de re-render au stockage. */
  const blobsRef = useRef<Map<string, File>>(new Map());
  // Lot L7 (E2) : documents deja deposes et publicites enregistres sans clic. Meme
  // forme que la validation ; les publicites figurent aussi sous `juridique`, que la
  // relecture de l'etape privilegie (unwrap).
  useStepAutosave(
    7,
    () => {
      const documents = docs
        .filter((d) => d.uploaded)
        .map((d) => ({
          id: d.id,
          filename: d.filename,
          sizeBytes: d.sizeBytes,
          type: d.uiType,
          dataroomType: d.uiType ? DOC_TYPE_BINDING[d.uiType].dataroomType : 'AUTRE',
          uploaded: d.uploaded,
          documentId: d.documentId,
        }));
      return {
        documents,
        documentsJuridiques: documents,
        publicites,
        juridique: {
          documents,
          publicites,
          piecesUploaded: documents.map((d) => ({
            code: d.type,
            filename: d.filename,
            sizeBytes: d.sizeBytes,
            type: d.type,
            dataroomDocumentId: d.documentId,
          })),
          extractedFields: aggregateExtractedFields(),
        },
      };
    },
    registerDirty,
  );

  /**
   * Fix 2026-06-25 — Ajout d'un fichier SANS upload. Le fichier entre en
   * attente avec `uiType = ''` (type non defini). AUCUN depot dataroom n'est
   * declenche tant que l'employe n'a pas choisi un type valide par fichier
   * (cf {@link #updateType}). Corrige le bug ou tout arrivait type STATUTS.
   */
  function addFile(file: File) {
    const id = uuid();
    blobsRef.current.set(id, file);
    setDocs((arr) => [
      ...arr,
      {
        id,
        filename: file.name,
        sizeBytes: file.size,
        uiType: '',
        uploaded: false,
        busy: false,
        extracting: false,
      },
    ]);
    setGlobalError(null);
  }

  /**
   * Fix 2026-06-25 — Depot dataroom effectif d'un document dont le type vient
   * d'etre choisi. Garde : ne se declenche QUE si le blob est disponible et le
   * dossier rattache. Le nom canonique reflete le VRAI type (RC__..., BAIL__...).
   */
  async function uploadDoc(id: string, uiType: UiDocType) {
    const file = blobsRef.current.get(id);
    if (!file) return;
    if (!dossierId) {
      setGlobalError(
        'Aucun dossier n’est rattaché à ce ticket : le dépôt dans la Data Room est impossible. '
          + 'Revenez à la liste des tickets et rouvrez le parcours.',
      );
      return;
    }
    setDocs((arr) =>
      arr.map((d) => (d.id === id ? { ...d, busy: true, error: undefined } : d)),
    );
    setGlobalError(null);

    const binding = DOC_TYPE_BINDING[uiType];

    let uploadError: string | null = null;
    // Prompt H (2026-06-23) — convention de renommage normalisée :
    // <DOCUMENT_TYPE>__<DENOMINATION_SLUG>__<DATE_ISO?>.<ext>
    const denominationSlug = slugDenomination(denomination ?? 'Société');
    const ext = extensionOf(file.name);
    const canonicalName = forJuridique({
      documentType: String(binding.dataroomType),
      denominationSlug,
      // pas de date à ce stade (OCR pourra la fournir plus tard)
      extension: ext,
    });
    const renamedFile = new File([file], canonicalName, { type: file.type });
    try {
      await dataroomService.uploadJuridique(dossierId, {
        file: renamedFile,
        documentType: binding.dataroomType,
        title: canonicalName.replace(/\.[^.]+$/, ''),
      });
    } catch (err) {
      uploadError = (err as Error)?.message ?? 'Le dépôt dans la Data Room a échoué : réessayez.';
    }

    setDocs((arr) =>
      arr.map((d) => {
        if (d.id !== id) return d;
        return {
          ...d,
          busy: false,
          uploaded: !uploadError,
          error: uploadError ?? undefined,
        };
      }),
    );
  }

  /**
   * Fix 2026-06-07 (BUG 5 finition) — Extraction OCR a la demande.
   * Appelle {@code aiService.extract} avec le schema correspondant au
   * type UI. N'echoue jamais "fort" : en cas d'erreur, on stocke le
   * message dans {@code extractError} et le user peut continuer.
   */
  async function extractFor(id: string) {
    const doc = docs.find((d) => d.id === id);
    const file = blobsRef.current.get(id);
    if (!doc || !file || !doc.uiType) return;
    const binding = DOC_TYPE_BINDING[doc.uiType];
    if (!binding.aiSchema) {
      // Pas de schema = pas d'extraction possible pour ce type. Le bouton
      // ne devrait pas etre visible dans ce cas, mais on protege quand meme.
      setDocs((arr) =>
        arr.map((d) => (d.id === id
          ? { ...d, extractError: 'Aucune extraction automatique n’est disponible pour ce type de document.' }
          : d)),
      );
      return;
    }
    setDocs((arr) =>
      arr.map((d) => (d.id === id ? { ...d, extracting: true, extractError: undefined } : d)),
    );
    let extraction: Record<string, unknown> | null = null;
    let extractError: string | null = null;
    try {
      extraction = await aiService.extract(file, binding.aiSchema);
    } catch (err) {
      extractError = (err as Error)?.message ?? 'L’extraction automatique est indisponible.';
    }
    setDocs((arr) =>
      arr.map((d) => {
        if (d.id !== id) return d;
        const fields = (extraction?.fields as Record<string, string> | undefined) ?? undefined;
        return {
          ...d,
          extracting: false,
          fields,
          source: extraction?.source as string | undefined,
          extractionMode: extraction?.extractionMode as string | undefined,
          confidence:
            typeof extraction?.confidence === 'number'
              ? (extraction.confidence as number)
              : undefined,
          warnings: Array.isArray(extraction?.warnings)
            ? (extraction.warnings as string[])
            : undefined,
          manualFallback: !!extraction?.degraded,
          extractError: extractError ?? undefined,
        };
      }),
    );
  }

  function handleFiles(files: FileList | null) {
    if (!files) return;
    Array.from(files).forEach((f) => addFile(f));
  }

  function handleDrop(ev: React.DragEvent<HTMLLabelElement>) {
    ev.preventDefault();
    setDragOver(false);
    Array.from(ev.dataTransfer.files).forEach((f) => addFile(f));
  }

  function remove(id: string) {
    blobsRef.current.delete(id);
    setDocs((arr) => arr.filter((d) => d.id !== id));
  }

  /**
   * Fix 2026-06-25 — Choix du type PAR FICHIER. C'est le SEUL declencheur
   * d'upload : des qu'un type valide est selectionne et que le document n'est
   * ni en cours, ni deja depose, on lance le depot dataroom avec ce type
   * (et le nom canonique correspondant). Repasser sur "" ne fait rien.
   */
  function updateType(id: string, uiType: UiDocType | '') {
    setDocs((arr) => arr.map((d) => (d.id === id ? { ...d, uiType } : d)));
    if (!uiType) return;
    const current = docs.find((d) => d.id === id);
    if (current && (current.busy || current.uploaded)) return;
    void uploadDoc(id, uiType);
  }

  /**
   * Aggrege toutes les suggestions LLM en un seul Map {champStep1 -> valeur}.
   * En cas de doublon (ex deux docs extraient {@code raisonSociale}), la
   * derniere valeur l'emporte -- on accepte ce comportement deterministe.
   */
  function aggregateExtractedFields(): Record<string, string> {
    const acc: Record<string, string> = {};
    for (const d of docs) {
      if (!d.fields) continue;
      for (const [llmKey, value] of Object.entries(d.fields)) {
        const formField = LLM_FIELD_TO_INFO[llmKey];
        if (formField && value && String(value).trim()) {
          acc[formField] = String(value);
        }
      }
    }
    return acc;
  }

  const uploadedCount = docs.filter((d) => d.uploaded).length;
  // Fix 2026-06-25 — Nombre de fichiers en attente de type (bloquant pour
  // continuer : un fichier sans type ne sera jamais depose).
  const untypedCount = docs.filter((d) => !d.uiType).length;
  // Fix 2026-06-07 (BUG 5) — On garde uniquement la contrainte "au moins
  // un document" (RG-IM02 backend). Le statut `uploaded` n'est PLUS exige
  // pour avancer : si le dossier dataroom n'est pas joignable ou si la
  // categorie n'a pas de schema OCR, le user peut quand meme valider.
  // L'OCR (extraction LLM) reste 100% optionnel.
  // Fix 2026-06-25 — En revanche, TOUS les fichiers doivent avoir un type
  // (sinon ils ne sont pas deposes) : on bloque la validation tant qu'il en
  // reste sans type.
  const canSubmit =
    docs.length >= 1 && docs.every((d) => !d.busy) && untypedCount === 0;

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        // Garde-fou JS explicite (au moins 1 doc, aucun fichier sans type, pas
        // d'upload en cours) depuis que `noValidate` desactive les bulles natives.
        if (!canSubmit || !dossierId) return;
        const pubIncomplete = publicites.findIndex((p) => manquePublicite(p) !== null);
        if (pubIncomplete >= 0) {
          setGlobalError(`Publicité n° ${pubIncomplete + 1} : ${manquePublicite(publicites[pubIncomplete])}`);
          return;
        }
        const documents = docs.map((d) => ({
          id: d.id,
          filename: d.filename,
          sizeBytes: d.sizeBytes,
          type: d.uiType,
          dataroomType: d.uiType ? DOC_TYPE_BINDING[d.uiType].dataroomType : 'AUTRE',
          uploaded: d.uploaded,
          documentId: d.documentId,
        }));
        const piecesUploaded = docs
          .filter((d) => d.uploaded)
          .map((d) => ({
            code: d.uiType,
            filename: d.filename,
            sizeBytes: d.sizeBytes,
            type: d.uiType,
            dataroomDocumentId: d.documentId,
          }));
        // Fix 2026-06-07 (BUG 5) — Le back ImportWorkflow.handleDocsJuridiques
        // lit `p.get("documents")` ou `p.get("documentsJuridiques")` A PLAT
        // sur le payload. L'ancien front emballait tout sous `juridique: {...}`
        // d'ou le 400 systematique "Au moins un document juridique requis".
        // Fix : on envoie `documents` (et alias) A LA RACINE + on conserve
        // l'enveloppe `juridique` pour les consommateurs internes (Step5
        // synthese, suggestions OCR).
        void onSubmit({
          documents,
          documentsJuridiques: documents,
          publicites,
          juridique: {
            documents,
            piecesUploaded,
            extractedFields: aggregateExtractedFields(),
          },
        });
      }}
      className="mx-auto max-w-[900px] space-y-6"
    >
      <div className="rounded-xl border border-info/30 bg-info-soft p-4 text-xs text-fg">
        Déposez les documents juridiques existants (statuts, bail, CNIE, RC, IF,
        certificat négatif…). Pour chaque fichier,{' '}
        <strong>choisissez son type</strong> : le dépôt dans la Data Room
        juridique ne se déclenche qu’<strong>une fois le type défini</strong>
        {' '}(et le fichier est renommé selon ce type) — {uploadedCount} /{' '}
        {docs.length} déposé{uploadedCount > 1 ? 's' : ''}.{' '}
        <strong>L’extraction automatique est facultative</strong> : elle se lance
        à la demande avec le bouton « Extraire » et ne bloque jamais la progression.
      </div>

      {!dossierId && (
        <div className="rounded-lg border border-danger/40 bg-danger-soft p-3 text-sm text-danger">
          Aucun dossier n’est rattaché à ce ticket. Revenez à la liste des tickets
          et rouvrez le parcours.
        </div>
      )}

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h3 className="text-base font-bold text-fg">Importer des documents</h3>
          <p className="text-[11px] text-fg-subtle">
            Le type se choisit <strong>par fichier</strong> ci-dessous.
          </p>
        </div>

        <label
          htmlFor="import-files"
          onDragOver={(ev) => {
            ev.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
          className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-8 text-center transition ${
            dragOver
              ? 'border-accent bg-accent-soft'
              : 'border-border bg-bg-overlay hover:border-border-hi'
          }`}
        >
          <Upload className="mx-auto mb-3 h-10 w-10 text-fg-muted" />
          <p className="text-sm font-semibold text-fg">
            Glissez-déposez vos fichiers PDF ou images
          </p>
          <p className="text-xs text-fg-subtle">
            ou cliquez pour les choisir (plusieurs fichiers possibles)
          </p>
          <input
            id="import-files"
            type="file"
            multiple
            accept="application/pdf,image/*"
            onChange={(ev) => handleFiles(ev.target.files)}
            className="hidden"
          />
        </label>

        {globalError && (
          <p className="mt-3 rounded-lg border border-danger/30 bg-danger-soft px-3 py-2 text-xs text-danger">
            {globalError}
          </p>
        )}
      </div>

      {docs.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-1 text-base font-bold text-fg">
            Documents déposés ({uploadedCount}/{docs.length})
          </h3>
          {untypedCount > 0 && (
            <p
              className="mb-3 text-[11px] text-warning"
              data-testid="untyped-warning"
            >
              {untypedCount} fichier{untypedCount > 1 ? 's' : ''} en attente de type : choisissez un
              type pour déclencher le dépôt.
            </p>
          )}
          <ul className="space-y-3">
            {docs.map((d) => (
              <li
                key={d.id}
                className="rounded-lg border border-border bg-bg-overlay p-3"
              >
                <div className="flex items-center gap-3">
                  <FileText className="h-5 w-5 text-fg-muted" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-fg" title={d.filename}>
                      {d.filename}
                    </p>
                    <p className="text-[11px] text-fg-subtle">
                      {(d.sizeBytes / 1024).toFixed(1)} Ko ·{' '}
                      {d.uiType ? (
                        DOC_TYPE_LABELS[d.uiType]
                      ) : (
                        <span className="text-warning">Type à définir</span>
                      )}
                    </p>
                    {d.error && (
                      <p className="mt-1 text-[11px] text-danger">
                        {d.error}
                      </p>
                    )}
                  </div>
                  <select
                    value={d.uiType}
                    onChange={(ev) =>
                      updateType(d.id, ev.target.value as UiDocType | '')
                    }
                    className="h-8 rounded border border-border bg-bg-raised px-2 text-xs"
                    // Le type se verrouille une fois le depot lance/termine
                    // (changer de type ne re-deposerait pas sous un nouveau nom).
                    disabled={d.busy || d.uploaded}
                    aria-label={`Type du document ${d.filename}`}
                    data-testid={`doc-type-${d.id}`}
                  >
                    <option value="">Choisir un type…</option>
                    {(Object.keys(DOC_TYPE_LABELS) as UiDocType[]).map((k) => (
                      <option key={k} value={k}>
                        {DOC_TYPE_LABELS[k]}
                      </option>
                    ))}
                  </select>
                  {d.busy ? (
                    <Loader className="h-4 w-4 animate-spin text-fg-subtle" />
                  ) : d.uploaded ? (
                    <CheckCircle2 className="h-4 w-4 text-success" />
                  ) : !d.uiType ? (
                    <span
                      className="text-[11px] text-warning"
                      title="Choisissez un type pour déposer le document"
                    >
                      Type requis
                    </span>
                  ) : (
                    <span className="text-[11px] text-warning" title="Document non déposé : réessayez en le retirant puis en le déposant de nouveau" aria-label="Document non déposé">
                      ⚠
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => remove(d.id)}
                    className="rounded p-1 text-danger hover:bg-danger/10"
                    aria-label="Retirer ce document"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>

                {/* Fix 2026-06-07 (BUG 5 finition) — Bouton "Extraire"
                    a la demande UNIQUEMENT, jamais auto-declenche. Affiche
                    UNIQUEMENT pour les types qui ont un schema OCR
                    correspondant (binding.aiSchema != null). Le doc est
                    deja depose en dataroom : l'extraction n'est qu'une
                    aide pour pre-remplir Step1. */}
                {d.uploaded && d.uiType && DOC_TYPE_BINDING[d.uiType].aiSchema && (
                  <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-border pt-3">
                    <button
                      type="button"
                      onClick={() => extractFor(d.id)}
                      disabled={d.extracting}
                      className={`flex items-center gap-1.5 rounded-lg px-3 h-8 text-xs font-medium transition ${
                        d.extracting
                          ? 'cursor-wait bg-border text-fg-subtle'
                          : 'bg-info-soft text-info hover:bg-bg-overlay'
                      }`}
                      title="Lance une extraction automatique pour pré-remplir la fiche de la société (facultatif)"
                    >
                      {d.extracting ? (
                        <>
                          <Loader className="h-3.5 w-3.5 animate-spin" /> Extraction…
                        </>
                      ) : d.fields && Object.keys(d.fields).length > 0 ? (
                        <>
                          <Sparkles className="h-3.5 w-3.5" /> Extraire de nouveau
                        </>
                      ) : (
                        <>
                          <Sparkles className="h-3.5 w-3.5" /> Extraire (facultatif)
                        </>
                      )}
                    </button>
                    <span className="text-[11px] text-fg-subtle">
                      L’extraction sert uniquement à pré-remplir la fiche de la société.
                      Vous pouvez valider et continuer sans extraire.
                    </span>
                    {d.extractError && (
                      <p className="w-full text-[11px] text-warning">
                        Extraction automatique : {d.extractError}
                      </p>
                    )}
                  </div>
                )}

                {d.fields && Object.keys(d.fields).length > 0 && (
                  <div className="mt-3">
                    <OcrSuggestionsPanel
                      filename={d.filename}
                      source={d.source}
                      extractionMode={d.extractionMode}
                      confidence={d.confidence}
                      warnings={d.warnings}
                      manualFallback={d.manualFallback}
                      suggestions={Object.entries(d.fields).map<OcrSuggestion>(
                        ([field, value]) => ({ field, label: field, value }),
                      )}
                      onApplyOne={() => {
                        // RG-IM05 : les suggestions sont appliquees cote
                        // Step1 (via stepData.step2.juridique.extractedFields).
                        // Ici on n'a pas le form Step1 sous la main.
                      }}
                      onApplyAllEmpty={() => {
                        /* idem -- application reelle au Step1. */
                      }}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <PublicitesSociete value={publicites} onChange={setPublicites} dossierId={dossierId}
        societe={denomination || 'Société'} />

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!canSubmit || saving || !dossierId}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            canSubmit && !saving && dossierId
              ? 'bg-accent text-accent-fg hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Validation en cours…' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

import { useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  FileText,
  Image as ImageIcon,
  Loader,
  Sparkles,
  Trash2,
  Upload,
} from 'lucide-react';
import { identityService } from '../../services/identityService';
import type {
  ExtractedIdentity,
  IdentityFields,
  IdentityType,
} from '../../types/identity';

/**
 * Sous-ensemble des champs qui ont un sens pour les formulaires "personne
 * physique" du workflow CREATION (Step5 Dirigeants + Step6 Associes).
 * <p>
 * 2026-06-16 : ajout de {@code date_validite} (anciennement omise) pour
 * pouvoir pre-remplir le champ "CIN valable jusqu'au" (pieceValidite) cote
 * dirigeant et associé personne physique.
 */
export type IdentityPhysiqueValues = Pick<
  IdentityFields,
  | 'nom'
  | 'prenom'
  | 'cin'
  | 'date_naissance'
  | 'lieu_naissance'
  | 'date_validite'
  | 'adresse'
  | 'sexe'
  | 'nationalite'
>;

/**
 * Sous-ensemble des champs qui ont un sens pour le formulaire Étape 1 du
 * workflow CREATION (Certificat Négatif / registre).
 */
export type IdentityCnValues = Pick<
  IdentityFields,
  | 'numero_cn'
  | 'denomination'
  | 'ice'
  | 'beneficiaire'
  | 'activite'
  | 'tribunal'
  | 'date_expiration'
  | 'date_delivrance'
>;

/**
 * Type unifié pour {@link IdentityExtractorProps.onApply}. Selon le mode, la
 * valeur ne contiendra qu'un sous-ensemble strict (PHYSIQUE en mode "cin",
 * CN en mode "cn") — l'écran appelant filtre ce qui le concerne via son
 * mapping.
 */
export type IdentityValues = Partial<IdentityPhysiqueValues & IdentityCnValues>;

export interface IdentityExtractorProps {
  /**
   * Mode d'opération du composant — choisi par l'écran appelant, JAMAIS par
   * l'utilisateur via UI :
   *  - `"cin"` (défaut) : 2 uploads (recto + verso) + toggle 2 boutons
   *    [Ancienne CIN | Nouvelle CIN]. AUCUNE option CN visible.
   *  - `"cn"` : 1 seul upload (carte/registre). AUCUN toggle, AUCUN verso.
   *    Le type envoyé au backend est toujours `CN`.
   * Ce découpage applique la règle métier : l'extraction CN est dédiée à
   * l'Étape 1 (dénomination), l'extraction CIN aux Étapes 5/6 (associé /
   * gérant personne physique).
   */
  mode?: 'cin' | 'cn';
  /**
   * Dossier auquel rattacher l'archive PDF (recto+verso fusionnés en mode
   * cin, fichier seul en mode cn). Si absent, l'archivage est désactivé
   * (extraction-only).
   */
  dossierId?: string | null;
  /**
   * Callback appelé quand l'employé clique "Appliquer au formulaire".
   * Reçoit les champs visibles + validés par l'utilisateur (tels que dans
   * les inputs au moment du clic, donc éditables avant l'application).
   * Le second argument expose les métadonnées de la dernière extraction
   * (archivedDocumentId notamment) pour que l'écran puisse refléter
   * l'archivage côté formulaire (ex. cinUploaded=true).
   */
  onApply: (
    values: IdentityValues,
    meta?: { archivedDocumentId: string | null; source: string; type: IdentityType },
  ) => void;
  /**
   * Test-only injection : permet de remplacer l'appel réseau dans les tests.
   * Par défaut, utilise {@link identityService.extract}.
   */
  extractFn?: typeof identityService.extract;
  /**
   * Fix A6 (2026-08-16) — valeurs DÉJÀ saisies dans le formulaire cible.
   *
   * Sans elles, l'extracteur ne peut pas savoir qu'« Appliquer au formulaire »
   * va détruire une saisie manuelle : il l'écrasait donc en silence. Fournies,
   * elles déclenchent une confirmation explicite listant chaque remplacement.
   */
  currentValues?: IdentityValues;
  /**
   * Test-only : valeur initiale du toggle CIN (ignorée en mode "cn").
   * En mode "cin", seules ANCIENNE et NOUVELLE sont acceptées ; CN est
   * silencieusement remappée sur NOUVELLE par défaut.
   */
  initialType?: IdentityType;
}

/** Labels affichés à côté de chaque champ extrait — CIN PHYSIQUE. */
const CIN_FIELD_LABELS: Record<keyof IdentityPhysiqueValues, string> = {
  nom: 'Nom',
  prenom: 'Prénom',
  cin: 'N° CIN',
  date_naissance: 'Date de naissance',
  lieu_naissance: 'Lieu de naissance',
  date_validite: "CIN valable jusqu'au",
  adresse: 'Adresse',
  sexe: 'Sexe (M / F)',
  nationalite: 'Nationalité',
};

/** Ordre d'affichage des champs PHYSIQUE (cohérent avec la lecture d'une CIN). */
const CIN_FIELD_ORDER: (keyof IdentityPhysiqueValues)[] = [
  'nom',
  'prenom',
  'cin',
  'date_naissance',
  'lieu_naissance',
  'date_validite',
  'sexe',
  'nationalite',
  'adresse',
];

/** Labels affichés à côté de chaque champ extrait — CN (carte / registre). */
const CN_FIELD_LABELS: Record<keyof IdentityCnValues, string> = {
  numero_cn: 'N° CN',
  denomination: 'Dénomination',
  ice: 'ICE',
  beneficiaire: 'Bénéficiaire',
  activite: 'Activité',
  tribunal: 'Tribunal',
  date_expiration: "Date d'expiration",
  date_delivrance: 'Date de délivrance',
};

const CN_FIELD_ORDER: (keyof IdentityCnValues)[] = [
  'numero_cn',
  'denomination',
  'ice',
  'beneficiaire',
  'activite',
  'tribunal',
  'date_expiration',
  'date_delivrance',
];

/** Traduit les codes warnings backend en messages lisibles. */
const WARNING_LABELS: Record<string, string> = {
  cin_format: 'Format CIN à vérifier (lettres + chiffres).',
  sexe_format: 'Sexe non reconnu : à vérifier.',
  mrz_not_found: 'MRZ non détectée au verso : extraction par image uniquement.',
  mrz_date_naissance: 'Date de naissance illisible côté MRZ.',
  mrz_date_validite: 'Date de validité illisible côté MRZ.',
  mrz_names: 'Nom/prénom MRZ illisibles : champs à vérifier.',
};

function humanizeWarning(code: string): string {
  if (WARNING_LABELS[code]) return WARNING_LABELS[code];
  if (code.startsWith('date_')) return `Date « ${code.slice(5)} » à vérifier.`;
  return code.replace(/_/g, ' ');
}

/** Badge "Source" affiché à droite des champs. */
function SourceBadge({ source }: { source: string }) {
  const label =
    source === 'merged' ? 'fusion (KIE + MRZ)' : source === 'kie' ? 'KIE' : source;
  const cls =
    source === 'merged'
      ? 'bg-success/10 text-success border-success/30'
      : 'bg-accent/10 text-accent border-accent/30';
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${cls}`}
    >
      <Sparkles className="h-3 w-3" />
      Source : {label}
    </span>
  );
}

/** Vignette d'aperçu d'un fichier (image preview + nom). */
function FilePreview({
  file,
  onClear,
  label,
}: {
  file: File;
  onClear: () => void;
  label: string;
}) {
  const url = useMemo(
    () => (file.type.startsWith('image/') ? URL.createObjectURL(file) : null),
    [file],
  );
  return (
    <div className="flex items-center gap-3 rounded-lg border border-success/40 bg-success/5 p-2">
      {url ? (
        <img
          src={url}
          alt={label}
          className="h-12 w-12 rounded object-cover"
        />
      ) : (
        <div className="flex h-12 w-12 items-center justify-center rounded bg-bg-overlay">
          <FileText className="h-6 w-6 text-fg-subtle" />
        </div>
      )}
      <div className="flex-1 text-xs">
        <p className="font-semibold text-fg">{label}</p>
        <p className="truncate text-fg-subtle">{file.name}</p>
      </div>
      <button
        type="button"
        onClick={onClear}
        aria-label={`Supprimer ${label}`}
        className="rounded p-1 text-fg-subtle hover:bg-bg-overlay hover:text-danger"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

/** Zone de dépôt fichier. */
function DropZone({
  label,
  required,
  inputId,
  onFile,
}: {
  label: string;
  required?: boolean;
  inputId: string;
  onFile: (file: File) => void;
}) {
  return (
    <label
      htmlFor={inputId}
      className="block cursor-pointer rounded-lg border-2 border-dashed border-border bg-bg-overlay p-4 text-center transition hover:border-accent hover:bg-accent/5"
    >
      <Upload className="mx-auto mb-1 h-6 w-6 text-fg-subtle" />
      <p className="text-xs font-semibold text-fg">
        {label}
        {required && <span className="ml-1 text-danger">*</span>}
      </p>
      {/* 2026-06-22 — Restreint aux images : le modèle d'extraction (Donut) a été
          entraîné uniquement sur des images ; les PDF ne sont pas pris en charge. */}
      <p className="text-[10px] text-fg-subtle">Image (JPG / PNG)</p>
      <input
        id={inputId}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(ev) => {
          const f = ev.target.files?.[0];
          if (f) onFile(f);
        }}
      />
    </label>
  );
}

/**
 * Fix A6 (2026-08-16) — nettoyage des LIBELLÉS DU DOCUMENT capturés par l'OCR.
 *
 * Le KIE renvoie parfois le texte imprimé de la carte au lieu de la seule valeur :
 * « BENNANI » est ressorti en « CARTE NATIONALE », « Salma » en « Salina CARTE
 * NATIONALE ». Ces mentions ne sont jamais une identité — on les retire avant de
 * proposer la valeur. Si la chaîne n'était QUE du libellé, on rend une chaîne vide
 * plutôt qu'un faux nom : un champ vide se voit, un faux nom passe inaperçu.
 */
const LIBELLES_PARASITES = [
  /ROYAUME\s+DU\s+MAROC/gi,
  /ROYAUME\s+DU\s+MAROG/gi,
  /CARTE\s+NATIONALE\s+D['’]?\s*IDENTIT[EÉ]?/gi,
  /CARTE\s+NATIONALE/gi,
  /NATIONAL\s+IDENTITY\s+CARD/gi,
  /CERTIFICAT\s+N[EÉ]GATIF/gi,
  /VALABLE\s+JUSQU['’]?\s*AU/gi,
];

export function nettoieValeurOcr(raw: string): string {
  let out = raw;
  for (const re of LIBELLES_PARASITES) out = out.replace(re, ' ');
  return out.replace(/\s{2,}/g, ' ').trim();
}

export function IdentityExtractor({
  mode = 'cin',
  dossierId,
  onApply,
  extractFn,
  initialType,
  currentValues,
}: IdentityExtractorProps) {
  // En mode "cn" le type est figé à 'CN' et ignore toute prop initialType.
  // En mode "cin" on remappe silencieusement 'CN' (illégal ici) sur 'NOUVELLE'.
  const defaultType: IdentityType =
    mode === 'cn'
      ? 'CN'
      : initialType && initialType !== 'CN'
        ? initialType
        : 'NOUVELLE';
  const [type, setType] = useState<IdentityType>(defaultType);
  const [recto, setRecto] = useState<File | null>(null);
  const [verso, setVerso] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [extracted, setExtracted] = useState<ExtractedIdentity | null>(null);
  // Valeurs éditables remontées depuis l'extraction. Union large : selon
  // le mode, seul un sous-ensemble est peuplé.
  const [editable, setEditable] = useState<IdentityValues>({});
  /** Fix A6 — confirmation demandée avant d'écraser une saisie manuelle. */
  const [confirmerEcrasement, setConfirmerEcrasement] = useState(false);

  // Ordre + labels d'affichage selon mode.
  const fieldOrder = mode === 'cn' ? CN_FIELD_ORDER : CIN_FIELD_ORDER;
  const fieldLabels: Record<string, string> =
    mode === 'cn' ? CN_FIELD_LABELS : CIN_FIELD_LABELS;

  // On fait porter la ref au callback pour permettre l'injection en tests.
  const extractRef = useRef(extractFn ?? identityService.extract);
  extractRef.current = extractFn ?? identityService.extract;

  const isCn = type === 'CN';
  const canExtract = !!recto && !loading;

  function resetExtraction() {
    setExtracted(null);
    setEditable({});
    setError(null);
  }

  function handleTypeChange(next: IdentityType) {
    if (next === type) return;
    setType(next);
    // Bascule en CN -> on retire le verso (non pertinent).
    if (next === 'CN') {
      setVerso(null);
    }
    resetExtraction();
  }

  async function handleExtract() {
    if (!recto) return;
    setLoading(true);
    setError(null);
    try {
      const out = await extractRef.current({
        recto,
        verso: isCn ? null : verso,
        type,
        dossierId: dossierId ?? null,
        archive: Boolean(dossierId),
      });
      setExtracted(out);
      // Pré-remplit les inputs éditables avec ce qu'on a extrait, en filtrant
      // uniquement les clés du mode actuel (PHYSIQUE en "cin", CN en "cn").
      const next: IdentityValues = {};
      for (const key of fieldOrder as string[]) {
        const v = (out.fields as Record<string, string | undefined>)[key];
        // Fix A6 — on nettoie les libellés du document avant de les proposer.
        if (typeof v === 'string') (next as Record<string, string>)[key] = nettoieValeurOcr(v);
      }
      setEditable(next);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 503) {
        setError(
          "Service d'extraction indisponible. Vous pouvez saisir les informations manuellement ci-dessous.",
        );
      } else if (status === 400) {
        setError(
          (err as { response?: { data?: { detail?: string } } })?.response?.data
            ?.detail ?? 'Requête invalide.',
        );
      } else if (status === 403) {
        setError("Action refusée : vous n'avez pas le droit d'archiver dans la Data Room.");
      } else {
        setError(
          "Extraction impossible. La saisie manuelle reste disponible ci-dessous.",
        );
      }
    } finally {
      setLoading(false);
    }
  }

  function handleEditableChange(field: string, value: string) {
    setEditable((prev) => ({ ...prev, [field]: value }));
  }

  /**
   * Fix A6 (2026-08-16) — champs déjà saisis que l'application ÉCRASERAIT.
   *
   * « Appliquer au formulaire » remplaçait la saisie manuelle sans le moindre
   * avertissement : en simulation, le prénom « Salma » est devenu « Salina CARTE
   * NATIONALE » et le nom « BENNANI » est devenu « CARTE NATIONALE ». Une identité
   * fausse s'est ainsi propagée jusqu'aux actes.
   *
   * On liste ici les écrasements réels (valeur courante non vide ET différente) pour
   * demander confirmation. Rien à confirmer quand les champs sont vides : le cas
   * nominal — remplir un formulaire vierge — reste en un clic.
   */
  const ecrasements: Array<{ key: string; avant: string; apres: string }> = [];
  for (const key of fieldOrder as string[]) {
    const avant = (
      (currentValues as Record<string, string | undefined> | undefined)?.[key] ?? ''
    ).trim();
    const apres = ((editable as Record<string, string | undefined>)[key] ?? '').trim();
    if (avant && apres && avant !== apres) ecrasements.push({ key, avant, apres });
  }

  function handleApply() {
    if (ecrasements.length > 0 && !confirmerEcrasement) {
      setConfirmerEcrasement(true);
      return;
    }
    applique();
  }

  function applique() {
    setConfirmerEcrasement(false);
    // 2026-07-05 — On remonte le TYPE de CIN choisi (toggle interne
    // Ancienne/Nouvelle) dans le meta : les ecrans appelants (ex. directeur
    // succursale) peuvent ainsi le persister sans dupliquer un selecteur.
    const meta = extracted
      ? { archivedDocumentId: extracted.archivedDocumentId, source: extracted.source, type }
      : undefined;
    onApply(editable, meta);
  }

  return (
    <section
      data-testid="identity-extractor"
      className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <h4 className="flex items-center gap-2 text-sm font-bold text-fg">
            <Sparkles className="h-4 w-4 text-accent" />
            {mode === 'cn'
              ? "Assistant d'extraction CN (carte / registre)"
              : "Assistant d'extraction CIN"}
          </h4>
          <p className="text-[11px] text-fg-subtle">
            {mode === 'cn'
              ? "Téléversez la carte / le registre, l'assistant pré-remplit les champs. La saisie manuelle reste possible."
              : "Téléversez recto + verso, l'assistant pré-remplit les champs. La saisie manuelle reste possible — chaque champ extrait est éditable."}
          </p>
        </div>
      </header>

      {/* Toggle type de pièce — visible UNIQUEMENT en mode CIN, avec
          exactement 2 boutons [Ancienne | Nouvelle]. AUCUNE option CN. */}
      {mode === 'cin' && (
        <div
          role="radiogroup"
          aria-label="Type de CIN"
          className="grid grid-cols-2 gap-2"
        >
          {(['NOUVELLE', 'ANCIENNE'] as IdentityType[]).map((t) => {
            const active = t === type;
            const label = t === 'NOUVELLE' ? 'Nouvelle CIN' : 'Ancienne CIN';
            return (
              <button
                type="button"
                key={t}
                role="radio"
                aria-checked={active}
                data-testid={`id-type-${t}`}
                onClick={() => handleTypeChange(t)}
                className={`rounded-lg border-2 px-3 py-2 text-xs font-semibold transition ${
                  active
                    ? 'border-accent bg-accent/5 text-fg'
                    : 'border-border bg-bg-overlay text-fg-subtle hover:border-accent/40'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>
      )}

      {/* Zones d'upload — 1 seule case en mode CN, recto+verso en mode CIN. */}
      <div className={`grid gap-2 ${isCn ? 'grid-cols-1' : 'grid-cols-2'}`}>
        {recto ? (
          <FilePreview
            file={recto}
            onClear={() => { setRecto(null); resetExtraction(); }}
            label={isCn ? 'Pièce' : 'Recto'}
          />
        ) : (
          <DropZone
            label={isCn ? 'Carte / registre' : 'Recto'}
            required
            inputId="id-recto-input"
            onFile={(f) => { setRecto(f); resetExtraction(); }}
          />
        )}
        {!isCn && (
          verso ? (
            <FilePreview file={verso} onClear={() => { setVerso(null); resetExtraction(); }} label="Verso" />
          ) : (
            <DropZone
              label="Verso (optionnel)"
              inputId="id-verso-input"
              onFile={(f) => { setVerso(f); resetExtraction(); }}
            />
          )
        )}
      </div>

      {/* Bouton Extraire */}
      <div className="flex items-center justify-between">
        <button
          type="button"
          data-testid="id-extract-btn"
          onClick={handleExtract}
          disabled={!canExtract}
          className={`inline-flex h-9 items-center gap-2 rounded-lg px-3 text-xs font-semibold transition ${
            canExtract
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {loading ? (
            <Loader className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <Sparkles className="h-3.5 w-3.5" />
          )}
          {loading ? 'Extraction en cours…' : 'Extraire'}
        </button>
        {dossierId && (
          <span className="inline-flex items-center gap-1 text-[10px] text-fg-subtle">
            <ImageIcon className="h-3 w-3" />
            Archivage Data Room actif
          </span>
        )}
      </div>

      {/* Erreur de bout en bout : on n'efface JAMAIS les inputs éditables si jamais remplis. */}
      {error && (
        <div
          role="alert"
          data-testid="id-error"
          className="flex items-start gap-2 rounded-lg border-l-4 border-danger bg-danger/10 p-3 text-xs text-fg"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger" />
          <p>{error}</p>
        </div>
      )}

      {/* Champs extraits éditables */}
      {extracted && (
        <div
          data-testid="id-extracted-panel"
          className="space-y-3 rounded-lg border border-border bg-bg-overlay p-3"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-semibold text-fg">
              Champs extraits — à vérifier puis appliquer
            </p>
            <SourceBadge source={extracted.source} />
          </div>

          {extracted.warnings.length > 0 && (
            <ul
              data-testid="id-warnings"
              className="space-y-1 rounded-md border border-warning/40 bg-warning/5 p-2 text-[11px] text-fg"
            >
              {extracted.warnings.map((w) => (
                <li key={w} className="flex items-start gap-1">
                  <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0 text-warning" />
                  <span>{humanizeWarning(w)}</span>
                </li>
              ))}
            </ul>
          )}

          <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
            {fieldOrder.map((field) => (
              <div key={field}>
                <label className="mb-1 block text-[11px] text-fg-subtle">
                  {fieldLabels[field]}
                </label>
                <input
                  type="text"
                  data-testid={`id-field-${field}`}
                  value={(editable as Record<string, string | undefined>)[field] ?? ''}
                  onChange={(e) => handleEditableChange(field, e.target.value)}
                  className="h-8 w-full rounded border border-border bg-bg-raised px-2 text-xs"
                />
              </div>
            ))}
          </div>

          {extracted.archivedDocumentId && (
            <div
              data-testid="id-archived"
              className="flex items-center gap-2 rounded-md border border-success/30 bg-success/5 p-2 text-[11px] text-success"
            >
              <CheckCircle className="h-3.5 w-3.5" />
              Pièce archivée dans la Data Room (ref&nbsp;
              <code className="font-mono">{extracted.archivedDocumentId.slice(0, 8)}</code>).
            </div>
          )}

          {/* Fix A6 — confirmation explicite AVANT d'écraser une saisie manuelle. */}
          {confirmerEcrasement && ecrasements.length > 0 && (
            <div
              role="alert"
              data-testid="id-overwrite-confirm"
              className="space-y-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900"
            >
              <p className="font-medium">
                {ecrasements.length === 1
                  ? 'Une valeur déjà saisie va être remplacée :'
                  : `${ecrasements.length} valeurs déjà saisies vont être remplacées :`}
              </p>
              <ul className="list-disc space-y-0.5 pl-4">
                {ecrasements.map((e) => (
                  <li key={e.key}>
                    <strong>{fieldLabels[e.key] ?? e.key}</strong> : « {e.avant} » →
                    {' '}« {e.apres} »
                  </li>
                ))}
              </ul>
              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  data-testid="id-overwrite-cancel"
                  onClick={() => setConfirmerEcrasement(false)}
                  className="inline-flex h-8 items-center rounded-lg border border-border bg-bg-raised px-3 font-semibold text-fg"
                >
                  Conserver ma saisie
                </button>
                <button
                  type="button"
                  data-testid="id-overwrite-accept"
                  onClick={applique}
                  className="inline-flex h-8 items-center rounded-lg bg-amber-600 px-3 font-semibold text-white"
                >
                  Remplacer
                </button>
              </div>
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="button"
              data-testid="id-apply-btn"
              onClick={handleApply}
              className="inline-flex h-8 items-center gap-2 rounded-lg bg-accent px-3 text-xs font-semibold text-bg-raised hover:bg-accent-hover"
            >
              <CheckCircle className="h-3.5 w-3.5" />
              Appliquer au formulaire
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

export default IdentityExtractor;

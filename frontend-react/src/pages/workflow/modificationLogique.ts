/**
 * Logique pure du parcours MODIFICATION (sans React), testable unitairement.
 *
 * Lot L6 (E7) : extraite de ModificationWorkflowPage.tsx, qui ne doit exporter que des
 * composants (regle react-refresh/only-export-components). Contenu inchange, hormis la
 * langue des libelles et messages affiches.
 */
// Type d'assemblee simplifie (spec 2026-08-11) : l'employe choisit Ordinaire ou
// Extraordinaire. Le decisionType envoye au backend est derive de (forme, nature) :
// SARL ordinaire→AGO, extraordinaire→AGE ; SARL_AU → decision de l'associe unique (AU).
export type AssembleeNature = 'ordinaire' | 'extraordinaire';
export const NATURE_OPTIONS: { value: AssembleeNature; label: string }[] = [
  { value: 'ordinaire', label: 'Ordinaire' },
  { value: 'extraordinaire', label: 'Extraordinaire' },
];
export function decisionTypeFor(isAU: boolean, nature: AssembleeNature): string {
  if (isAU) return 'AU';
  return nature === 'ordinaire' ? 'AGO' : 'AGE';
}

/**
 * Regle des 16 jours : le noyau (constante + helpers purs) vit desormais dans
 * `components/workflow/convocationDelai` — PARTAGE avec le workflow DISSOLUTION
 * (2026-08-12). Re-exporte ici pour ne rien casser des imports existants.
 */
export { CONVOCATION_DELAI_JOURS, addDaysIso, convocationDelaiError } from '../../components/workflow/convocationDelai';

// RG transverse 2026-06-05 : liste PLATE (pas de groupes/titres) — tri alphabetique strict
// pour favoriser la recherche/scan rapide. Les categories ne sont plus exposees a l'UI ;
// elles restent disponibles via l'indication "article" sous chaque item.
interface ModificationItem {
  id: string;
  label: string;
  article?: string;
}

const MODIFICATIONS: ModificationItem[] = [
  { id: 'AUGMENTATION_CAPITAL', label: 'Augmentation de capital (numéraire)', article: 'Articles 6-7' },
  { id: 'AUGMENTATION_CAPITAL_NATURE', label: 'Augmentation de capital (apport en nature)', article: 'Articles 6-7' },
  { id: 'AUGMENTATION_CAPITAL_RESERVES', label: 'Augmentation par incorporation de réserves', article: 'Articles 6-7' },
  { id: 'CESSION_PARTIELLE', label: 'Cession partielle de parts', article: 'Articles 8-9' },
  { id: 'CESSION_TOTALE', label: 'Cession totale (changement d’associé unique)', article: 'Article 9' },
  { id: 'CHANGEMENT_DENOMINATION', label: 'Changement de dénomination', article: 'Article 2' },
  { id: 'CHANGEMENT_OBJET', label: 'Changement de l’objet social', article: 'Article 3' },
  { id: 'CLAUSE_AGREMENT', label: 'Clause d’agrément', article: 'Article 10' },
  { id: 'CLAUSE_PREEMPTION', label: 'Droit de préemption / inaliénabilité', article: 'Statuts' },
  { id: 'CONTINUATION_PERTES', label: 'Continuation malgré les pertes', article: 'Loi 5-96 art. 86' },
  { id: 'CREATION_SUCCURSALE', label: 'Création / transfert / suppression de succursale', article: 'Article 17' },
  { id: 'DESIGNATION_CAC', label: 'Désignation d’un commissaire aux comptes', article: 'Article 15' },
  { id: 'DESIGNATION_GERANT', label: 'Nomination d’un gérant', article: 'Article 12' },
  { id: 'FUSION_SCISSION', label: 'Fusion / scission / apport partiel', article: 'Projet annexe' },
  { id: 'MODALITES_DECISIONS', label: 'Modalités des décisions', article: 'Statuts' },
  { id: 'MODIF_NOMBRE_GERANTS', label: 'Modification du nombre ou de la durée du mandat des gérants', article: 'Article 12' },
  { id: 'MODIF_POUVOIRS_GERANT', label: 'Modification des pouvoirs ou de la rémunération', article: 'Article 12' },
  { id: 'MODIF_VALEUR_NOMINALE', label: 'Modification de la valeur nominale des parts', article: 'Article 7' },
  { id: 'NANTISSEMENT', label: 'Nantissement de parts', article: 'Acte annexe' },
  { id: 'PACTE_ASSOCIES', label: 'Pacte d’associés', article: 'Annexe' },
  { id: 'POUVOIRS_FORMALITES', label: 'Pouvoirs pour formalités', article: 'Décision standard' },
  { id: 'PROROGATION_DUREE', label: 'Prorogation de la durée', article: 'Article 5' },
  { id: 'REDUCTION_CAPITAL', label: 'Réduction de capital', article: 'Articles 6-7' },
  { id: 'REVOCATION_GERANT', label: 'Révocation d’un gérant', article: 'Article 12' },
  { id: 'TRANSFERT_SIEGE', label: 'Transfert du siège social', article: 'Article 4' },
  { id: 'TRANSFORMATION', label: 'Transformation (SARL ↔ SA, etc.)', article: 'Statuts complets' },
  { id: 'TRANSMISSION_PARTS', label: 'Transmission par succession/donation', article: 'Articles 8-9' },
].sort((a, b) => a.label.localeCompare(b.label, 'fr', { sensitivity: 'base' }));

export const ALL_MODS = MODIFICATIONS;

export const DECISION_TYPES = [
  { value: 'AU', label: 'Décision de l’associé unique (SARL à associé unique)' },
  { value: 'AGE', label: 'Assemblée générale extraordinaire (SARL)' },
  { value: 'AGO', label: 'Assemblée générale ordinaire (AGO)' },
  { value: 'CA', label: 'Conseil d’administration' },
  { value: 'PV', label: 'Procès-verbal de réunion' },
];

// ============================================================================
// VALIDATION TEMPS REEL (RG transverse 2026-06-05) — par modification choisie
// ============================================================================
// Chaque entree definit les champs requis + regle de validation + message
// utilisateur precis (typo/oubli). Renvoie null si OK, sinon le message FR.
type FieldValidator = (value: string, values: Record<string, string>) => string | null;

export interface FieldSpec {
  name: string;
  label: string;
  validate: FieldValidator;
}

function required(label: string): FieldValidator {
  return (v) => (!v || !v.trim()) ? `${label} est obligatoire.` : null;
}
function minLen(label: string, n: number): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    if (v.trim().length < n) return `${label} doit comporter au moins ${n} caractères (actuellement : ${v.trim().length}).`;
    return null;
  };
}
function positiveNumber(label: string): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    const n = Number(v);
    if (!Number.isFinite(n)) return `${label} doit être un nombre valide.`;
    if (n <= 0) return `${label} doit être strictement positif.`;
    return null;
  };
}
function dateField(label: string, opts?: { notFuture?: boolean; notPast?: boolean }): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    const ts = Date.parse(v);
    if (Number.isNaN(ts)) return `${label} doit être une date valide (AAAA-MM-JJ).`;
    if (opts?.notFuture && ts > Date.now() + 86400000)
      return `${label} ne peut pas être dans le futur.`;
    if (opts?.notPast && ts < Date.now() - 86400000)
      return `${label} ne peut pas être dans le passé.`;
    return null;
  };
}
function cin(label: string): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    // CIN marocaine : 1-2 lettres + 5-7 chiffres (souple : autorise espaces internes).
    const clean = v.replace(/\s+/g, '').toUpperCase();
    if (!/^[A-Z]{1,2}\d{4,8}$/.test(clean))
      return `${label} invalide : format attendu 1 ou 2 lettres puis 4 à 8 chiffres (ex. : AB123456).`;
    return null;
  };
}

const FIELD_SPECS: Record<string, FieldSpec[]> = {
  AUGMENTATION_CAPITAL: [
    { name: 'nouveauCapital', label: 'Nouveau capital (MAD)', validate: positiveNumber('Le nouveau capital') },
    { name: 'nouvellesParts', label: 'Nombre de nouvelles parts', validate: positiveNumber('Le nombre de nouvelles parts') },
    { name: 'dateVersement', label: 'Date de versement des fonds', validate: dateField('La date de versement', { notFuture: false }) },
  ],
  AUGMENTATION_CAPITAL_NATURE: [
    { name: 'valeurApport', label: 'Valeur de l’apport (MAD)', validate: positiveNumber('La valeur de l’apport') },
    { name: 'partsAttribuees', label: 'Nombre de parts attribuées', validate: positiveNumber('Le nombre de parts attribuées') },
    { name: 'descriptionBien', label: 'Description du bien apporté', validate: minLen('La description du bien', 10) },
    { name: 'cacNom', label: 'Commissaire aux apports — Nom', validate: required('Le nom du commissaire aux apports') },
  ],
  REDUCTION_CAPITAL: [
    { name: 'montantReduction', label: 'Montant de la réduction (MAD)', validate: positiveNumber('Le montant de la réduction') },
    { name: 'motif', label: 'Motif', validate: minLen('Le motif', 10) },
  ],
  TRANSFERT_SIEGE: [
    { name: 'nouvelleAdresse', label: 'Nouvelle adresse', validate: minLen('La nouvelle adresse', 5) },
    { name: 'nouvelleVille', label: 'Nouvelle ville', validate: required('La nouvelle ville') },
    { name: 'dateEffet', label: 'Date d’effet', validate: dateField('La date d’effet') },
  ],
  CHANGEMENT_DENOMINATION: [
    { name: 'nouvelleDenomination', label: 'Nouvelle dénomination', validate: minLen('La nouvelle dénomination', 2) },
  ],
  CHANGEMENT_OBJET: [
    { name: 'nouvelObjet', label: 'Nouvel objet social', validate: minLen('Le nouvel objet social', 20) },
  ],
  PROROGATION_DUREE: [
    { name: 'annees', label: 'Nombre d’années de prorogation', validate: positiveNumber('Le nombre d’années') },
    { name: 'dateEffet', label: 'Date d’effet', validate: dateField('La date d’effet') },
  ],
  DESIGNATION_GERANT: [
    { name: 'nom', label: 'Nom', validate: required('Le nom') },
    { name: 'prenom', label: 'Prénom', validate: required('Le prénom') },
    { name: 'cin', label: 'CIN', validate: cin('CIN') },
    { name: 'dateEffet', label: 'Date d’effet', validate: dateField('La date d’effet') },
    { name: 'nationalite', label: 'Nationalité', validate: required('La nationalité') },
  ],
  REVOCATION_GERANT: [
    { name: 'identite', label: 'Identité du gérant révoqué', validate: required('L’identité du gérant révoqué') },
    { name: 'dateEffet', label: 'Date d’effet', validate: dateField('La date d’effet') },
    { name: 'motif', label: 'Motif', validate: minLen('Le motif', 10) },
  ],
  CESSION_PARTIELLE: [
    { name: 'dateActe', label: 'Date de l’acte', validate: dateField('La date de l’acte') },
    { name: 'cessionnaire', label: 'Cessionnaire', validate: required('Le cessionnaire') },
    { name: 'nombreParts', label: 'Nombre de parts cédées', validate: positiveNumber('Le nombre de parts cédées') },
    { name: 'prix', label: 'Prix de cession (MAD)', validate: positiveNumber('Le prix de cession') },
  ],
};

// Fallback generique : un champ "details" obligatoire >= 10 chars.
const FALLBACK_SPEC: FieldSpec[] = [
  { name: 'details', label: 'Détails', validate: minLen('Le détail', 10) },
];

// Types pour lesquels la generation s'appuie sur une "annexe a fournir" :
// le PV et les statuts sont quand meme generes, mais les champs structures ne
// sont pas exiges (l'avocat fournira l'acte annexe). Aligne sur le fallback du
// ResolutionTextGenerator (cote ai-service).
export const FALLBACK_ANNEXE_TYPES = new Set<string>([
  'AUGMENTATION_CAPITAL_NATURE',
  'CESSION_PARTIELLE',
  'CESSION_TOTALE',
  'TRANSMISSION_PARTS',
  'NANTISSEMENT',
  'TRANSFORMATION',
  'FUSION_SCISSION',
  'DESIGNATION_CAC',
  'PACTE_ASSOCIES',
]);

export function getFieldSpec(typeId: string): FieldSpec[] {
  return FIELD_SPECS[typeId] ?? FALLBACK_SPEC;
}

export function validateModification(typeId: string, values: Record<string, string>): Record<string, string> {
  const errs: Record<string, string> = {};
  for (const spec of getFieldSpec(typeId)) {
    const msg = spec.validate(values[spec.name] ?? '', values);
    if (msg) errs[spec.name] = msg;
  }
  return errs;
}

/**
 * PROMPT F (2026-06-23) — validation pure de l'étape 1 (sans React),
 * testable unitairement. Retourne un message d'erreur si bloquée, null si OK.
 */
export function validateStep1Inputs(input: {
  dossierId: string;
  selectedTypes: string[];
  datePV: string;
  decisionType: string;
}): string | null {
  if (!input.dossierId) return 'Sélectionnez la société à modifier.';
  if (!input.selectedTypes || input.selectedTypes.length === 0) {
    return 'Sélectionnez au moins une modification.';
  }
  if (!input.datePV || !input.decisionType) {
    return 'Renseignez la date du procès-verbal et le type de décision.';
  }
  return null;
}

/** Preflight pur (sans React) — utilise par l'etape 3 et testable unitairement. */
export interface PreflightEntry {
  typeId: string;
  missing: { name: string; label: string }[];
}
export interface PreflightResult {
  blocking: PreflightEntry[];
  warnings: PreflightEntry[];
  canGenerate: boolean;
}
export function computePreflight(
  selected: string[],
  values: Record<string, Record<string, string>>,
): PreflightResult {
  const blocking: PreflightEntry[] = [];
  const warnings: PreflightEntry[] = [];
  for (const typeId of selected) {
    const errs = validateModification(typeId, values[typeId] ?? {});
    if (Object.keys(errs).length === 0) continue;
    const spec = getFieldSpec(typeId);
    const missing = spec
      .filter((f) => errs[f.name])
      .map((f) => ({ name: f.name, label: f.label }));
    if (missing.length === 0) continue;
    (FALLBACK_ANNEXE_TYPES.has(typeId) ? warnings : blocking).push({ typeId, missing });
  }
  return { blocking, warnings, canGenerate: blocking.length === 0 };
}

// ============================================================================
// PREFLIGHT STATUT REFONDU (2026-06-24) — champs structurés manquants
// ============================================================================
// Le statut COMPLET refondu (STATUTS_REFONDUS_*) se génère depuis la fiche
// structurée (JAMAIS le scan). Pour une société importée scan-only, certains
// champs structurés sont absents : on les détecte ici et on FORCE leur saisie
// (avec le scan affiché en aide) avant d'autoriser la génération.
export interface RefonteField {
  key: string;
  label: string;
  type: 'text' | 'number' | 'textarea';
}

// Champs structurés indispensables à un statut refondu cohérent (en-tête + gérance).
const REFONTE_REQUIRED_FIELDS: RefonteField[] = [
  { key: 'denomination', label: 'Dénomination sociale', type: 'text' },
  { key: 'objetSocial', label: 'Objet social', type: 'textarea' },
  { key: 'adresseSiege', label: 'Adresse du siège social', type: 'text' },
  { key: 'capitalSocial', label: 'Capital social (MAD)', type: 'number' },
  { key: 'valeurNominale', label: 'Valeur nominale d’une part (MAD)', type: 'number' },
  { key: 'dureeAnnees', label: 'Durée de la société (années)', type: 'number' },
];

const REFONTE_GERANT_FIELD: RefonteField = {
  key: 'gerantNom',
  label: 'Nom du gérant en exercice',
  type: 'text',
};

export function isBlankValue(v: unknown): boolean {
  return v === null || v === undefined || String(v).trim() === '';
}

/**
 * Détecte les champs structurés MANQUANTS pour générer le statut refondu, à
 * partir de la fiche EFFECTIVE (fiche structurée persistée ⊕ saisies preflight).
 * Pure / testable. La gérance est requise via au moins un gérant connu
 * (fiche.gerants/dirigeants non vides) ou la saisie `gerantNom`.
 */
export function computeRefonteMissing(fiche: Record<string, unknown> | undefined): RefonteField[] {
  const f = fiche ?? {};
  const missing = REFONTE_REQUIRED_FIELDS.filter((fld) => isBlankValue(f[fld.key]));
  const gerants = f.gerants ?? f.dirigeants;
  const hasGerant =
    (Array.isArray(gerants) && gerants.length > 0) || !isBlankValue(f.gerantNom);
  if (!hasGerant) missing.push(REFONTE_GERANT_FIELD);
  return missing;
}

// Nettoie une portion de nom de fichier (retire les caractères interdits par les OS).
export function sanitizeFilenamePart(raw: string): string {
  return (raw || 'Societe')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120) || 'Societe';
}

// ============================================================================
// SYNTHESE etape 4 (2026-06-24) — helpers PURS (sans React, testables).
// Toutes les donnees du recap sont deja disponibles cote front (selection +
// valeurs saisies + faits dossier) ; on les transforme ici en lignes lisibles.
// ============================================================================

/** Categorie d'un document a partir de son code template (robuste sans le manifest). */
export function classifyDocKind(code: string): 'PV' | 'STATUTS' | 'JAL' | 'AUTRE' {
  if (code.startsWith('PV_')) return 'PV';
  if (code.startsWith('STATUTS_')) return 'STATUTS';
  if (code.startsWith('ANNONCE_LEGALE') || code.includes('JAL')) return 'JAL';
  return 'AUTRE';
}

/** Formate un montant MAD lisible (separateur de milliers fr-FR). */
export function formatMad(v: unknown): string {
  if (v === null || v === undefined || `${v}`.trim() === '') return '—';
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  return `${n.toLocaleString('fr-FR')} MAD`;
}

export interface ChangeRow {
  /** Libelle du type de modification (regroupement visuel). */
  typeLabel: string;
  /** Champ impacte (ex : « Capital social »). */
  champ: string;
  /** Valeur avant (ou « — » si non connue / non applicable). */
  avant: string;
  /** Valeur apres (issue des valeurs saisies a l'etape 2). */
  apres: string;
}

/**
 * Construit le tableau AVANT → APRES a partir de la selection (etape 1) et des
 * valeurs saisies (etape 2), enrichi des faits dossier (denomination, capital).
 * Les types non structures (annexe a fournir) retombent sur le champ « details ».
 */
export function buildChangeRows(
  selected: string[],
  values: Record<string, Record<string, string>>,
  dossier?: { denomination?: string; capitalSocial?: number },
): ChangeRow[] {
  const rows: ChangeRow[] = [];
  const labelOf = (id: string) => ALL_MODS.find((m) => m.id === id)?.label ?? id;
  for (const id of selected) {
    const v = values[id] ?? {};
    const L = labelOf(id);
    switch (id) {
      case 'CHANGEMENT_DENOMINATION':
        rows.push({ typeLabel: L, champ: 'Dénomination sociale', avant: dossier?.denomination || '—', apres: v.nouvelleDenomination || '—' });
        if (v.sigle) rows.push({ typeLabel: L, champ: 'Sigle', avant: '—', apres: v.sigle });
        break;
      case 'CHANGEMENT_OBJET':
        rows.push({ typeLabel: L, champ: 'Objet social', avant: '—', apres: v.nouvelObjet || '—' });
        break;
      case 'TRANSFERT_SIEGE':
        rows.push({
          typeLabel: L,
          champ: 'Siège social',
          avant: '—',
          apres: [v.nouvelleAdresse, v.nouvelleVille].filter(Boolean).join(', ') || '—',
        });
        break;
      case 'PROROGATION_DUREE':
        rows.push({
          typeLabel: L,
          champ: 'Durée de la société',
          avant: '—',
          apres: v.annees ? `+ ${v.annees} an(s)${v.dateEffet ? ` (effet ${v.dateEffet})` : ''}` : '—',
        });
        break;
      case 'AUGMENTATION_CAPITAL':
        rows.push({ typeLabel: L, champ: 'Capital social', avant: formatMad(dossier?.capitalSocial), apres: formatMad(v.nouveauCapital) });
        rows.push({ typeLabel: L, champ: 'Nouvelles parts émises', avant: '—', apres: v.nouvellesParts || '—' });
        break;
      case 'AUGMENTATION_CAPITAL_NATURE':
        rows.push({ typeLabel: L, champ: 'Apport en nature', avant: '—', apres: formatMad(v.valeurApport) });
        rows.push({ typeLabel: L, champ: 'Bien apporté', avant: '—', apres: v.descriptionBien || '—' });
        if (v.cacNom) rows.push({ typeLabel: L, champ: 'Commissaire aux apports', avant: '—', apres: v.cacNom });
        break;
      case 'REDUCTION_CAPITAL': {
        const ancien = dossier?.capitalSocial;
        const reduc = Number(v.montantReduction);
        const apres =
          ancien != null && Number.isFinite(reduc) ? formatMad(ancien - reduc) : '—';
        rows.push({ typeLabel: L, champ: 'Capital social', avant: formatMad(ancien), apres });
        break;
      }
      case 'DESIGNATION_GERANT':
        rows.push({ typeLabel: L, champ: 'Gérant nommé', avant: '—', apres: [v.prenom, v.nom].filter(Boolean).join(' ') || '—' });
        if (v.remuneration) rows.push({ typeLabel: L, champ: 'Rémunération du gérant', avant: '—', apres: formatMad(v.remuneration) });
        break;
      case 'REVOCATION_GERANT':
        rows.push({ typeLabel: L, champ: 'Gérant révoqué', avant: v.identite || '—', apres: 'Révoqué' });
        break;
      case 'CESSION_PARTIELLE':
        rows.push({
          typeLabel: L,
          champ: 'Cession de parts',
          avant: '—',
          apres: `${v.nombreParts || '?'} part(s) → ${v.cessionnaire || '?'}${v.prix ? ` (${formatMad(v.prix)})` : ''}`,
        });
        break;
      default: {
        // Types non structures (annexe a fournir / declaratif) : champ « details ».
        rows.push({ typeLabel: L, champ: 'Modification', avant: '—', apres: v.details || '— (annexe à fournir)' });
      }
    }
  }
  return rows;
}

/**
 * Lot L6b (E15) : lieu de signature saisi dans la séance du procès-verbal (instantané du
 * formulaire du PV), transmis aux documents de la grille (statuts refondus, annonce) qui
 * impriment « Fait à …, le … ». Absent : null (la génération le nomme, rien n'est deviné).
 */
export function lieuSignatureDuPv(pvSnapshot: Record<string, unknown> | null | undefined): string | null {
  const v = pvSnapshot?.lieuSignature;
  return typeof v === 'string' && v.trim() ? v.trim() : null;
}


import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  Building2,
  CheckCircle,
  ChevronRight,
  Download,
  ExternalLink,
  Eye,
  FileText,
  Gavel,
  ListChecks,
  Loader,
  Megaphone,
  Pencil,
  RefreshCcw,
  Search,
  Sparkles,
  X,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { workflowService, type DossierParties } from '../../services/workflow.service';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { dataroomService } from '../../services/dataroom.service';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import { ModificationOperationForm } from '../../components/workflow/ModificationOperationForm';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import type { DocumentType, DossierBrief } from '../../types/dataroom';
import {
  generateDocument,
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import {
  GROUP_LABELS,
  decisionsForForme,
  findDecision,
  type ModForme,
  type OfficialDecision,
} from './officialModificationDecisions';
import {
  RES_SPECS,
  ResolutionFieldsEditor,
  flattenResolution,
  oldValuesFor,
  personNames,
  seedResolutions,
  type PersonLists,
  type ResolutionState,
} from '../../components/workflow/modificationResolutions';
import { IdentityExtractor } from '../../components/identity/IdentityExtractor';
import { GeneratedDocPreview } from '../../components/document/GeneratedDocPreview';
import { DocumentEditModal } from '../../components/document/DocumentEditModal';
import {
  buildDocFilename,
  docTypeForTemplateCode,
  formeLabel,
} from '../../components/workflow/workflowFilename';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';

// Phase 1 (2026-08-10) — voie directeur : déclencheurs basés sur le `resolutionType`
// (snake_case) du catalogue officiel. Miroir de ModificationWorkflow.*_TRIGGERS_RT (backend).
const JAL_TRIGGERS_RT = new Set<string>([
  'transfert_siege', 'transformation',
  'augmentation_capital_numeraire', 'augmentation_capital_nature',
  'augmentation_capital_incorporation', 'reduction_capital',
  'agrement_cession', 'agrement_transmission', 'cession_parts_pluripersonnelle',
  'operation_restructuration', 'modification_denomination', 'modification_objet',
  'prorogation_duree',
]);
const CAC_TRIGGERS_RT = new Set<string>(['augmentation_capital_nature']);
const FORME_CHANGE_TRIGGERS_RT = new Set<string>(['cession_parts_pluripersonnelle']);

// Phase 2 (2026-08-11) — resolutionTypes NON opposables aux tiers : aucune ligne
// d'annonce légale. Miroir exact de ModificationAnnonceVarsBuilder.NON_PUBLIABLES.
const NON_PUBLIABLES_RT = new Set<string>([
  'modification_exercice', 'commissaire_comptes', 'nantissement_parts',
  'approbation_comptes', 'affectation_resultat', 'distribution_dividendes',
  'distribution_reserves', 'acompte_dividendes', 'conventions_reglementees',
  'renouvellement_gerant', 'remuneration_gerant', 'ratification_actes_formation',
  'autorisation_gerance', 'designation_commissaire_transformation',
  'pouvoirs_formalites',
]);

// Phase 2 — champ à pré-remplir avec le nom du nouvel associé issu de l'OCR CIN.
const NEW_ASSOCIE_TARGET_FIELD: Record<string, string> = {
  agrement_cession: 'cessionnaireNom',
  agrement_transmission: 'cessionnaireNom',
  cession_parts_pluripersonnelle: 'cessionnaireNom',
  augmentation_capital_nature: 'apporteurNom',
};

const STEPS: RoadmapStep[] = [
  { number: 1, label: 'Selection' },
  { number: 2, label: 'Saisie' },
  { number: 3, label: 'Generation' },
  { number: 4, label: 'Pieces jointes' },
  { number: 5, label: 'Synthese' },
];

// Type d'assemblee simplifie (spec 2026-08-11) : l'employe choisit Ordinaire ou
// Extraordinaire. Le decisionType envoye au backend est derive de (forme, nature) :
// SARL ordinaire→AGO, extraordinaire→AGE ; SARL_AU → decision de l'associe unique (AU).
type AssembleeNature = 'ordinaire' | 'extraordinaire';
const NATURE_OPTIONS: { value: AssembleeNature; label: string }[] = [
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
export { CONVOCATION_DELAI_JOURS, addDaysIso, convocationDelaiError };

// RG transverse 2026-06-05 : liste PLATE (pas de groupes/titres) — tri alphabetique strict
// pour favoriser la recherche/scan rapide. Les categories ne sont plus exposees a l'UI ;
// elles restent disponibles via l'indication "article" sous chaque item.
interface ModificationItem {
  id: string;
  label: string;
  article?: string;
}

const MODIFICATIONS: ModificationItem[] = [
  { id: 'AUGMENTATION_CAPITAL', label: 'Augmentation de capital (numeraire)', article: 'Articles 6-7' },
  { id: 'AUGMENTATION_CAPITAL_NATURE', label: 'Augmentation de capital (apport nature)', article: 'Articles 6-7' },
  { id: 'AUGMENTATION_CAPITAL_RESERVES', label: 'Augmentation par incorporation de reserves', article: 'Articles 6-7' },
  { id: 'CESSION_PARTIELLE', label: 'Cession partielle de parts', article: 'Articles 8-9' },
  { id: 'CESSION_TOTALE', label: "Cession totale (changement associe unique)", article: 'Article 9' },
  { id: 'CHANGEMENT_DENOMINATION', label: 'Changement de denomination', article: 'Article 2' },
  { id: 'CHANGEMENT_OBJET', label: "Changement de l'objet social", article: 'Article 3' },
  { id: 'CLAUSE_AGREMENT', label: "Clause d'agrement", article: 'Article 10' },
  { id: 'CLAUSE_PREEMPTION', label: 'Droit de preemption / inalienabilite', article: 'Statuts' },
  { id: 'CONTINUATION_PERTES', label: 'Continuation malgre pertes', article: 'Loi 5-96 art. 86' },
  { id: 'CREATION_SUCCURSALE', label: 'Creation / transfert / suppression de succursale', article: 'Article 17' },
  { id: 'DESIGNATION_CAC', label: "Designation d'un CAC", article: 'Article 15' },
  { id: 'DESIGNATION_GERANT', label: "Nomination d'un gerant", article: 'Article 12' },
  { id: 'FUSION_SCISSION', label: 'Fusion / scission / apport partiel', article: 'Projet annexe' },
  { id: 'MODALITES_DECISIONS', label: 'Modalites de decisions', article: 'Statuts' },
  { id: 'MODIF_NOMBRE_GERANTS', label: 'Modification nombre/duree gerants', article: 'Article 12' },
  { id: 'MODIF_POUVOIRS_GERANT', label: 'Modification pouvoirs/remuneration', article: 'Article 12' },
  { id: 'MODIF_VALEUR_NOMINALE', label: 'Modification valeur nominale parts', article: 'Article 7' },
  { id: 'NANTISSEMENT', label: 'Nantissement de parts', article: 'Acte annexe' },
  { id: 'PACTE_ASSOCIES', label: "Pacte d'associes", article: 'Annexe' },
  { id: 'POUVOIRS_FORMALITES', label: 'Pouvoirs pour formalites', article: 'Decision standard' },
  { id: 'PROROGATION_DUREE', label: 'Prorogation de la duree', article: 'Article 5' },
  { id: 'REDUCTION_CAPITAL', label: 'Reduction de capital', article: 'Articles 6-7' },
  { id: 'REVOCATION_GERANT', label: "Revocation d'un gerant", article: 'Article 12' },
  { id: 'TRANSFERT_SIEGE', label: 'Transfert du siege social', article: 'Article 4' },
  { id: 'TRANSFORMATION', label: 'Transformation (SARL <-> SA, etc.)', article: 'Statuts complets' },
  { id: 'TRANSMISSION_PARTS', label: 'Transmission par succession/donation', article: 'Articles 8-9' },
].sort((a, b) => a.label.localeCompare(b.label, 'fr', { sensitivity: 'base' }));

const ALL_MODS = MODIFICATIONS;

const DECISION_TYPES = [
  { value: 'AU', label: "Decision de l'associe unique (SARL AU)" },
  { value: 'AGE', label: 'Assemblee Generale Extraordinaire (SARL)' },
  { value: 'AGO', label: 'Assemblee Generale Ordinaire (AGO)' },
  { value: 'CA', label: "Conseil d'administration" },
  { value: 'PV', label: 'Proces-verbal de reunion' },
];

// ============================================================================
// VALIDATION TEMPS REEL (RG transverse 2026-06-05) — par modification choisie
// ============================================================================
// Chaque entree definit les champs requis + regle de validation + message
// utilisateur precis (typo/oubli). Renvoie null si OK, sinon le message FR.
type FieldValidator = (value: string, values: Record<string, string>) => string | null;

interface FieldSpec {
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
    if (v.trim().length < n) return `${label} doit comporter au moins ${n} caracteres (actuel : ${v.trim().length}).`;
    return null;
  };
}
function positiveNumber(label: string): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    const n = Number(v);
    if (!Number.isFinite(n)) return `${label} doit etre un nombre valide.`;
    if (n <= 0) return `${label} doit etre strictement positif.`;
    return null;
  };
}
function dateField(label: string, opts?: { notFuture?: boolean; notPast?: boolean }): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    const ts = Date.parse(v);
    if (Number.isNaN(ts)) return `${label} doit etre une date valide (AAAA-MM-JJ).`;
    if (opts?.notFuture && ts > Date.now() + 86400000)
      return `${label} ne peut pas etre dans le futur.`;
    if (opts?.notPast && ts < Date.now() - 86400000)
      return `${label} ne peut pas etre dans le passe.`;
    return null;
  };
}
function cin(label: string): FieldValidator {
  return (v) => {
    if (!v || !v.trim()) return `${label} est obligatoire.`;
    // CIN marocaine : 1-2 lettres + 5-7 chiffres (souple : autorise espaces internes).
    const clean = v.replace(/\s+/g, '').toUpperCase();
    if (!/^[A-Z]{1,2}\d{4,8}$/.test(clean))
      return `${label} invalide : format attendu 1-2 lettres puis 4-8 chiffres (ex : AB123456).`;
    return null;
  };
}

const FIELD_SPECS: Record<string, FieldSpec[]> = {
  AUGMENTATION_CAPITAL: [
    { name: 'nouveauCapital', label: 'Nouveau capital (MAD)', validate: positiveNumber('Nouveau capital') },
    { name: 'nouvellesParts', label: 'Nombre de nouvelles parts', validate: positiveNumber('Nombre de nouvelles parts') },
    { name: 'dateVersement', label: 'Date versement des fonds', validate: dateField('Date versement', { notFuture: false }) },
  ],
  AUGMENTATION_CAPITAL_NATURE: [
    { name: 'valeurApport', label: "Valeur de l'apport (MAD)", validate: positiveNumber("Valeur de l'apport") },
    { name: 'partsAttribuees', label: 'Nombre de parts attribuees', validate: positiveNumber('Nombre de parts attribuees') },
    { name: 'descriptionBien', label: 'Description du bien apporte', validate: minLen('Description du bien', 10) },
    { name: 'cacNom', label: 'Commissaire aux apports — Nom', validate: required('Nom du commissaire aux apports') },
  ],
  REDUCTION_CAPITAL: [
    { name: 'montantReduction', label: 'Montant reduction (MAD)', validate: positiveNumber('Montant de reduction') },
    { name: 'motif', label: 'Motif', validate: minLen('Motif', 10) },
  ],
  TRANSFERT_SIEGE: [
    { name: 'nouvelleAdresse', label: 'Nouvelle adresse', validate: minLen('Nouvelle adresse', 5) },
    { name: 'nouvelleVille', label: 'Nouvelle ville', validate: required('Nouvelle ville') },
    { name: 'dateEffet', label: "Date d'effet", validate: dateField("Date d'effet") },
  ],
  CHANGEMENT_DENOMINATION: [
    { name: 'nouvelleDenomination', label: 'Nouvelle denomination', validate: minLen('Nouvelle denomination', 2) },
  ],
  CHANGEMENT_OBJET: [
    { name: 'nouvelObjet', label: 'Nouvel objet social', validate: minLen('Nouvel objet social', 20) },
  ],
  PROROGATION_DUREE: [
    { name: 'annees', label: "Nombre d'annees de prorogation", validate: positiveNumber("Nombre d'annees") },
    { name: 'dateEffet', label: "Date d'effet", validate: dateField("Date d'effet") },
  ],
  DESIGNATION_GERANT: [
    { name: 'nom', label: 'Nom', validate: required('Nom') },
    { name: 'prenom', label: 'Prenom', validate: required('Prenom') },
    { name: 'cin', label: 'CIN', validate: cin('CIN') },
    { name: 'dateEffet', label: "Date d'effet", validate: dateField("Date d'effet") },
    { name: 'nationalite', label: 'Nationalite', validate: required('Nationalite') },
  ],
  REVOCATION_GERANT: [
    { name: 'identite', label: 'Identite du gerant revoque', validate: required('Identite du gerant revoque') },
    { name: 'dateEffet', label: "Date d'effet", validate: dateField("Date d'effet") },
    { name: 'motif', label: 'Motif', validate: minLen('Motif', 10) },
  ],
  CESSION_PARTIELLE: [
    { name: 'dateActe', label: "Date de l'acte", validate: dateField("Date de l'acte") },
    { name: 'cessionnaire', label: 'Cessionnaire', validate: required('Cessionnaire') },
    { name: 'nombreParts', label: 'Nombre de parts cedees', validate: positiveNumber('Nombre de parts cedees') },
    { name: 'prix', label: 'Prix de cession (MAD)', validate: positiveNumber('Prix de cession') },
  ],
};

// Fallback generique : un champ "details" obligatoire >= 10 chars.
const FALLBACK_SPEC: FieldSpec[] = [
  { name: 'details', label: 'Details', validate: minLen('Details', 10) },
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
  if (!input.dossierId) return 'Selectionnez la societe a modifier.';
  if (!input.selectedTypes || input.selectedTypes.length === 0) {
    return 'Selectionnez au moins une modification.';
  }
  if (!input.datePV || !input.decisionType) {
    return 'Renseignez la date du PV et le type de decision.';
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
  { key: 'valeurNominale', label: "Valeur nominale d'une part (MAD)", type: 'number' },
  { key: 'dureeAnnees', label: 'Durée de la société (années)', type: 'number' },
];

const REFONTE_GERANT_FIELD: RefonteField = {
  key: 'gerantNom',
  label: 'Nom du gérant en exercice',
  type: 'text',
};

function isBlankValue(v: unknown): boolean {
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

// ============================================================================
// GENERATION DOCUMENTS PAR BLOC (RG transverse 2026-06-05)
// ============================================================================
// Mapping code template -> DocumentType juridique pour depot auto dataroom.
function mapTemplateToDocumentType(code: string): DocumentType {
  // Phase E1 — PV_MODIFICATION_* (modèles directeur) + PV_AGE_* (legacy) → slot PV_MODIFICATION.
  if (code.startsWith('PV_MODIFICATION') || code.startsWith('PV_AGE'))
    return 'PV_MODIFICATION';
  // STATUTS_MODIFIES_* (acte modificatif) ET STATUTS_REFONDUS_* (statut complet
  // refondu, 2026-06-24) déposent dans le slot juridique « Statuts ».
  if (code.startsWith('STATUTS_')) return 'STATUTS';
  // Annonce légale de modification (Phase 2) + anciens ANNONCE_JAL_* → slot ANNONCE_JAL.
  if (code.startsWith('ANNONCE_LEGALE') || code.startsWith('ANNONCE_JAL') || code.includes('JAL'))
    return 'ANNONCE_JAL';
  return 'AUTRE';
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
        rows.push({ typeLabel: L, champ: 'Denomination sociale', avant: dossier?.denomination || '—', apres: v.nouvelleDenomination || '—' });
        if (v.sigle) rows.push({ typeLabel: L, champ: 'Sigle', avant: '—', apres: v.sigle });
        break;
      case 'CHANGEMENT_OBJET':
        rows.push({ typeLabel: L, champ: 'Objet social', avant: '—', apres: v.nouvelObjet || '—' });
        break;
      case 'TRANSFERT_SIEGE':
        rows.push({
          typeLabel: L,
          champ: 'Siege social',
          avant: '—',
          apres: [v.nouvelleAdresse, v.nouvelleVille].filter(Boolean).join(', ') || '—',
        });
        break;
      case 'PROROGATION_DUREE':
        rows.push({
          typeLabel: L,
          champ: 'Duree de la societe',
          avant: '—',
          apres: v.annees ? `+ ${v.annees} an(s)${v.dateEffet ? ` (effet ${v.dateEffet})` : ''}` : '—',
        });
        break;
      case 'AUGMENTATION_CAPITAL':
        rows.push({ typeLabel: L, champ: 'Capital social', avant: formatMad(dossier?.capitalSocial), apres: formatMad(v.nouveauCapital) });
        rows.push({ typeLabel: L, champ: 'Nouvelles parts emises', avant: '—', apres: v.nouvellesParts || '—' });
        break;
      case 'AUGMENTATION_CAPITAL_NATURE':
        rows.push({ typeLabel: L, champ: 'Apport en nature', avant: '—', apres: formatMad(v.valeurApport) });
        rows.push({ typeLabel: L, champ: 'Bien apporte', avant: '—', apres: v.descriptionBien || '—' });
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
        rows.push({ typeLabel: L, champ: 'Gerant nomme', avant: '—', apres: [v.prenom, v.nom].filter(Boolean).join(' ') || '—' });
        if (v.remuneration) rows.push({ typeLabel: L, champ: 'Remuneration gerant', avant: '—', apres: formatMad(v.remuneration) });
        break;
      case 'REVOCATION_GERANT':
        rows.push({ typeLabel: L, champ: 'Gerant revoque', avant: v.identite || '—', apres: 'Revoque' });
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
        rows.push({ typeLabel: L, champ: 'Modification', avant: '—', apres: v.details || '— (annexe a fournir)' });
      }
    }
  }
  return rows;
}

interface DocState {
  generating: boolean;
  generated: boolean;
  filename?: string;
  blob?: Blob;
  validated: boolean;
  error: string | null;
  previewOpen: boolean;
  /** True quand le depot auto dataroom a reussi (sur Valider). */
  depositedToDataroom: boolean;
}

function freshDocState(): DocState {
  return {
    generating: false,
    generated: false,
    validated: false,
    error: null,
    previewOpen: false,
    depositedToDataroom: false,
  };
}

function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

/**
 * Monte {@link ModificationWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
export function ModificationWorkflowPage() {
  return (
    <WorkflowBoot>
      <ModificationWorkflowPageBody />
    </WorkflowBoot>
  );
}

function ModificationWorkflowPageBody() {
  const navigate = useNavigate();
  const {
    ticket, progress, loading, saving, error, setError, stepData, saveDraft,
    executeStep, viewStep, maxStep, goToStep, goPrev: navPrev, registerDirty,
  } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);

  // Step 1 state
  // Phase 1 — la sélection porte désormais des IDs de décisions officielles
  // (catalogue `officialModificationDecisions`), persistés sous `step1.selectedDecisions`.
  const initialSelected = (stepData.step1?.selectedDecisions as string[]) ?? [];
  const initialDate = (stepData.step1?.datePV as string) ?? '';
  // Type d'assemblee simplifie : Ordinaire / Extraordinaire (defaut extraordinaire —
  // la majorite des modifications statutaires relevent de l'AGE). Retro-compat : un
  // brouillon persiste sous l'ancien `decisionType` (AGO ⇒ ordinaire, sinon extra).
  const persistedNature = stepData.step1?.assembleeNature as AssembleeNature | undefined;
  const legacyDecision = stepData.step1?.decisionType as string | undefined;
  const initialNature: AssembleeNature =
    persistedNature ?? (legacyDecision === 'AGO' ? 'ordinaire' : 'extraordinaire');
  // PROMPT F (2026-06-23) — sélecteur de société à l'étape 1.
  const initialDossierId = (stepData.step1?.dossierId as string) ?? '';
  const [selected, setSelected] = useState<string[]>(initialSelected);
  const [datePV, setDatePV] = useState(initialDate);
  const [assembleeNature, setAssembleeNature] = useState<AssembleeNature>(initialNature);
  const [selectedDossierId, setSelectedDossierId] = useState<string>(initialDossierId);
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [dossiersLoading, setDossiersLoading] = useState(false);
  const [search, setSearch] = useState('');
  // Convocation OPTIONNELLE (etape 1) — date + heure + associes a convoquer (BD).
  // Regle DURE des 16 jours entre convocation et PV (validee front + back).
  const initialConv = (stepData.step1?.convocation as
    | { date?: string; heure?: string; associes?: string[] }
    | undefined) ?? {};
  const [convocationDate, setConvocationDate] = useState<string>(initialConv.date ?? '');
  // L'heure etait annoncee par le commentaire ci-dessus mais son etat n'existait
  // pas : elle n'etait ni saisie, ni transmise a la lettre de convocation.
  const [convocationHeure, setConvocationHeure] = useState<string>(initialConv.heure ?? '');
  // Parties prenantes du dossier (associes/gerants) chargees DES la selection de la
  // societe — peuplent le selecteur « associes a convoquer » sans re-saisie d'identite.
  const [dossierParties, setDossierParties] = useState<DossierParties | null>(null);

  // Step 2 state — valeurs legacy (conservées pour buildChangeRows/synthèse).
  const [values] = useState<Record<string, Record<string, string>>>(
    (stepData.step2?.valeurs as Record<string, Record<string, string>>) ?? {},
  );
  // Phase 2 — RÉSOLUTIONS typées (une par décision sélectionnée), source de la
  // saisie des nouvelles valeurs (étape 2) et de la génération du PV (étape 3).
  const [resolutions, setResolutions] = useState<ResolutionState[]>(
    () => (stepData.step2?.resolutions as ResolutionState[] | undefined) ?? [],
  );

  // Step 3 state — generated docs (1 entry par template code)
  const [docs, setDocs] = useState<Record<string, DocState>>(() => {
    const persisted = (stepData.step3?.documents as Record<string, Partial<DocState>>) ?? {};
    const seed: Record<string, DocState> = {};
    for (const [k, v] of Object.entries(persisted)) {
      // Les blobs ne survivent pas a la persistance : on conserve juste les flags.
      seed[k] = { ...freshDocState(), generated: !!v.generated, validated: !!v.validated, depositedToDataroom: !!v.depositedToDataroom, filename: v.filename };
    }
    return seed;
  });
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadTemplatesError, setLoadTemplatesError] = useState<string | null>(null);
  // Phase 7 — le PV est généré par l'éditeur de résolutions ; on remonte son état
  // « validé » au niveau page (gate de l'étape 3).
  /**
   * Fix M2 (2026-08-16) — un PV validé le RESTE.
   *
   * `pvValidated` démarrait à `false` à chaque montage. Revenir à l'étape 1 (pour
   * générer la convocation, cas courant) puis repartir en avant remettait donc le
   * compteur à zéro : il fallait re-générer ET re-valider un PV déjà produit et
   * déjà déposé en Data Room — au risque d'empiler des versions identiques.
   *
   * On ré-hydrate l'état depuis ce que le workflow a persisté (`step5.pvValide`,
   * écrit à la finalisation, et le drapeau de document de l'étape 3). La validation
   * est ainsi une donnée du dossier, pas un état d'écran.
   */
  const [pvValidated, setPvValidated] = useState<boolean>(() => {
    if (stepData.step5?.pvValide === true) return true;
    // `executeStep(3, { documents: docFlags })` persiste ces drapeaux.
    const flags = (stepData.step3?.documents ?? {}) as Record<string, unknown>;
    return flags.PV_MODIFICATION === true;
  });
  // Phase 5 — pièces jointes déposées (persistées pour la synthèse).
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step3?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );
  // Phase 2 — dépôt légal (annonce) : n° + date attribués par le greffe APRÈS dépôt,
  // donc facultatifs à la génération (l'annonce se génère avec ces champs vides).
  const [depotLegalNumero, setDepotLegalNumero] = useState<string>(
    (stepData.step3?.depotLegalNumero as string) ?? '',
  );
  const [depotLegalDate, setDepotLegalDate] = useState<string>(
    (stepData.step3?.depotLegalDate as string) ?? '',
  );
  // #7 — instantanés des panneaux séance (présence/bureau/ordre du jour…), persistés
  // dans le brouillon pour NE PAS perdre les saisies manuelles au changement d'étape.
  const [convocationSnapshot, setConvocationSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step1?.convocationSnapshot as Record<string, unknown>) ?? null,
  );
  const [pvSnapshot, setPvSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step3?.pvSnapshot as Record<string, unknown>) ?? null,
  );
  const [feuilleSnapshot, setFeuilleSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step3?.feuilleSnapshot as Record<string, unknown>) ?? null,
  );

  // PREFLIGHT statut refondu (2026-06-24) — saisies des champs structurés manquants
  // pour une société importée scan-only. Persistées dans step3.ficheOverrides.
  const [ficheOverrides, setFicheOverrides] = useState<Record<string, string>>(
    (stepData.step3?.ficheOverrides as Record<string, string>) ?? {},
  );
  // Scan du statut d'origine affiché EN AIDE (lecture seule, sans OCR ni édition).
  const [scanDocId, setScanDocId] = useState<string | null>(null);
  const [scanOpen, setScanOpen] = useState(false);
  const [scanLoading, setScanLoading] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);

  useEffect(() => {
    if (initialSelected.length && selected.length === 0) setSelected(initialSelected);
    if (initialDate && !datePV) setDatePV(initialDate);
    if (initialDossierId && !selectedDossierId) setSelectedDossierId(initialDossierId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [progress?.id]);

  // PROMPT F (2026-06-23) — charger les sociétés dispo pour le sélecteur.
  useEffect(() => {
    let cancelled = false;
    setDossiersLoading(true);
    dataroomService
      .listDossiers()
      .then((list) => {
        if (cancelled) return;
        setDossiers(list);
      })
      .catch(() => {
        /* best-effort : on n'affiche pas d'erreur bloquante ici */
      })
      .finally(() => {
        if (!cancelled) setDossiersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Phase 1 — décisions officielles sélectionnées (résolues depuis les IDs) +
  // resolutionTypes dédupliqués (vocabulaire directeur alimentant le PV / les flags).
  const selectedDecisions = useMemo(
    () => selected.map((id) => findDecision(id)).filter((d): d is OfficialDecision => !!d),
    [selected],
  );
  const selectedResolutionTypes = useMemo(() => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const d of selectedDecisions) {
      if (!seen.has(d.resolutionType)) {
        seen.add(d.resolutionType);
        out.push(d.resolutionType);
      }
    }
    return out;
  }, [selectedDecisions]);

  // Phase 2 — réconcilie les résolutions avec la sélection (une par décision) :
  // conserve les valeurs saisies, ajoute/retire selon la sélection courante.
  useEffect(() => {
    setResolutions((prev) =>
      seedResolutions(
        prev,
        selectedDecisions.map((d) => ({ id: d.id, resolutionType: d.resolutionType, label: d.label })),
      ),
    );
  }, [selectedDecisions]);

  // Lecture des faits dossier injectes par le backend (workflow_progress.data.dossier).
  const dossier = stepData.dossier as
    | {
        formeJuridique?: string;
        denomination?: string;
        capitalSocial?: number;
        ice?: string;
        adresseSiege?: string;
        ville?: string;
        rcNumero?: string;
        rcTribunal?: string;
        // Backbone refonte statuts (2026-06-24) : état structuré complet persisté
        // (objet/durée/gérance/associés/parts…). Source des "ancien_*" envoyés au mapper.
        ficheStructuree?: Record<string, unknown>;
      }
    | undefined;
  // P1 (fix 400) — la forme juridique doit être connue DÈS l'étape 1, avant le
  // premier execute-step : les faits `stepData.dossier` ne sont persistés qu'APRÈS
  // une validation réussie. On la dérive donc aussi de la société sélectionnée dans
  // la liste (DossierBrief.formeJuridique), sinon le decisionType envoyé peut être
  // incohérent avec la forme réelle → 400 « associé unique / pluri-associés ».
  const selectedDossierBrief = useMemo(
    () => dossiers.find((d) => d.id === selectedDossierId) ?? null,
    [dossiers, selectedDossierId],
  );
  const formeJuridique =
    (dossier?.formeJuridique as string | undefined) ?? selectedDossierBrief?.formeJuridique ?? null;
  const isAU = formeJuridique === 'SARL_AU';
  const forme: ModForme = isAU ? 'SARL_AU' : 'SARL';

  // Type d'assemblee : la nature (Ordinaire/Extraordinaire) est choisie par l'employe ;
  // le decisionType envoye au backend en est DERIVE, garantissant la coherence avec la
  // forme (SARL_AU → toujours AU). Plus de useEffect de « forcage » (source du 400).
  const decisionType = useMemo(
    () => decisionTypeFor(isAU, assembleeNature),
    [isAU, assembleeNature],
  );

  // P2 — liste OFFICIELLE unique recherchable (SANS groupes) filtrée par la forme,
  // triée alphabétiquement, avec recherche par libellé.
  const visibleDecisions = useMemo(() => {
    const term = search.trim().toLowerCase();
    return decisionsForForme(forme)
      .filter((d) => term === '' || d.label.toLowerCase().includes(term))
      .sort((a, b) => a.label.localeCompare(b.label, 'fr', { sensitivity: 'base' }));
  }, [forme, search]);

  // Indicateurs RG sur la selection courante (voie directeur : basés sur resolutionType).
  const jalRequis = useMemo(
    () => selectedResolutionTypes.some((rt) => JAL_TRIGGERS_RT.has(rt)),
    [selectedResolutionTypes],
  );
  const cacRequis = useMemo(
    () => selectedResolutionTypes.some((rt) => CAC_TRIGGERS_RT.has(rt)),
    [selectedResolutionTypes],
  );
  const formeChangeRequis = useMemo(
    () => isAU && selectedResolutionTypes.some((rt) => FORME_CHANGE_TRIGGERS_RT.has(rt)),
    [isAU, selectedResolutionTypes],
  );
  // Phase 2 — au moins une décision PUBLIABLE → l'annonce légale est proposée/générée.
  // Sinon (que des décisions internes), pas d'annonce : on la masque de la grille.
  const hasPubliableDecision = useMemo(
    () => selectedResolutionTypes.some((rt) => !NON_PUBLIABLES_RT.has(rt)),
    [selectedResolutionTypes],
  );

  // ─── PREFLIGHT generation (etape 3) ────────────────────────────────────
  // Liste les champs obligatoires manquants par type selectionne. Separe en
  // 2 groupes :
  //   - blocking : types "couverts" (variables structures dans le PV/statuts).
  //                Tant qu'il reste un manquant, la generation est BLOQUEE.
  //   - warnings : types en "annexe a fournir" (cessions, nature, fusion...).
  //                On affiche un avertissement mais on n'empeche pas de generer.
  // Phase 1 (transitoire) — le preflight legacy (par typeId UPPERCASE) ne s'applique
  // plus à la sélection directeur ; la saisie détaillée est portée par l'éditeur de
  // résolutions (étape 3) et sera intégrée à l'étape 2 en Phase 2. On neutralise donc
  // le preflight legacy pour ne pas bloquer la génération des statuts refondus.
  const preflight = useMemo(() => {
    const raw = computePreflight([], values);
    const withLabel = (entries: PreflightEntry[]) =>
      entries.map((e) => ({
        ...e,
        label: ALL_MODS.find((m) => m.id === e.typeId)?.label ?? e.typeId,
      }));
    return {
      blocking: withLabel(raw.blocking),
      warnings: withLabel(raw.warnings),
    };
  }, [values]);
  const canGenerate = preflight.blocking.length === 0;

  // ─── PREFLIGHT statut refondu (2026-06-24) ──────────────────────────────
  // Fiche EFFECTIVE = faits dossier scalaires (colonnes) ⊕ fiche_structuree
  // persistée ⊕ saisies preflight (ficheOverrides). Sert à détecter ce qui
  // manque pour générer un statut COMPLET refondu, et à alimenter le mapper.
  const effectiveFiche = useMemo<Record<string, unknown>>(() => {
    const fs = (dossier?.ficheStructuree ?? {}) as Record<string, unknown>;
    const flat: Record<string, unknown> = {
      denomination: dossier?.denomination,
      formeJuridique: dossier?.formeJuridique,
      adresseSiege: dossier?.adresseSiege,
      capitalSocial: dossier?.capitalSocial,
      ice: dossier?.ice,
      rcNumero: dossier?.rcNumero,
    };
    // Priorité : overrides > fiche_structuree > faits scalaires. On ignore les vides.
    const merged: Record<string, unknown> = {};
    for (const src of [flat, fs, ficheOverrides]) {
      for (const [k, v] of Object.entries(src)) {
        if (!isBlankValue(v)) merged[k] = v;
      }
    }
    return merged;
  }, [dossier, ficheOverrides]);

  // P3 — associés & gérants EXISTANTS (fiche BD) pour les dropdowns de saisie
  // (jamais de re-saisie d'identité ; seul un nouvel entrant passe par l'OCR).
  const personLists = useMemo<PersonLists>(
    () => ({
      associe: personNames(effectiveFiche.associes),
      gerant: personNames(effectiveFiche.gerants ?? effectiveFiche.dirigeants),
    }),
    [effectiveFiche],
  );

  // Phase 7 — nommage unifié `type - dénomination - forme (- v<n>)` + versioning.
  // Dénomination cible : la nouvelle si une modification la change, sinon la courante.
  const targetDenomination = useMemo(() => {
    const nouvelle = resolutions
      .map((r) => (r.type === 'modification_denomination' ? r.values.nouvelleDenomination : ''))
      .find((v) => v && v.trim());
    return (
      (nouvelle && nouvelle.trim()) ||
      String(effectiveFiche.denomination ?? dossier?.denomination ?? ticket?.titre ?? 'Societe')
    );
  }, [resolutions, effectiveFiche, dossier?.denomination, ticket?.titre]);

  const depositMotif = useMemo(
    () => `Modification : ${selectedResolutionTypes.join(', ')}`,
    [selectedResolutionTypes],
  );

  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }): string =>
      buildDocFilename(
        docTypeForTemplateCode(tpl.code),
        targetDenomination,
        formeLabel(formeJuridique),
        opts?.version,
      ),
    [targetDenomination, formeJuridique],
  );

  // Le statut refondu n'est exigé que si un template REFONDUS est disponible.
  const hasRefonteTemplate = useMemo(
    () => templates.some((t) => t.code.includes('REFONDUS')),
    [templates],
  );
  // Phase 2 — grille des documents : l'annonce légale n'est offerte que si au moins une
  // décision PUBLIABLE est sélectionnée (sinon masquée — « pas d'annonce générée »).
  const visibleTemplates = useMemo(
    () =>
      templates.filter((t) =>
        t.code.startsWith('ANNONCE_LEGALE_MODIFICATION') ? hasPubliableDecision : true,
      ),
    [templates, hasPubliableDecision],
  );
  const refonteMissing = useMemo(
    () => (hasRefonteTemplate ? computeRefonteMissing(effectiveFiche) : []),
    [hasRefonteTemplate, effectiveFiche],
  );
  const refonteBlocked = hasRefonteTemplate && refonteMissing.length > 0;

  // Associés de la société pré-remplis (BD) au format `AssocieInput` — pour peupler
  // la présence/quorum des séances SANS re-saisie. Source : dossierParties (chargé dès
  // la sélection) avec repli sur effectiveFiche.associes (persisté après étape 1).
  const bdAssocies = useMemo<AssocieInput[]>(() => {
    const source =
      (dossierParties?.associes && dossierParties.associes.length > 0
        ? dossierParties.associes
        : (effectiveFiche.associes as unknown[] | undefined)) ?? [];
    return (source as Array<Record<string, unknown>>)
      .map((a): AssocieInput => {
        const parts = String(a?.nombreParts ?? '').trim();
        const morale = String(a?.typePersonne ?? '').toUpperCase() === 'MORALE';
        return {
          typePersonne: morale ? 'MORALE' : 'PHYSIQUE',
          civilite: String(a?.civilite ?? ''),
          prenom: String(a?.prenom ?? ''),
          nom: String(a?.nom ?? ''),
          denomination: String(a?.denomination ?? ''),
          adresse: String(a?.adresse ?? ''),
          nombreParts: parts,
          nombreVoix: parts,
          presence: 'présent',
          mandataireNom: '',
          // Fix M6 (2026-08-16) — la CIN est en base depuis la création : sans
          // elle, la comparution des PV d'associé unique sortait « CIN n° , ».
          pieceType: 'CIN',
          pieceNumero: String(a?.cin ?? a?.cinNumero ?? a?.pieceNumero ?? ''),
        };
      })
      .filter((a) => a.nom || a.prenom || a.denomination);
  }, [dossierParties, effectiveFiche]);

  // Gérants de la société pré-remplis (BD) au format `GerantInput`.
  const bdGerants = useMemo<GerantInput[]>(() => {
    const source =
      (dossierParties?.gerants && dossierParties.gerants.length > 0
        ? dossierParties.gerants
        : (effectiveFiche.gerants as unknown[] | undefined)
          ?? (effectiveFiche.dirigeants as unknown[] | undefined)) ?? [];
    return (source as Array<Record<string, unknown>>)
      .map((g): GerantInput => ({
        civilite: String(g?.civilite ?? ''),
        prenom: String(g?.prenom ?? ''),
        nom: String(g?.nom ?? ''),
      }))
      .filter((g) => g.nom || g.prenom);
  }, [dossierParties, effectiveFiche]);

  // Ordre du jour = libellés des modifications choisies (étape 1) — pré-remplit les
  // séances (convocation / PV) SANS re-saisie.
  const ordreDuJourFromMods = useMemo<string[]>(
    () => (selectedDecisions.length > 0 ? selectedDecisions.map((d) => d.label) : ['']),
    [selectedDecisions],
  );

  // Charge les parties prenantes (associes/gerants) DES qu'une societe est
  // selectionnee — pour peupler le selecteur de convocation avant toute validation.
  useEffect(() => {
    if (!selectedDossierId) {
      setDossierParties(null);
      return;
    }
    let cancelled = false;
    workflowService
      .getDossierParties(selectedDossierId)
      .then((parties) => {
        if (!cancelled) setDossierParties(parties);
      })
      .catch(() => {
        /* best-effort : repli sur effectiveFiche.associes */
      });
    return () => {
      cancelled = true;
    };
  }, [selectedDossierId]);

  // Regle DURE des 16 jours : ecart entre la convocation et la date du PV. Miroir
  // exact de la validation backend (ModificationWorkflow.validateConvocation).
  const convocationError = useMemo<string | null>(
    () => convocationDelaiError(convocationDate, datePV),
    [convocationDate, datePV],
  );

  // Chargement des templates MODIFICATION quand on arrive sur l'etape 3 (generation).
  // Aussi a l'etape 5 (synthese) pour disposer des libelles (documentKind), meme si
  // l'employe arrive directement sur l'etape finale.
  useEffect(() => {
    if (viewStep !== 3 && viewStep !== 5) return;
    if (templates.length > 0) return;
    let cancelled = false;
    setLoadingTemplates(true);
    setLoadTemplatesError(null);
    listTemplatesForWorkflow('MODIFICATION')
      .then((items) => {
        if (cancelled) return;
        // Filtre par forme juridique : SARL_AU -> templates *_SARL_AU, sinon *_SARL.
        const suffix = isAU ? 'SARL_AU' : 'SARL';
        const filtered = items.filter((t) => {
          // Le PV de modification est généré par l'éditeur de résolutions (ci-dessous),
          // pas par la grille : on ne garde ici que les Statuts refondus.
          if (t.code.startsWith('PV_MODIFICATION')) return false;
          if (t.code.endsWith('_SARL_AU')) return suffix === 'SARL_AU';
          if (t.code.endsWith('_SARL')) return suffix === 'SARL';
          return true;
        });
        const sorted = [...filtered].sort((a, b) => {
          // PV avant Statuts pour respecter l'ordre juridique.
          if (a.code.startsWith('PV_') && !b.code.startsWith('PV_')) return -1;
          if (b.code.startsWith('PV_') && !a.code.startsWith('PV_')) return 1;
          return (a.documentKind || a.code).localeCompare(b.documentKind || b.code);
        });
        setTemplates(sorted);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadTemplatesError(
          err instanceof Error ? err.message : 'Impossible de charger les documents disponibles.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoadingTemplates(false);
      });
    return () => {
      cancelled = true;
    };
  }, [viewStep, isAU, templates.length]);

  // 2026-06-22 — Early returns DÉPLACÉS après TOUS les hooks (voir plus bas,
  // après depositGeneratedToDataroom) pour respecter les Rules of Hooks :
  // un useCallback se trouvait après ces return -> "Rendered more hooks than
  // during the previous render" quand loading passait de true à false.

  const step = viewStep;

  // Phase 2 — mutations d'une résolution (par id de décision).
  const setResField = (id: string, key: string, value: string) =>
    setResolutions((prev) =>
      prev.map((r) => (r.id === id ? { ...r, values: { ...r.values, [key]: value } } : r)),
    );
  const setResRows = (id: string, key: string, rows: Array<Record<string, string>>) =>
    setResolutions((prev) =>
      prev.map((r) => (r.id === id ? { ...r, rows: { ...r.rows, [key]: rows } } : r)),
    );
  const setResObjet = (id: string, objet: string) =>
    setResolutions((prev) => prev.map((r) => (r.id === id ? { ...r, objet } : r)));
  const setResNewAssocie = (id: string, na: Record<string, string>) =>
    setResolutions((prev) => prev.map((r) => (r.id === id ? { ...r, newAssocie: na } : r)));
  // Patch d'un seul champ du nouvel associé (saisie manuelle, fusionne avec l'existant).
  const setResNewAssocieField = (id: string, key: string, value: string) =>
    setResolutions((prev) =>
      prev.map((r) =>
        r.id === id ? { ...r, newAssocie: { ...(r.newAssocie ?? {}), [key]: value } } : r,
      ),
    );

  // 2026-06-22 (P1) — Sauvegarde AVANT toute navigation pour ne pas perdre les
  // valeurs saisies à l'étape courante (le brouillon n'était posé qu'au clic manuel).
  async function goPrev() {
    await handleSaveDraft();
    navPrev();
  }
  // Navigation via la roadmap : sauve l'étape courante puis va à l'étape demandée.
  async function navigateToStep(n: number) {
    await handleSaveDraft();
    goToStep(n);
  }

  function updateDoc(code: string, patch: Partial<DocState>) {
    setDocs((prev) => ({
      ...prev,
      [code]: { ...(prev[code] ?? freshDocState()), ...patch },
    }));
  }

  /**
   * RG transverse 2026-06-05 — depot auto dataroom (Killer Feature Bug 4).
   * Idempotent : `depositedToDataroom` empeche les doubles uploads en cas
   * de revalidation. Best-effort : un echec dataroom n'invalide pas la
   * progression de l'utilisateur (l'erreur est consignee mais le doc reste valide).
   */
  const depositGeneratedToDataroom = useCallback(
    async (tpl: TemplateInfo, st: DocState) => {
      // PROMPT F (2026-06-23) — on dépose dans le dossier CHOISI à l'étape 1
      // (et non plus celui rattaché au ticket).
      const targetDossierId = selectedDossierId || ticket?.dossierId;
      if (!targetDossierId || !st.blob || !st.filename) return;
      if (st.depositedToDataroom) return;
      const targetType = mapTemplateToDocumentType(tpl.code);
      const targetTitle = tpl.documentKind || tpl.code;
      // Sprint 2026-06-23 — versioning : si un Document logique existant porte
      // déjà le même (type+title) dans ce dossier, on remplace en NOUVELLE
      // VERSION (l'ancien part en historique). Sinon création classique.
      // Le motif trace la cause : « Modification : <types> ».
      const motif = `Modification : ${selectedResolutionTypes.join(', ')}`;
      try {
        let existingDocumentId: string | undefined;
        let matchedVersion: number | undefined;
        try {
          const view = await dataroomService.getJuridique(targetDossierId);
          // Appariement standard : même (type + titre) -> nouvelle version du MÊME
          // document logique (régénérations successives).
          let match = view.documentsEnVigueur.find(
            (d) => d.documentType === targetType && d.title === targetTitle,
          );
          // 2026-06-24 — Statut refondu : s'il n'existe pas encore sous ce titre,
          // il PREND LA PLACE du statut en vigueur (le SCAN importé ou le statut
          // précédent, documentType=STATUTS) -> celui-ci part en historique avec
          // le motif « Modification : <types> ». C'est le slot « Statuts » canonique.
          if (!match && tpl.code.includes('REFONDUS') && targetType === 'STATUTS') {
            match = view.documentsEnVigueur.find((d) => d.documentType === 'STATUTS');
          }
          existingDocumentId = match?.id;
          matchedVersion = match?.version;
        } catch {
          // best-effort : si la vue échoue, on retombe sur la création classique.
        }
        // Phase 7 — convention de nommage unifiée `type - dénomination - forme (- v<n>)`.
        // Le suffixe « - v<n> » n'apparaît que si un document de même type+nom existe déjà.
        const nextVersion = existingDocumentId ? (matchedVersion ?? 1) + 1 : undefined;
        const filename = workflowFilenameFor(tpl, { version: nextVersion });
        await dataroomService.depositGeneratedDoc(targetDossierId, st.blob, {
          documentType: targetType,
          title: targetTitle,
          filename,
          ticketId: ticket?.id,
          motif,
          replacePrevious: !!existingDocumentId,
          existingDocumentId,
        });
        updateDoc(tpl.code, { depositedToDataroom: true });
      } catch (err) {
        // Best-effort : on remonte l'erreur dans le doc mais on n'invalide pas la validation.
        const msg = err instanceof Error ? err.message : 'Echec depot Dataroom';
        updateDoc(tpl.code, { error: `Genere/valide OK. Depot Dataroom : ${msg}` });
      }
    },
    [
      selectedDossierId,
      ticket?.dossierId,
      ticket?.id,
      selectedResolutionTypes,
      workflowFilenameFor,
    ],
  );

  // 2026-06-22 — Tous les hooks sont déclarés au-dessus : on place ICI les
  // returns conditionnels (après le dernier hook) pour respecter les Rules of Hooks.
  if (loading) return <Loader2 />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );

  async function generateOne(tpl: TemplateInfo) {
    updateDoc(tpl.code, { generating: true, error: null, validated: false });
    try {
      const { blob, filename } = await generateDocument('MODIFICATION', tpl.code, buildPayload());
      updateDoc(tpl.code, {
        generating: false,
        generated: true,
        blob,
        filename,
        previewOpen: true,
        depositedToDataroom: false,
      });
    } catch (err) {
      updateDoc(tpl.code, {
        generating: false,
        error: err instanceof Error ? err.message : 'Generation impossible',
      });
    }
  }
  function downloadOne(tpl: TemplateInfo) {
    const st = docs[tpl.code];
    if (st?.blob && st.filename) triggerBrowserDownload(st.blob, st.filename);
  }
  async function validateOne(tpl: TemplateInfo) {
    const st = docs[tpl.code];
    if (!st?.generated) return;
    updateDoc(tpl.code, { validated: true });
    // Depot auto dataroom (RG transverse 2026-06-05).
    await depositGeneratedToDataroom(tpl, { ...st, validated: true });
  }

  // PREFLIGHT statut refondu — saisie d'un champ structuré manquant.
  function setFicheOverride(key: string, val: string) {
    setFicheOverrides((p) => ({ ...p, [key]: val }));
  }

  /**
   * Charge le SCAN du statut d'origine (documentType=STATUTS) et l'affiche EN AIDE
   * à la lecture (aperçu seul, sans OCR ni édition). Best-effort : message si absent.
   */
  async function openOriginalStatutsScan() {
    const targetDossierId = selectedDossierId || ticket?.dossierId;
    if (!targetDossierId) {
      setScanError('Aucune société sélectionnée.');
      return;
    }
    setScanLoading(true);
    setScanError(null);
    try {
      const view = await dataroomService.getJuridique(targetDossierId);
      const scan = view.documentsEnVigueur.find((d) => d.documentType === 'STATUTS');
      if (!scan) {
        setScanError("Aucun statut d'origine trouvé dans la Data Room de cette société.");
        return;
      }
      setScanDocId(scan.id);
      setScanOpen(true);
    } catch {
      setScanError("Impossible de charger le statut d'origine.");
    } finally {
      setScanLoading(false);
    }
  }

  async function handleValidate() {
    setError(null);
    if (step === 1) {
      const guard = validateStep1Inputs({
        dossierId: selectedDossierId,
        selectedTypes: selected,
        datePV,
        decisionType,
      });
      if (guard) {
        setError(guard);
        return;
      }
      // Convocation OPTIONNELLE — regle DURE des 16 jours (bloque si fournie et < 16 j).
      if (convocationError) {
        setError(convocationError);
        return;
      }
      // Voie directeur : décisions officielles (id + resolutionType), `selectedTypes`
      // = resolutionTypes. Le backend recalcule JAL/CAC/forme depuis les resolutionTypes.
      await executeStep(1, {
        dossierId: selectedDossierId,
        // Fallback backend (RG-M06) si les faits dossier ne sont pas encore chargés.
        formeJuridique: isAU ? 'SARL_AU' : 'SARL',
        selectedDecisions: selectedDecisions.map((d) => ({
          id: d.id,
          resolutionType: d.resolutionType,
          label: d.label,
          group: d.group,
          generic: !!d.generic,
          newAssocie: !!d.newAssocie,
        })),
        selectedTypes: selectedResolutionTypes,
        datePV,
        decisionType,
        assembleeNature,
        convocation: convocationPayload(),
      });
    } else if (step === 2) {
      // Saisie par résolution (anciennes valeurs affichées + OCR nouvel associé).
      // Les résolutions typées sont persistées côté backend pour la génération
      // (étape 3) et la synthèse. Validation métier fine portée par l'éditeur.
      await executeStep(2, { valeurs: {}, resolutions });
    } else if (step === 3) {
      // RG-M12 : le PV (éditeur de résolutions) ET les statuts refondus doivent être
      // validés. Le PV n'est plus dans la grille : on lit son état via `pvValidated`.
      const missing: string[] = [];
      if (!pvValidated) missing.push('le PV de modification');
      const statutsTpl = templates.find((t) => t.code.startsWith('STATUTS_'));
      if (statutsTpl && !docs[statutsTpl.code]?.validated) missing.push('les statuts');
      if (missing.length > 0) {
        setError('Validez tous les documents avant de continuer : ' + missing.join(', ') + '.');
        return;
      }
      // La génération n'impose pas de payload bloquant ; on avance vers les pièces jointes.
      const docFlags: Record<string, boolean> = { PV_MODIFICATION: pvValidated };
      for (const [code, st] of Object.entries(docs)) docFlags[code] = !!st.validated;
      await executeStep(3, { documents: docFlags });
    } else if (step === 4) {
      // Étape pièces jointes OPTIONNELLE : jamais bloquante (peut être vide).
      await executeStep(4, {
        piecesJointes: piecesJointes.map((p) => ({
          id: p.id,
          label: p.label,
          filename: p.filename,
          version: p.version ?? null,
        })),
      });
    } else if (step === 5) {
      // Finalisation : validation PV + statuts + (annonce légale si décision publiable).
      const statutsValides = templates.some(
        (t) => t.code.startsWith('STATUTS_') && docs[t.code]?.validated,
      );
      const annonceTpl = templates.find((t) => t.code.startsWith('ANNONCE_LEGALE_MODIFICATION'));
      const jalValide = annonceTpl ? !!docs[annonceTpl.code]?.validated : false;
      // RG-M13 : si une annonce est requise (décision publiable) mais non validée, on bloque
      // avec un message clair AVANT l'appel backend.
      if (hasPubliableDecision && !jalValide) {
        setError(
          "Validez l'annonce légale de modification (étape 3) avant de finaliser : sa publication "
            + 'au Journal d\'Annonces Légales est obligatoire pour cette modification.',
        );
        return;
      }
      await executeStep(5, { pvValide: pvValidated, statutsValides, jalValide });
    }
  }

  /**
   * Contenu courant de l'étape — voir la note dans DissolutionWorkflowPage.
   *
   * Les étapes 3 et 4 partagent la clé `step3` (documents + pièces jointes) :
   * la sauvegarde automatique doit conserver ce regroupement, sans quoi la
   * relecture au retour perdrait la moitié du bloc.
   */
  const draftPayloadFor = useCallback(
    (n: number): { cle: string; payload: Record<string, unknown> } | null => {
      if (n === 1) {
        return {
          cle: 'step1',
          payload: {
            selectedDecisions: selected,
            datePV,
            decisionType,
            assembleeNature,
            convocation: convocationPayload(),
            convocationSnapshot,
            dossierId: selectedDossierId || null,
          },
        };
      }
      if (n === 2) return { cle: 'step2', payload: { resolutions } };
      if (n === 3 || n === 4) {
        // Seules les métadonnées des documents sont persistables (pas les blobs).
        const persistable: Record<string, Partial<DocState>> = {};
        for (const [code, st] of Object.entries(docs)) {
          persistable[code] = {
            generated: st.generated,
            validated: st.validated,
            depositedToDataroom: st.depositedToDataroom,
            filename: st.filename,
          };
        }
        return {
          cle: 'step3',
          payload: {
            documents: persistable,
            ficheOverrides,
            piecesJointes,
            depotLegalNumero,
            depotLegalDate,
            pvSnapshot,
            feuilleSnapshot,
          },
        };
      }
      return null;
    },
    [selected, datePV, decisionType, assembleeNature, convocationDate, convocationHeure,
      convocationSnapshot, selectedDossierId, resolutions, docs, ficheOverrides,
      piecesJointes, depotLegalNumero, depotLegalDate, pvSnapshot, feuilleSnapshot],
  );

  // `__stepKey` : les etapes 3 et 4 partagent la cle `step3` (cf. flushDraft).
  useStepAutosave(
    step,
    () => {
      const d = draftPayloadFor(step);
      return d ? { __stepKey: d.cle, ...d.payload } : null;
    },
    registerDirty,
  );

  async function handleSaveDraft() {
    const d = draftPayloadFor(step);
    if (d) await saveDraft(step, { [d.cle]: d.payload });
  }

  /** Payload convocation normalisé (ou undefined si aucune date renseignée). */
  function convocationPayload(): { date: string; heure?: string } | undefined {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }

  async function handleCloturer() {
    if (!ticket) return;
    try {
      await ticketService.transition(ticket.id, { target: 'CLOTURE_DOSSIER' });
      navigate('/tickets');
    } catch (err) {
      setError((err as Error)?.message ?? 'Echec de cloture');
    }
  }

  // Mises à jour FONCTIONNELLES : plusieurs sélections rapprochées (clics rapides,
  // batching React) ne doivent pas s'écraser — l'ancienne forme `[...selected, id]`
  // lisait un état périmé et ne conservait que la dernière sélection.
  const add = (id: string) =>
    setSelected((prev) => (prev.includes(id) ? prev : [...prev, id]));
  const remove = (id: string) => setSelected((prev) => prev.filter((x) => x !== id));

  const isTerminated = progress.statut === 'TERMINE';

  function buildPayload(): Record<string, unknown> {
    // Backbone refonte statuts (2026-06-24) — on transmet l'ÉTAT STRUCTURÉ COMPLET
    // (et plus seulement {denomination, forme, ice}) afin que le mapper dispose des
    // valeurs "ancien_*" (capital/adresse/objet…) et puisse refondre le statut.
    // Source : fiche EFFECTIVE = faits dossier ⊕ fiche_structuree ⊕ saisies preflight.
    const fiche: Record<string, unknown> = { ...effectiveFiche };
    const fnum = (v: unknown): number | null => {
      if (v === null || v === undefined || v === '') return null;
      const n = Number(v);
      return Number.isFinite(n) ? n : null;
    };
    // Gérant saisi au preflight (société importée scan-only) : injecté dans la
    // fiche pour que la refonte rende l'article GÉRANCE, si aucun gérant connu.
    const gerantNom = String(fiche.gerantNom ?? '').trim();
    const hasGerant = Array.isArray(fiche.gerants ?? fiche.dirigeants);
    if (gerantNom && !hasGerant) {
      fiche.gerants = [{ nom: gerantNom, isStatutaire: false }];
    }
    return {
      // Point 4 (audit directeur) — transmet le dossierId pour que la generation backend
      // enrichisse l'identite societe depuis la BD (SocieteIdentityEnricher), comme les
      // autres workflows. Sans lui, la voie legacy generait sans enrichissement BD.
      dossierId: selectedDossierId || ticket?.dossierId || undefined,
      societe: {
        denomination: (fiche.denomination as string) ?? dossier?.denomination ?? ticket?.titre,
        formeJuridique: (fiche.formeJuridique as string) ?? formeJuridique ?? 'SARL',
        iceNumero: (fiche.ice as string) ?? dossier?.ice ?? null,
        rcNumero: (fiche.rcNumero as string) ?? dossier?.rcNumero ?? null,
        rcVille: dossier?.rcTribunal ?? null,
        ifNumero: (fiche.identifiantFiscal as string) ?? null,
        adresseSiege: (fiche.adresseSiege as string) ?? dossier?.adresseSiege ?? null,
        objetSocial: (fiche.objetSocial as string) ?? null,
        // Clés attendues par ModificationMapper.mapStatutsModifies / mapSocieteHeader.
        capitalChiffres: fnum(fiche.capitalSocial) ?? fnum(dossier?.capitalSocial),
        valeurPart: fnum(fiche.valeurNominale),
        nombreParts: fnum(fiche.nombreParts),
      },
      assemblee: {
        dateAge: datePV,
      },
      // Séance : alimente $ASSEMBLEE_DATE (annonce légale + PV) via SeancePvVarsBuilder.
      seance: {
        type: assembleeNature,
        date: datePV,
      },
      // Phase 2 — dépôt légal (annonce) : n°/date attribués par le greffe APRÈS dépôt.
      // Générés VIDES si non fournis (aucun marqueur résiduel).
      depotLegal: {
        numero: depotLegalNumero || null,
        date: depotLegalDate || null,
      },
      // Phase 2 — les modifications proviennent des résolutions saisies (étape 2).
      modifications: resolutions.map((r) => ({
        typeId: r.type,
        details: r.values,
        objet: r.objet,
        nouvelAssocie: r.newAssocie ?? null,
      })),
      resolutions: resolutions.map(flattenResolution),
      decisionType,
      // Pass-through de l'état structuré complet pour la refonte (étape 2 backend).
      ficheStructuree: fiche,
    };
  }

  // ─── Donnees derivees pour la synthese (etape 4) — plain consts (cheap) ───
  // Phase 6 — récap avant→après reconstruit depuis la saisie PAR RÉSOLUTION (étape 2).
  const resolutionRecap = resolutions.map((r) => {
    const decision = findDecision(r.id);
    const spec = RES_SPECS[r.type];
    const olds = oldValuesFor(r.type, effectiveFiche);
    const newVals = (spec?.fields ?? [])
      .map((f) => ({ label: f.label, value: (r.values[f.key] ?? '').trim() }))
      .filter((x) => x.value !== '');
    return {
      id: r.id,
      label: decision?.label ?? spec?.label ?? r.type,
      olds,
      newVals,
      newAssocie: r.newAssocie,
    };
  });
  // Articles de statuts impactes : agrégés depuis les faits step 1 (backend).
  const step1Articles =
    (stepData.step1?.articlesImpactes as { type: string; articles: string[] }[] | undefined) ?? [];
  const articlesImpactes = Array.from(
    new Set(step1Articles.flatMap((a) => a.articles ?? []).filter((s): s is string => !!s)),
  );
  // Documents generes/valides. Le PV vient de l'éditeur (pvValidated) ; les statuts +
  // optionnels (incident/feuille) viennent de `docs` (grille + panneaux).
  const synthesisDocs: {
    code: string;
    label: string;
    kind: 'PV' | 'STATUTS' | 'JAL' | 'AUTRE';
    validated: boolean;
    deposited: boolean;
  }[] = [
    ...(pvValidated
      ? [{ code: 'PV_MODIFICATION', label: 'PV de modification', kind: 'PV' as const, validated: true, deposited: true }]
      : []),
    ...Object.entries(docs)
      .filter(([, st]) => st.generated || st.validated)
      .map(([code, st]) => ({
        code,
        label: templates.find((t) => t.code === code)?.documentKind || code,
        kind: classifyDocKind(code),
        validated: st.validated,
        deposited: st.depositedToDataroom,
      })),
  ].sort((a, b) => {
    const order = { PV: 0, STATUTS: 1, JAL: 2, AUTRE: 3 } as const;
    return order[a.kind] - order[b.kind];
  });

  return (
    <>
      <WorkflowShell
        ticket={ticket}
        steps={STEPS}
        currentStep={maxStep}
        viewStep={step}
        onNavigate={navigateToStep}
        error={error}
        saving={saving}
        onPrev={step > 1 ? goPrev : undefined}
        onSaveDraft={!isTerminated ? handleSaveDraft : undefined}
        onValidate={!isTerminated ? handleValidate : undefined}
        onCancel={() => setShowCancel(true)}
        footer={{ validateLabel: step === 5 ? 'Finaliser' : "Valider l'etape" }}
      >
        {step === 1 && (
          <div className="space-y-5">
            <header>
              <h2 className="text-lg font-semibold text-fg">Etape 1 — Selection des modifications</h2>
              <p className="text-sm text-fg-subtle">
                Choisissez la societe puis les modifications a apporter. Les articles des statuts impactes seront detectes automatiquement.
              </p>
            </header>

            {/* P1 — message d'erreur backend (StepResult.blocked) affiché explicitement. */}
            {error && (
              <div
                role="alert"
                data-testid="mod-step1-error"
                className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"
              >
                <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* PROMPT F (2026-06-23) — sélecteur de société, obligatoire. */}
            <div
              className="rounded-xl border border-border bg-bg-raised p-4"
              data-testid="step1-societe-block"
            >
              <label
                htmlFor="step1-societe-select"
                className="mb-1 block text-sm font-semibold text-fg"
              >
                Societe a modifier *
              </label>
              <p className="mb-2 text-xs text-fg-subtle">
                Les documents generes seront deposes (et versionnes) dans la Data Room de cette societe.
              </p>
              <select
                id="step1-societe-select"
                data-testid="step1-societe-select"
                value={selectedDossierId}
                onChange={(e) => setSelectedDossierId(e.target.value)}
                disabled={dossiersLoading}
                className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
              >
                <option value="">
                  {dossiersLoading ? 'Chargement…' : 'Selectionnez une societe…'}
                </option>
                {dossiers.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.raisonSociale}
                    {d.formeJuridique ? ` — ${d.formeJuridique}` : ''}
                    {d.ice ? ` (ICE ${d.ice})` : ''}
                  </option>
                ))}
              </select>
              {!selectedDossierId && !dossiersLoading && (
                <p className="mt-1 text-xs text-fg-subtle">
                  La societe est obligatoire pour pouvoir continuer.
                </p>
              )}
            </div>

            {/* Sélection des modifications — DEUX panneaux : à gauche les modifications
                disponibles (filtrées selon la forme juridique de la société), à droite
                celles sélectionnées. Sans groupes/accordéons. */}
            <div className="grid gap-4 lg:grid-cols-2" data-testid="mod-official-list">
              {/* GAUCHE : disponibles selon la forme. */}
              <div className="flex flex-col rounded-xl border border-border bg-bg-raised">
                <div className="border-b border-border p-3">
                  <div className="mb-2 flex items-center gap-2">
                    <ListChecks className="h-4 w-4 text-fg-subtle" />
                    <p className="text-sm font-semibold text-fg">
                      Modifications disponibles — {forme}
                    </p>
                  </div>
                  <div className="relative">
                    <Search className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-fg-subtle" />
                    <input
                      type="text"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Rechercher une modification…"
                      data-testid="mod-search-input"
                      className="w-full rounded-lg border border-border-hi bg-bg-raised py-1.5 pl-8 pr-3 text-xs focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                    />
                  </div>
                </div>
                <div className="max-h-[520px] overflow-y-auto p-2">
                  {visibleDecisions.length === 0 && (
                    <p className="py-6 text-center text-xs text-fg-subtle">Aucun résultat.</p>
                  )}
                  {visibleDecisions.map((it) => {
                    const checked = selected.includes(it.id);
                    return (
                      <button
                        key={it.id}
                        type="button"
                        onClick={() => (checked ? remove(it.id) : add(it.id))}
                        data-testid={`mod-decision-${it.id}`}
                        aria-pressed={checked}
                        className={`group flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left text-xs transition ${
                          checked ? 'bg-accent/10' : 'hover:bg-accent/5'
                        }`}
                      >
                        <span
                          className={`mt-0.5 flex h-4 w-4 flex-shrink-0 items-center justify-center rounded border ${
                            checked ? 'border-accent bg-accent text-bg-raised' : 'border-border-hi'
                          }`}
                        >
                          {checked && <CheckCircle className="h-3 w-3" />}
                        </span>
                        <span className="flex-1 text-fg">
                          {it.label}
                          {it.newAssocie && (
                            <span className="ml-1 rounded bg-emerald-50 px-1 py-0.5 text-[9px] font-semibold text-emerald-700">
                              nouvel associé (OCR)
                            </span>
                          )}
                          {it.generic && (
                            <span className="ml-1 rounded bg-amber-100 px-1 py-0.5 text-[9px] font-semibold text-amber-700">
                              type générique
                            </span>
                          )}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* DROITE : sélectionnées. */}
              <div className="flex flex-col rounded-xl border-2 border-accent/40 bg-bg-raised">
                <div className="border-b border-indigo-100 bg-accent/10 p-3">
                  <div className="flex items-center gap-2">
                    <CheckCircle className="h-4 w-4 text-accent" />
                    <p className="text-sm font-semibold text-fg">Modifications sélectionnées</p>
                    <span
                      className="ml-auto rounded-full bg-accent px-2 py-0.5 text-[10px] font-bold text-bg-raised"
                      data-testid="mod-selected-count"
                    >
                      {selected.length}
                    </span>
                  </div>
                </div>
                <div className="max-h-[520px] flex-1 overflow-y-auto p-3">
                  {selectedDecisions.length === 0 ? (
                    <p className="py-8 text-center text-xs text-fg-subtle">
                      Cochez une ou plusieurs modifications à gauche pour les ajouter.
                    </p>
                  ) : (
                    <ul className="space-y-2" data-testid="mod-selected-chips">
                      {selectedDecisions.map((it, i) => (
                        <li
                          key={it.id}
                          className="group flex items-start gap-2 rounded-lg border border-border bg-bg-overlay p-2"
                        >
                          <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded bg-accent text-[10px] font-bold text-bg-raised">
                            {i + 1}
                          </span>
                          <div className="min-w-0 flex-1">
                            <p className="text-xs font-semibold text-fg">{it.label}</p>
                            <p className="mt-0.5 flex flex-wrap items-center gap-1 text-[10px] text-fg-subtle">
                              <span className="rounded bg-violet-50 px-1 py-0.5 font-medium text-violet-700">
                                {GROUP_LABELS[it.group]}
                              </span>
                              {it.newAssocie && (
                                <span className="rounded bg-emerald-50 px-1 py-0.5 font-medium text-emerald-700">
                                  nouvel associé (OCR)
                                </span>
                              )}
                              {it.generic && (
                                <span className="rounded bg-amber-100 px-1 py-0.5 font-medium text-amber-700">
                                  type générique
                                </span>
                              )}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() => remove(it.id)}
                            className="rounded p-1 text-fg-subtle hover:bg-danger/10 hover:text-danger"
                            aria-label={`Retirer ${it.label}`}
                          >
                            <X className="h-3.5 w-3.5" />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <p className="mb-3 text-sm font-semibold text-fg">
                Procès-verbal / Décision & convocation
              </p>
              <div className="grid gap-3 md:grid-cols-3">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="mod-convocation-date"
                  onChange={(e) => {
                    const v = e.target.value;
                    setConvocationDate(v);
                    // RG-M : l'assemblée se tient 16 jours après la convocation.
                    if (v) setDatePV(addDaysIso(v, CONVOCATION_DELAI_JOURS));
                  }}
                />
                <TextField
                  label="Heure de convocation"
                  type="time"
                  value={convocationHeure}
                  data-testid="mod-convocation-heure"
                  onChange={(e) => setConvocationHeure(e.target.value)}
                />
                <TextField
                  label="Date du PV / de l'assemblée *"
                  type="date"
                  value={datePV}
                  onChange={(e) => setDatePV(e.target.value)}
                />
                <Select
                  label="Type d'assemblée *"
                  value={assembleeNature}
                  onChange={(e) => setAssembleeNature(e.target.value as AssembleeNature)}
                  options={NATURE_OPTIONS}
                />
              </div>
              <p className="mt-2 text-[11px] text-fg-subtle">
                Renseignez la date de convocation : la date du PV / de l'assemblée est fixée
                automatiquement à <strong>+ {CONVOCATION_DELAI_JOURS} jours</strong> (règle des 16 jours).
                Vous pouvez repousser la date du PV, jamais la rapprocher à moins de 16 jours.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="mod-convocation-16j-error"
                  className="mt-2 flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-2 text-xs text-danger"
                >
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
              {formeJuridique && (
                <p className="mt-2 text-xs text-fg-subtle" data-testid="mod-assemblee-hint">
                  Forme juridique detectee : <strong>{formeJuridique}</strong> —{' '}
                  {isAU
                    ? `decision ${assembleeNature} de l'associe unique (SARL AU).`
                    : `assemblee generale ${assembleeNature === 'ordinaire' ? 'ordinaire (AGO)' : 'extraordinaire (AGE)'}.`}
                </p>
              )}
              {(jalRequis || cacRequis || formeChangeRequis) && (
                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  {jalRequis && (
                    <span className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">
                      <Megaphone className="h-4 w-4" /> Publication au JAL obligatoire (RG-M13/M14)
                    </span>
                  )}
                  {cacRequis && (
                    <span className="flex items-center gap-2 rounded-lg bg-violet-50 px-3 py-2 text-xs font-medium text-violet-800">
                      <Gavel className="h-4 w-4" /> Commissaire aux apports obligatoire (RG-M10)
                    </span>
                  )}
                  {formeChangeRequis && (
                    <span className="flex items-center gap-2 rounded-lg bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800 sm:col-span-2">
                      <AlertTriangle className="h-4 w-4" /> Cette cession entrainera l'adaptation
                      automatique des statuts en SARL pluri-associes (RG-M11)
                    </span>
                  )}
                </div>
              )}
            </div>

            {/* Phase 1 — Convocation proposée JUSTE APRÈS la sélection (optionnelle, repliable). */}
            <details className="rounded-xl border border-border bg-bg-raised" data-testid="mod-convocation-step1">
              <summary className="flex cursor-pointer list-none items-center gap-2 p-4 text-sm font-semibold text-fg">
                <ChevronRight className="h-4 w-4 transition group-open:rotate-90" />
                <Megaphone className="h-4 w-4 text-accent" />
                Convocation à l'assemblée (optionnelle)
                <span className="ml-auto text-xs font-normal text-fg-subtle">Repliable — non obligatoire</span>
              </summary>
              <div className="space-y-3 border-t border-border p-4">
                <p className="text-[11px] text-fg-subtle">
                  La date de convocation et le type d'assemblée sont ceux saisis ci-dessus.
                  Les <strong>associés</strong>, la <strong>gérance</strong> et l'<strong>ordre du
                  jour</strong> (= modifications choisies) sont <strong>déjà pré-remplis</strong> —
                  ajustez la présence si besoin, sans tout re-saisir.
                </p>
                <ConvocationPanel
                  dossierId={selectedDossierId || ticket?.dossierId}
                  ticketId={ticket?.id}
                  formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                  societe={{
                    denomination: dossier?.denomination ?? ticket.titre,
                    capitalChiffres: dossier?.capitalSocial ?? null,
                    siegeSocial: dossier?.adresseSiege ?? null,
                    nombreParts:
                      (effectiveFiche.nombreParts as number | string | undefined) ?? null,
                    rcNumero: dossier?.rcNumero ?? null,
                    villeGreffe: dossier?.rcTribunal ?? dossier?.ville ?? null,
                  }}
                  // Date d'assemblee, date ET heure de convocation viennent de cette
                  // etape : le panneau les recoit, il ne les redemande pas.
                  defaultSeance={{
                    type: assembleeNature,
                    date: datePV,
                    convDate: convocationDate,
                    convHeure: convocationHeure,
                  }}
                  embedded
                  initialAssocies={bdAssocies}
                  initialGerants={bdGerants}
                  initialOrdreDuJour={ordreDuJourFromMods}
                  lockOrdreDuJour
                  initialSnapshot={convocationSnapshot}
                  onSnapshot={setConvocationSnapshot}
                  lockType
                  hideSeanceDate
                  hideConvDate
                  versioned
                  motif={depositMotif}
                  filenameFor={workflowFilenameFor}
                />
              </div>
            </details>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4" data-testid="mod-step2-saisie">
            <header>
              <h2 className="text-lg font-semibold text-fg">Étape 2 — Saisie des nouvelles valeurs</h2>
              <p className="text-sm text-fg-subtle">
                Pour chaque décision, saisissez uniquement les <strong>nouvelles</strong> valeurs.
                L'<strong>ancienne valeur</strong> est affichée à côté (lecture seule, depuis la BD).
                Les données déjà connues ne sont jamais re-saisies. Pour l'entrée d'un nouvel
                associé, importez sa CIN (OCR) — les champs se pré-remplissent.
              </p>
            </header>

            {/* Identité société — lecture seule (référence, jamais re-saisie). */}
            <div
              className="rounded-xl border border-border bg-bg-overlay p-4"
              data-testid="mod-step2-reference"
            >
              <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                <Building2 className="h-4 w-4 text-accent" /> Société (référence — non modifiable ici)
              </p>
              <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
                {[
                  ['Dénomination', effectiveFiche.denomination ?? dossier?.denomination],
                  ['Forme juridique', formeJuridique],
                  ['Capital social', formatMad(effectiveFiche.capitalSocial ?? dossier?.capitalSocial)],
                  ['Valeur nominale', formatMad(effectiveFiche.valeurNominale)],
                  ['Nombre de parts', effectiveFiche.nombreParts],
                  ['Siège social', effectiveFiche.adresseSiege ?? dossier?.adresseSiege],
                  ['ICE', effectiveFiche.ice ?? dossier?.ice],
                  ['RC', effectiveFiche.rcNumero ?? dossier?.rcNumero],
                  ['Objet social', effectiveFiche.objetSocial],
                ].map(([label, value]) => {
                  const shown = value !== null && value !== undefined && String(value).trim() !== ''
                    ? String(value)
                    : '—';
                  return (
                    <div key={String(label)}>
                      <p className="text-xs text-fg-subtle">{String(label)}</p>
                      <p className="truncate text-sm font-medium text-fg" title={shown}>
                        {shown}
                      </p>
                    </div>
                  );
                })}
              </div>
            </div>

            {resolutions.length === 0 && (
              <p className="rounded-lg border border-amber-200 bg-warning/10 p-3 text-xs text-amber-700">
                Aucune décision sélectionnée — revenez à l'étape 1.
              </p>
            )}

            {resolutions.map((r, i) => {
              const decision = findDecision(r.id);
              const spec = RES_SPECS[r.type];
              const olds = oldValuesFor(r.type, effectiveFiche);
              const targetField = NEW_ASSOCIE_TARGET_FIELD[r.type];
              return (
                <div
                  key={r.id}
                  className="rounded-xl border border-border bg-bg-raised p-4"
                  data-testid={`mod-res-${r.id}`}
                >
                  <div className="mb-3 flex items-start gap-2">
                    <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded bg-violet-600 text-[11px] font-bold text-white">
                      {i + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <h3 className="text-sm font-semibold text-fg">{decision?.label ?? spec?.label ?? r.type}</h3>
                      <p className="mt-0.5 flex flex-wrap items-center gap-1 text-[10px] text-fg-subtle">
                        {decision && (
                          <span className="rounded bg-violet-50 px-1 py-0.5 font-medium text-violet-700">
                            {GROUP_LABELS[decision.group]}
                          </span>
                        )}
                        {decision?.newAssocie && (
                          <span className="rounded bg-emerald-50 px-1 py-0.5 font-medium text-emerald-700">
                            nouvel associé (OCR)
                          </span>
                        )}
                        {decision?.generic && (
                          <span className="rounded bg-amber-100 px-1 py-0.5 font-medium text-amber-700">
                            type générique
                          </span>
                        )}
                      </p>
                    </div>
                  </div>

                  {/* Anciennes valeurs (lecture seule). */}
                  {olds.length > 0 && (
                    <div className="mb-3 grid gap-2 rounded-lg bg-bg-overlay p-3 sm:grid-cols-3">
                      {olds.map((o) => (
                        <div key={o.label}>
                          <p className="text-[10px] uppercase tracking-wide text-fg-subtle">Ancienne valeur</p>
                          <p className="text-xs font-medium text-fg-muted">
                            {o.label} : <span className="text-fg">{o.value}</span>
                          </p>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Objet (facultatif) + champs de la résolution. */}
                  <div className="mb-3">
                    <TextField
                      label="Objet de la résolution (facultatif)"
                      value={r.objet}
                      placeholder={spec?.label}
                      onChange={(e) => setResObjet(r.id, e.target.value)}
                    />
                  </div>
                  <ResolutionFieldsEditor
                    spec={spec}
                    values={r.values}
                    rows={r.rows}
                    personLists={personLists}
                    onField={(key, value) => setResField(r.id, key, value)}
                    onRows={(key, rows) => setResRows(r.id, key, rows)}
                  />

                  {/* Exception — nouvel associé : OCR CIN + pré-remplissage. */}
                  {decision?.newAssocie && (() => {
                    const na = r.newAssocie ?? {};
                    const isMorale = (na.typePersonne ?? 'PHYSIQUE') === 'MORALE';
                    const setNa = (k: string, v: string) => {
                      setResNewAssocieField(r.id, k, v);
                      // Reporte le nom complet dans le champ cible de la résolution (cessionnaire/apporteur).
                      if ((k === 'nom' || k === 'prenom' || k === 'denomination') && targetField) {
                        const next = { ...na, [k]: v };
                        const full = isMorale
                          ? (next.denomination ?? '')
                          : [next.prenom, next.nom].filter(Boolean).join(' ').trim();
                        if (full) setResField(r.id, targetField, full);
                      }
                    };
                    return (
                      <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50/50 p-3" data-testid={`mod-newassocie-form-${r.id}`}>
                        <p className="mb-1 text-xs font-semibold text-emerald-800">
                          Nouvel associé entrant — saisie de la personne (même logique qu'à la création)
                        </p>
                        <p className="mb-3 text-[11px] text-emerald-700">
                          L'extraction CIN (OCR) est <strong>optionnelle</strong> : elle pré-remplit les champs.
                          Vous pouvez aussi tout saisir manuellement.
                        </p>

                        {/* Type de personne */}
                        <div className="mb-3 flex gap-2">
                          {(['PHYSIQUE', 'MORALE'] as const).map((tp) => (
                            <button
                              key={tp}
                              type="button"
                              onClick={() => setNa('typePersonne', tp)}
                              data-testid={`mod-newassocie-type-${tp}-${r.id}`}
                              className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                                (na.typePersonne ?? 'PHYSIQUE') === tp
                                  ? 'border-emerald-500 bg-emerald-100 text-emerald-800'
                                  : 'border-border-hi text-fg hover:border-emerald-400'
                              }`}
                            >
                              {tp === 'PHYSIQUE' ? 'Personne physique' : 'Personne morale'}
                            </button>
                          ))}
                        </div>

                        {!isMorale ? (
                          <>
                            {/* OCR CIN optionnel (physique). */}
                            <div className="mb-3">
                              <IdentityExtractor
                                mode="cin"
                                dossierId={selectedDossierId || ticket?.dossierId}
                                onApply={(vals) => {
                                  const patch: Record<string, string> = {
                                    typePersonne: 'PHYSIQUE',
                                    nom: (vals.nom as string) ?? na.nom ?? '',
                                    prenom: (vals.prenom as string) ?? na.prenom ?? '',
                                    cin: (vals.cin as string) ?? na.cin ?? '',
                                    adresse: (vals.adresse as string) ?? na.adresse ?? '',
                                    nationalite: (vals.nationalite as string) ?? na.nationalite ?? '',
                                    dateNaissance: (vals.date_naissance as string) ?? na.dateNaissance ?? '',
                                    lieuNaissance: (vals.lieu_naissance as string) ?? na.lieuNaissance ?? '',
                                  };
                                  setResNewAssocie(r.id, { ...na, ...patch });
                                  const full = [patch.prenom, patch.nom].filter(Boolean).join(' ').trim();
                                  if (full && targetField) setResField(r.id, targetField, full);
                                }}
                              />
                            </div>
                            {/* Champs éditables (physique). */}
                            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                              <Select
                                label="Civilité"
                                value={na.civilite ?? ''}
                                onChange={(e) => setNa('civilite', e.target.value)}
                                options={[
                                  { value: '', label: '—' },
                                  { value: 'M.', label: 'M.' },
                                  { value: 'Mme', label: 'Mme' },
                                ]}
                              />
                              <TextField label="Prénom" value={na.prenom ?? ''} onChange={(e) => setNa('prenom', e.target.value)} />
                              <TextField label="Nom" value={na.nom ?? ''} onChange={(e) => setNa('nom', e.target.value)} />
                              <TextField label="CIN" value={na.cin ?? ''} onChange={(e) => setNa('cin', e.target.value)} />
                              <TextField label="Nationalité" value={na.nationalite ?? ''} onChange={(e) => setNa('nationalite', e.target.value)} />
                              <TextField label="Date de naissance" type="date" value={na.dateNaissance ?? ''} onChange={(e) => setNa('dateNaissance', e.target.value)} />
                              <TextField label="Lieu de naissance" value={na.lieuNaissance ?? ''} onChange={(e) => setNa('lieuNaissance', e.target.value)} />
                              <div className="sm:col-span-2 lg:col-span-3">
                                <TextField label="Adresse" value={na.adresse ?? ''} onChange={(e) => setNa('adresse', e.target.value)} />
                              </div>
                            </div>
                          </>
                        ) : (
                          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                            <div className="sm:col-span-2 lg:col-span-3">
                              <TextField label="Dénomination" value={na.denomination ?? ''} onChange={(e) => setNa('denomination', e.target.value)} />
                            </div>
                            <TextField label="Forme juridique" value={na.formeEntite ?? ''} onChange={(e) => setNa('formeEntite', e.target.value)} placeholder="Ex : SARL" />
                            <TextField label="RC" value={na.rc ?? ''} onChange={(e) => setNa('rc', e.target.value)} />
                            <TextField label="ICE" value={na.ice ?? ''} onChange={(e) => setNa('ice', e.target.value)} />
                            <div className="sm:col-span-2 lg:col-span-3">
                              <TextField label="Siège" value={na.siege ?? ''} onChange={(e) => setNa('siege', e.target.value)} />
                            </div>
                            <TextField label="Représentant — Prénom" value={na.repPrenom ?? ''} onChange={(e) => setNa('repPrenom', e.target.value)} />
                            <TextField label="Représentant — Nom" value={na.repNom ?? ''} onChange={(e) => setNa('repNom', e.target.value)} />
                            <TextField label="Représentant — CIN" value={na.repCin ?? ''} onChange={(e) => setNa('repCin', e.target.value)} />
                          </div>
                        )}

                        {/* Parts (commun physique / morale). */}
                        <div className="mt-3 grid gap-3 sm:grid-cols-2">
                          <TextField
                            label="Nombre de parts acquises / attribuées"
                            type="number"
                            value={na.nombreParts ?? ''}
                            onChange={(e) => setNa('nombreParts', e.target.value)}
                          />
                        </div>
                      </div>
                    );
                  })()}
                </div>
              );
            })}
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <header className="mb-2">
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-fg">Etape 3 — Generation des documents</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Cliquez « Generer » sur chaque document pour produire la version finale a partir
                des valeurs saisies. Une fois genere, l'apercu s'affiche par bloc avec les actions
                Telecharger / Modifier / Valider. Le document valide est automatiquement depose
                dans la Data Room du dossier (RG transverse).
                {jalRequis && (
                  <span className="ml-1 font-medium text-amber-700">
                    L'annonce JAL est obligatoire pour cette modification (RG-M14).
                  </span>
                )}
              </p>
            </header>

            {/* Phase E1 — Voie directeur : éditeur de résolutions générique (32 types) + PV de
                Modification à résolutions typées. Réutilise le noyau séance (Phase A) et
                l'identité société pré-remplie depuis la BD via dossierId. Les blocs legacy
                ci-dessous (statuts refondus / JAL) restent disponibles (retrait au lot E2). */}
            <section
              data-testid="modification-directeur-voie"
              className="rounded-2xl border border-violet-200 bg-violet-50/40 p-4"
            >
              <div className="mb-3 flex items-center gap-2">
                <Gavel className="h-5 w-5 text-violet-600" />
                <h3 className="text-base font-semibold text-fg">
                  Procès-verbal de modification (AGE) — résolutions saisies à l'étape 2
                </h3>
              </div>
              <ModificationOperationForm
                dossierId={selectedDossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={formeJuridique === 'SARL_AU' ? 'SARL_AU' : 'SARL'}
                societe={{
                  /*
                   * M4 — RÈGLE FIGÉE (décision produit du 2026-08-17).
                   *
                   * Les documents de SÉANCE — PV, convocation, feuille de présence —
                   * portent en en-tête l'ANCIENNE dénomination, celle en vigueur LE JOUR
                   * DE L'AG, y compris lorsque l'assemblée vote précisément son
                   * changement. Motif : la société délibère encore sous ce nom ; la
                   * nouvelle dénomination n'existe qu'À L'ISSUE du vote.
                   *
                   * La nouvelle dénomination apparaît donc uniquement :
                   *   - dans les RÉSOLUTIONS du PV (« … décide d'adopter la dénomination
                   *     NOVA INDUSTRIE MAROC »), ce qui est son premier acte d'existence ;
                   *   - dans les STATUTS REFONDUS et l'ANNONCE LÉGALE, qui décrivent la
                   *     société APRÈS la décision.
                   *
                   * Mise en œuvre : les trois panneaux de séance lisent la MÊME source —
                   * `dossier`, l'état persisté avant la modification. Ils ne peuvent donc
                   * plus diverger entre eux. Garde-fou : `modificationSeanceM4.test.ts`.
                   */
                  denomination: dossier?.denomination ?? ticket?.titre ?? null,
                  capitalChiffres: dossier?.capitalSocial ?? null,
                  siegeSocial: dossier?.adresseSiege ?? null,
                  // `nombreParts` manquait ici alors que la feuille de présence
                  // l'avait : le PV sortait donc sans total de parts.
                  nombreParts:
                    (effectiveFiche.nombreParts as number | string | undefined) ?? null,
                  rcNumero: dossier?.rcNumero ?? null,
                  villeGreffe: dossier?.rcTribunal ?? dossier?.ville ?? null,
                }}
                resolutions={resolutions}
                defaultDate={datePV || undefined}
                defaultType={assembleeNature}
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialOrdreDuJour={ordreDuJourFromMods}
                  lockOrdreDuJour
                initialSnapshot={pvSnapshot}
                onSnapshot={setPvSnapshot}
                lockType
                hideSeanceDate
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
                onReady={setPvValidated}
              />
            </section>

            {/* PREFLIGHT — etape 3 : bloque la generation si des champs requis manquent. */}
            {preflight.blocking.length > 0 && (
              <div
                role="alert"
                data-testid="preflight-blocking"
                className="rounded-xl border-2 border-danger/40 bg-danger/5 p-4"
              >
                <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-danger">
                  <AlertCircle className="h-4 w-4" />
                  Generation bloquee — completez les champs obligatoires :
                </div>
                <ul className="space-y-2">
                  {preflight.blocking.map((b) => (
                    <li key={b.typeId} className="rounded-lg border border-danger/30 bg-bg-raised p-3">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-fg">{b.label}</p>
                          <ul className="mt-1 list-disc pl-5 text-xs text-fg-muted">
                            {b.missing.map((m) => (
                              <li key={m.name} data-testid={`preflight-missing-${b.typeId}-${m.name}`}>
                                {m.label}
                              </li>
                            ))}
                          </ul>
                        </div>
                        <button
                          type="button"
                          onClick={() => navigateToStep(2)}
                          data-testid={`preflight-goto-step2-${b.typeId}`}
                          className="flex-shrink-0 rounded-lg bg-accent px-3 py-1.5 text-xs font-medium text-bg-raised hover:bg-accent-hover"
                        >
                          Aller a l'etape 2
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {preflight.warnings.length > 0 && (
              <div
                role="status"
                data-testid="preflight-warnings"
                className="rounded-xl border border-warning/40 bg-warning/5 p-4"
              >
                <div className="mb-1 flex items-center gap-2 text-sm font-semibold text-warning">
                  <AlertTriangle className="h-4 w-4" />
                  Types en "annexe a fournir" — generation autorisee mais l'acte
                  separe devra etre depose manuellement :
                </div>
                <ul className="ml-6 list-disc text-xs text-fg-muted">
                  {preflight.warnings.map((w) => (
                    <li key={w.typeId}>{w.label}</li>
                  ))}
                </ul>
              </div>
            )}

            {loadingTemplates && (
              <div className="rounded-xl border border-border bg-bg-raised p-6">
                <div className="flex items-center gap-3 text-sm text-fg-subtle">
                  <Loader className="h-4 w-4 animate-spin" /> Chargement des documents disponibles…
                </div>
              </div>
            )}
            {loadTemplatesError && !loadingTemplates && (
              <div className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
                <AlertTriangle className="mr-2 inline h-4 w-4" />
                {loadTemplatesError}
              </div>
            )}
            {!loadingTemplates && templates.length === 0 && !loadTemplatesError && (
              <div className="rounded-xl border border-border bg-bg-raised p-6 text-sm text-fg-subtle">
                Aucun template enregistre pour MODIFICATION — verifiez le manifest backend.
              </div>
            )}

            {/* PREFLIGHT STATUT REFONDU (2026-06-24) — société importée scan-only :
                on force la saisie des SEULS champs structurés manquants nécessaires
                au statut complet refondu, avec le SCAN d'origine affiché EN AIDE. */}
            {refonteBlocked && (
              <div
                role="alert"
                data-testid="refonte-preflight"
                className="rounded-xl border-2 border-amber-300 bg-amber-50 p-4"
              >
                <div className="mb-1 flex items-center gap-2 text-sm font-semibold text-amber-800">
                  <AlertTriangle className="h-4 w-4" />
                  Statut refondu : complétez l'état structuré de la société
                </div>
                <p className="mb-3 text-xs text-amber-700">
                  Le statut complet est généré depuis les données structurées (jamais depuis le
                  scan). Renseignez les champs manquants ci-dessous — affichez le statut d'origine
                  pour les lire. La génération du statut refondu reste bloquée tant que ces champs
                  ne sont pas saisis.
                </p>
                <button
                  type="button"
                  onClick={openOriginalStatutsScan}
                  disabled={scanLoading}
                  data-testid="refonte-open-scan"
                  className="mb-3 inline-flex items-center gap-1.5 rounded-lg border border-amber-400 bg-bg-raised px-3 py-1.5 text-xs font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-60"
                >
                  <Eye className="h-3.5 w-3.5" />
                  {scanLoading ? 'Chargement…' : "Afficher le statut d'origine (scan)"}
                </button>
                {scanError && (
                  <p className="mb-2 text-xs text-danger" role="alert">{scanError}</p>
                )}
                <div className="grid gap-3 md:grid-cols-2">
                  {refonteMissing.map((fld) => (
                    <div key={fld.key} className={fld.type === 'textarea' ? 'md:col-span-2' : ''}>
                      <label
                        htmlFor={`refonte-${fld.key}`}
                        className="mb-1 block text-xs font-medium text-amber-900"
                      >
                        {fld.label} <span className="text-danger">*</span>
                      </label>
                      {fld.type === 'textarea' ? (
                        <textarea
                          id={`refonte-${fld.key}`}
                          data-testid={`refonte-field-${fld.key}`}
                          rows={3}
                          value={ficheOverrides[fld.key] ?? ''}
                          onChange={(e) => setFicheOverride(fld.key, e.target.value)}
                          className="w-full rounded-lg border border-amber-300 bg-bg-raised px-3 py-2 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-200"
                        />
                      ) : (
                        <input
                          id={`refonte-${fld.key}`}
                          data-testid={`refonte-field-${fld.key}`}
                          type={fld.type}
                          value={ficheOverrides[fld.key] ?? ''}
                          onChange={(e) => setFicheOverride(fld.key, e.target.value)}
                          className="w-full rounded-lg border border-amber-300 bg-bg-raised px-3 py-2 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-200"
                        />
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* RG transverse 2026-06-05 — bloc unitaire par template :
                Generer -> Apercu / Telecharger / Modifier / Valider.
                Suppression du bloc "Documents disponibles" en bas (etait redondant). */}
            {/* Phase 2 — Dépôt légal (annonce) : n°/date attribués par le greffe APRÈS dépôt.
                Facultatifs — l'annonce se génère avec ces champs vides si non renseignés. */}
            {hasPubliableDecision && (
              <div
                className="rounded-xl border border-border bg-bg-raised p-4"
                data-testid="mod-depot-legal"
              >
                <p className="mb-1 flex items-center gap-2 text-sm font-semibold text-fg">
                  <Megaphone className="h-4 w-4 text-accent" /> Dépôt légal de l'annonce (facultatif)
                </p>
                <p className="mb-3 text-xs text-fg-subtle">
                  Le numéro et la date de dépôt sont attribués par le greffe <strong>après</strong> le
                  dépôt. Laissez vide pour générer l'annonce sans ces mentions (à compléter plus tard).
                </p>
                <div className="grid gap-3 md:grid-cols-2">
                  <TextField
                    label="N° de dépôt légal"
                    value={depotLegalNumero}
                    onChange={(e) => setDepotLegalNumero(e.target.value)}
                    placeholder="ex : 954333"
                  />
                  <TextField
                    label="Date de dépôt légal"
                    type="date"
                    value={depotLegalDate}
                    onChange={(e) => setDepotLegalDate(e.target.value)}
                  />
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              {visibleTemplates.map((tpl) => (
                <DocumentBlock
                  key={tpl.code}
                  tpl={tpl}
                  state={docs[tpl.code] ?? freshDocState()}
                  // Statut refondu : bloqué tant que l'état structuré est incomplet.
                  canGenerate={
                    tpl.code.includes('REFONDUS')
                      ? canGenerate && refonteMissing.length === 0
                      : canGenerate
                  }
                  onGenerate={() => generateOne(tpl)}
                  onDownload={() => downloadOne(tpl)}
                  onRegenerate={() => generateOne(tpl)}
                  onValidate={() => validateOne(tpl)}
                  onTogglePreview={() => updateDoc(tpl.code, { previewOpen: !docs[tpl.code]?.previewOpen })}
                  onEdited={(blob, filename) =>
                    updateDoc(tpl.code, {
                      blob,
                      filename,
                      validated: false,
                      depositedToDataroom: false,
                    })
                  }
                />
              ))}
            </div>

            {/* Phase 1 — la Convocation est désormais proposée à l'étape 1 (juste après
                la sélection). Les documents optionnels restants (Incident / Feuille) demeurent ici. */}
            {/*
              Fix M7 (2026-08-16) — pas d'assemblée, donc pas de documents d'assemblée.
              En SARL AU les décisions sont prises par l'associé unique : ni quorum à
              constater, ni feuille de présence multi-signataires à faire émarger.
              Ces panneaux étaient pourtant proposés, et les PV produits n'avaient
              aucune portée. On les masque quand la forme est SARL AU.
            */}
            {!(isAU) && (
              <IncidentSeancePanel
                dossierId={selectedDossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={{
                  denomination: dossier?.denomination ?? ticket.titre,
                  capitalChiffres: dossier?.capitalSocial ?? null,
                  siegeSocial: dossier?.adresseSiege ?? null,
                  nombreParts:
                    (effectiveFiche.nombreParts as number | string | undefined) ??
                    null,
                  rcNumero: dossier?.rcNumero ?? null,
                  villeGreffe: dossier?.rcTribunal ?? dossier?.ville ?? null,
                }}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: assembleeNature,
                  date: datePV,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            )}
            {!(isAU) && (
              <FeuillePresencePanel
                dossierId={selectedDossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={{
                  denomination: dossier?.denomination ?? ticket.titre,
                  capitalChiffres: dossier?.capitalSocial ?? null,
                  siegeSocial: dossier?.adresseSiege ?? null,
                  nombreParts:
                    (effectiveFiche.nombreParts as number | string | undefined) ??
                    null,
                  rcNumero: dossier?.rcNumero ?? null,
                  villeGreffe: dossier?.rcTribunal ?? dossier?.ville ?? null,
                }}
                // La convocation manquait ici : la feuille de presence la reaffichait
                // vide alors qu'elle est saisie a l'etape 1.
                defaultSeance={{
                  type: assembleeNature,
                  date: datePV,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialOrdreDuJour={ordreDuJourFromMods}
                lockOrdreDuJour
                initialSnapshot={feuilleSnapshot}
                onSnapshot={setFeuilleSnapshot}
                lockType
                hideSeanceDate
                hideConvDate
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            )}
          </div>
        )}

        {/* ÉTAPE 4 — Pièces jointes (OPTIONNELLE, jamais bloquante). Auto-dépôt Data
            Room versionné. L'employé peut ne rien déposer et passer à la synthèse. */}
        {step === 4 && (
          <div className="space-y-4" data-testid="mod-step4-pieces">
            <header>
              <h2 className="text-lg font-semibold text-fg">Étape 4 — Pièces jointes (optionnelle)</h2>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> des documents générés
                (statuts, PV, annonce…). Cette étape est <strong>entièrement facultative</strong> :
                vous pouvez la laisser vide et poursuivre vers la synthèse. Chaque dépôt est
                versionné dans la Data Room de la société.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={selectedDossierId || ticket?.dossierId}
              ticketId={ticket?.id}
              denomination={targetDenomination}
              forme={formeLabel(formeJuridique)}
              motif={depositMotif}
              onDeposited={(entry) =>
                setPiecesJointes((prev) => {
                  const next = prev.filter((p) => p.id !== entry.id);
                  next.push(entry);
                  return next;
                })
              }
            />
            {piecesJointes.length === 0 && (
              <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-subtle">
                Aucune pièce jointe déposée pour le moment — cette étape est facultative.
              </p>
            )}
          </div>
        )}

        {step === 5 && (
          <div className="space-y-4" data-testid="mod-synthese">
            <div className="rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 p-6 text-bg-raised">
              <CheckCircle className="mb-2 h-8 w-8" />
              <h2 className="text-2xl font-bold">Synthese — Modification</h2>
              <p className="text-bg text-sm">
                {selected.length} modification{selected.length > 1 ? 's' : ''} enregistree{selected.length > 1 ? 's' : ''} sur {dossier?.denomination || ticket.titre}.
              </p>
            </div>

            {/* 1) IDENTITE SOCIETE ─────────────────────────────────────── */}
            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-societe">
              <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                <Building2 className="h-4 w-4 text-accent" /> Societe concernee
              </p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2 lg:grid-cols-4">
                <div>
                  <p className="text-xs text-fg-subtle">Denomination</p>
                  <p className="text-sm font-medium text-fg">{dossier?.denomination || ticket.titre || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Forme juridique</p>
                  <p className="text-sm font-medium text-fg">{formeJuridique || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">ICE</p>
                  <p className="text-sm font-medium text-fg">{dossier?.ice || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Capital social</p>
                  <p className="text-sm font-medium text-fg">{formatMad(dossier?.capitalSocial)}</p>
                </div>
              </div>
              <div className="mt-3 grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-fg-subtle">Date PV / Decision</p>
                  <p className="text-sm font-medium text-fg">{datePV || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Type d'assemblee</p>
                  <p className="text-sm font-medium text-fg">
                    {isAU ? "Decision de l'associe unique" : 'Assemblee generale'}
                    {' '}({assembleeNature}){' — '}
                    {DECISION_TYPES.find((d) => d.value === decisionType)?.label ?? decisionType}
                  </p>
                </div>
              </div>
            </div>

            {/* 2) DÉTAIL DES MODIFICATIONS (avant → après, par résolution) ── */}
            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-changements">
              <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                <ArrowRight className="h-4 w-4 text-accent" /> Détail des modifications retenues
              </p>
              {resolutionRecap.length === 0 ? (
                <p className="rounded-lg bg-bg-overlay p-3 text-xs text-fg-subtle">
                  Aucune modification saisie.
                </p>
              ) : (
                <ul className="space-y-3">
                  {resolutionRecap.map((r, i) => (
                    <li key={r.id} className="rounded-lg border border-border bg-bg-overlay p-3">
                      <p className="mb-1 text-sm font-semibold text-fg">
                        {i + 1}. {r.label}
                      </p>
                      {r.olds.length > 0 && (
                        <p className="text-xs text-fg-muted">
                          {r.olds.map((o) => `${o.label} : ${o.value}`).join(' · ')}
                        </p>
                      )}
                      {r.newVals.length > 0 ? (
                        <div className="mt-1 flex flex-wrap gap-1.5">
                          {r.newVals.map((v) => (
                            <span
                              key={v.label}
                              className="rounded bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent"
                            >
                              {v.label} : {v.value}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <p className="mt-1 text-xs text-fg-subtle">Aucune valeur saisie.</p>
                      )}
                      {r.newAssocie && (r.newAssocie.nom || r.newAssocie.prenom) && (
                        <p className="mt-1 text-xs text-emerald-700">
                          Nouvel associé : {[r.newAssocie.prenom, r.newAssocie.nom].filter(Boolean).join(' ')}
                          {r.newAssocie.cin ? ` (CIN ${r.newAssocie.cin})` : ''}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* 3) ARTICLES IMPACTES + 4) EXIGENCES ──────────────────────── */}
            <div className="grid gap-4 lg:grid-cols-2">
              <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-articles">
                <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                  <FileText className="h-4 w-4 text-accent" /> Articles des statuts impactes
                </p>
                {articlesImpactes.length === 0 ? (
                  <p className="text-xs text-fg-subtle">Aucun article specifique detecte.</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {articlesImpactes.map((a) => (
                      <span key={a} className="rounded-lg bg-violet-50 px-2 py-1 text-xs font-medium text-violet-700">
                        {a}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-exigences">
                <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                  <AlertTriangle className="h-4 w-4 text-accent" /> Exigences detectees
                </p>
                {!jalRequis && !cacRequis && !formeChangeRequis ? (
                  <p className="text-xs text-fg-subtle">Aucune exigence particuliere.</p>
                ) : (
                  <div className="flex flex-col gap-2">
                    {jalRequis && (
                      <span className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">
                        <Megaphone className="h-4 w-4" /> Publication au JAL obligatoire (RG-M13/M14)
                      </span>
                    )}
                    {cacRequis && (
                      <span className="flex items-center gap-2 rounded-lg bg-violet-50 px-3 py-2 text-xs font-medium text-violet-800">
                        <Gavel className="h-4 w-4" /> Commissaire aux apports obligatoire (RG-M10)
                      </span>
                    )}
                    {formeChangeRequis && (
                      <span className="flex items-center gap-2 rounded-lg bg-rose-50 px-3 py-2 text-xs font-medium text-rose-800">
                        <AlertTriangle className="h-4 w-4" /> Passage SARL_AU → SARL (RG-M11)
                      </span>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* 5) DOCUMENTS GENERES + ETAT DE VALIDATION ────────────────── */}
            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-documents">
              <div className="mb-3 flex items-center justify-between gap-2">
                <p className="flex items-center gap-2 text-sm font-semibold text-fg">
                  <FileText className="h-4 w-4 text-accent" /> Documents generes
                </p>
                <button
                  type="button"
                  onClick={() => navigate('/data-rooms')}
                  className="flex items-center gap-1.5 rounded-lg border border-border bg-bg-raised px-3 py-1.5 text-xs font-medium text-fg hover:border-accent"
                  data-testid="synthese-dataroom-link"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> Ouvrir la Data Room
                </button>
              </div>
              {synthesisDocs.length === 0 ? (
                <p className="rounded-lg bg-bg-overlay p-3 text-xs text-fg-subtle">
                  Aucun document genere a l'etape 3.
                </p>
              ) : (
                <ul className="space-y-2">
                  {synthesisDocs.map((d) => (
                    <li
                      key={d.code}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border bg-bg-overlay p-3"
                      data-testid={`synthese-doc-${d.code}`}
                    >
                      <div className="flex min-w-0 items-center gap-2">
                        <FileText className="h-4 w-4 flex-shrink-0 text-accent" />
                        <span className="truncate text-sm font-medium text-fg">{d.label}</span>
                      </div>
                      <div className="flex flex-shrink-0 items-center gap-2">
                        {d.validated ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
                            <CheckCircle className="h-3 w-3" /> Valide
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-full bg-warning/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-warning">
                            <AlertCircle className="h-3 w-3" /> Non valide
                          </span>
                        )}
                        {d.deposited && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-accent">
                            Data Room
                          </span>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* 5 bis) PIÈCES JOINTES DÉPOSÉES ───────────────────────────── */}
            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-pieces">
              <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg">
                <FileText className="h-4 w-4 text-accent" /> Pièces jointes déposées
              </p>
              {piecesJointes.length === 0 ? (
                <p className="rounded-lg bg-bg-overlay p-3 text-xs text-fg-subtle">
                  Aucune pièce jointe déposée (facultatif).
                </p>
              ) : (
                <ul className="space-y-2">
                  {piecesJointes.map((p) => (
                    <li
                      key={p.id}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border bg-bg-overlay p-3"
                    >
                      <span className="min-w-0 truncate text-sm font-medium text-fg">
                        {p.label} <span className="text-xs text-fg-subtle">— {p.filename}</span>
                      </span>
                      <span className="inline-flex flex-shrink-0 items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-accent">
                        Data Room{p.version ? ` · v${p.version}` : ''}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* 6) CONFIRMATION FINALE (inchangee fonctionnellement) ──────── */}
            {!isTerminated && (
              <div className="flex gap-2">
                <Button onClick={handleCloturer}>Cloturer le workflow</Button>
              </div>
            )}
            {isTerminated && (
              <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800">
                Ticket cloture — retour aux tickets.
                <Button className="ml-3" onClick={() => navigate('/tickets')}>
                  Retour
                </Button>
              </div>
            )}
          </div>
        )}

        {/* 2026-06-05 — bloc "Documents disponibles" (GenerateDocumentPanel)
            SUPPRIME : la generation est entierement prise en charge par
            l'etape 3 sous forme de blocs unitaires (Generer / Apercu /
            Telecharger / Modifier / Valider + depot auto dataroom).
            Le payload buildPayload() reste utilise par generateOne(). */}
      </WorkflowShell>

      {showCancel && ticket && (
        <CancelTicketDialog
          ticket={ticket}
          onClose={() => setShowCancel(false)}
          onConfirm={async (comment) => {
            await ticketService.transition(ticket.id, { target: 'ANNULE', comment });
            navigate('/tickets');
          }}
        />
      )}

      {/* Scan du statut d'origine — aperçu EN AIDE à la lecture (sans OCR ni édition). */}
      <PdfPreviewModal
        open={scanOpen}
        documentId={scanDocId}
        filename="Statuts d'origine"
        canDownload={false}
        canPrint={false}
        onClose={() => setScanOpen(false)}
      />
    </>
  );
}

function Loader2() {
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}

// ============================================================================
// DocumentBlock — bloc unitaire par template, pattern aligne sur Step7Generation
// ============================================================================
interface DocumentBlockProps {
  tpl: TemplateInfo;
  state: DocState;
  canGenerate: boolean;
  onGenerate: () => void;
  onDownload: () => void;
  onRegenerate: () => void;
  onValidate: () => void;
  onTogglePreview: () => void;
  /** 2026-08-12 — Édition WYSIWYG (aligné sur « Éditer » de Step7 Création). */
  onEdited: (blob: Blob, filename: string) => void;
}

function DocumentBlock({ tpl, state, canGenerate, onGenerate, onDownload, onRegenerate, onValidate, onTogglePreview, onEdited }: DocumentBlockProps) {
  const title = tpl.documentKind || tpl.code;
  const [editing, setEditing] = useState(false);
  return (
    <div
      className={`overflow-hidden rounded-xl border bg-bg-raised shadow-sm ${
        state.validated ? 'border-success' : 'border-border'
      }`}
      data-testid={`mod-doc-block-${tpl.code}`}
    >
      <div className="flex items-start justify-between gap-2 border-b border-border bg-bg-overlay px-5 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 flex-shrink-0 text-accent" />
            <h4 className="truncate text-sm font-semibold text-fg" title={tpl.code}>
              {title}
            </h4>
          </div>
          <p className="mt-0.5 truncate text-[11px] text-fg-subtle">{tpl.code}</p>
        </div>
        {state.validated && (
          <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
            <CheckCircle className="h-3 w-3" /> Valide
            {state.depositedToDataroom && <span className="text-[9px] font-normal opacity-80">· Dataroom</span>}
          </span>
        )}
      </div>

      <div className="space-y-3 p-5">
        {state.error && (
          <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 p-2 text-xs text-danger" role="alert">
            <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
            <span className="flex-1">{state.error}</span>
          </div>
        )}

        {!state.generated ? (
          <button
            type="button"
            onClick={onGenerate}
            disabled={state.generating || !canGenerate}
            data-testid={`generate-btn-${tpl.code}`}
            title={!canGenerate ? "Completez les champs obligatoires (preflight)" : undefined}
            className={`flex h-10 w-full items-center justify-center gap-2 rounded-lg text-sm font-medium transition ${
              state.generating || !canGenerate
                ? 'cursor-not-allowed bg-border text-fg-subtle'
                : 'bg-accent text-bg-raised hover:bg-accent-hover'
            }`}
          >
            {state.generating ? (
              <>
                <Loader className="h-4 w-4 animate-spin" /> Generation…
              </>
            ) : (
              <>
                <Sparkles className="h-4 w-4" /> Generer
              </>
            )}
          </button>
        ) : (
          <>
            <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/5 p-2 text-xs">
              <CheckCircle className="h-4 w-4 text-success" />
              <span className="flex-1 truncate text-fg">{state.filename ?? 'Document genere'}</span>
            </div>

            {/* 2026-08-12 — Aperçu FIDÈLE (docx-preview) inline + plein écran,
                remplace l'ancien placeholder texte. Après navigation le blob
                n'est plus en mémoire → régénérer pour réafficher l'aperçu. */}
            {state.previewOpen && state.blob && (
              <GeneratedDocPreview
                blob={state.blob}
                title={title}
                filename={state.filename ?? title}
              />
            )}
            {state.previewOpen && !state.blob && (
              <div className="rounded-lg border border-border bg-bg-overlay p-3 text-[11px] text-fg-subtle">
                <Eye className="mr-1 inline h-3.5 w-3.5 align-text-bottom" />
                Aperçu indisponible : régénérez le document pour l'afficher.
              </div>
            )}

            {/* 2026-08-12 — Jeu de boutons aligné sur Step7 Création :
                Aperçu / Régénérer / Éditer / .docx / Valider. */}
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={onTogglePreview}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
              >
                <Eye className="h-3.5 w-3.5" />
                {state.previewOpen ? 'Masquer apercu' : 'Aperçu'}
              </button>
              <button
                type="button"
                onClick={onRegenerate}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
                title="Régénérer le document après mise à jour des valeurs amont"
              >
                <RefreshCcw className="h-3.5 w-3.5" /> Régénérer
              </button>
              <button
                type="button"
                onClick={() => setEditing(true)}
                disabled={!state.blob}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-accent/30 bg-accent/5 text-xs font-medium text-accent hover:bg-accent/10 disabled:opacity-60"
                title="Éditer le document (style Word)"
              >
                <Pencil className="h-3.5 w-3.5" /> Éditer
              </button>
              <button
                type="button"
                onClick={onDownload}
                className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent"
              >
                <Download className="h-3.5 w-3.5" /> .docx
              </button>
              {!state.validated ? (
                <button
                  type="button"
                  onClick={onValidate}
                  data-testid={`validate-btn-${tpl.code}`}
                  className="col-span-2 flex h-9 items-center justify-center gap-1.5 rounded-lg bg-success text-xs font-semibold text-bg-raised transition hover:bg-success/85"
                >
                  <CheckCircle className="h-3.5 w-3.5" /> Valider
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onRegenerate}
                  className="col-span-2 flex h-9 items-center justify-center gap-1.5 rounded-lg border border-success bg-success/10 text-xs text-success"
                >
                  <RefreshCcw className="h-3.5 w-3.5" /> Régénérer
                </button>
              )}
            </div>

            {editing && state.blob && (
              <DocumentEditModal
                blob={state.blob}
                title={title}
                filename={state.filename ?? title}
                onClose={() => setEditing(false)}
                onSaved={(blob, filename) => onEdited(blob, filename)}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}

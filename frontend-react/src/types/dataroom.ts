export type DossierStatut = 'EN_COURS' | 'ACTIF' | 'CLOTURE' | 'ARCHIVE';

export type DocumentType =
  | 'STATUTS'
  | 'PV_AGE'
  | 'PV_AGO'
  | 'PV_MODIFICATION'
  | 'PV_DISSOLUTION'
  | 'PV_LIQUIDATION'
  | 'ACTE_NOMINATION'
  | 'CONTRAT_BAIL'
  | 'CNIE_GERANT'
  | 'ANNONCE_JAL'
  | 'RC'
  | 'ICE'
  | 'TP'
  | 'CNSS'
  | 'APOSTILLE'
  // 2026-06-16 — Types d'archivage d'identité (DataroomIdentityArchiver).
  // Alignés avec la migration V16 (CHECK constraint) et avec
  // IdentityExtractionService.archiveDocumentType côté Java.
  | 'CIN_NOUVELLE'
  | 'CIN_ANCIENNE'
  | 'CN'
  // 2026-08-16 — Documents de SÉANCE (migration V23). Ils tombaient tous en
  // 'AUTRE' faute de type dédié : invisibles dans les filtres, et surtout
  // DÉDUPLIQUÉS entre deux séances d'un même dossier (même type + même titre
  // « AUTRE » ⇒ second dépôt silencieusement ignoré). Défauts M3 / L2 / L3.
  | 'CONVOCATION'
  | 'FEUILLE_PRESENCE'
  | 'RAPPORT_GESTION'
  | 'RAPPORT_LIQUIDATION'
  | 'AUTRE';

export type CategorieComptable =
  | 'ACHATS'
  | 'VENTES'
  | 'BANQUE'
  | 'CAISSE'
  | 'NDF'
  | 'LA_PAIE'
  // Prompt G (2026-06-23) — fourre-tout pour les imports d'anciens dossiers.
  | 'AUTRE';

export type DemandeStatut = 'NON_TRAITEE' | 'EN_COURS' | 'TRAITEE';

export interface DossierBrief {
  id: string;
  raisonSociale: string;
  formeJuridique: string | null;
  ice: string | null;
  ville: string | null;
  statut: DossierStatut | string;
  /**
   * Fix 2026-06-08 — Etat d'acces du dataroom : 'ACTIVE' | 'SUSPENDED'.
   * Sert au classement de la liste entre onglets "Actifs" / "Suspendus".
   */
  accessStatus?: 'ACTIVE' | 'SUSPENDED' | string;
  /**
   * Lot W2 (2026-07-04) — date d'effet de la dissolution (ISO AAAA-MM-JJ) ou
   * null. Posee par le workflow DISSOLUTION. Sert au badge « delai 16 j »
   * (RG-LI03) et au pre-remplissage de l'etape LIQUIDATION.
   */
  dateDissolution?: string | null;
  /**
   * Lot DIVERS §C (2026-08-13) — 'MAROCAINE' (défaut) | 'ETRANGERE'.
   * Une société mère étrangère est un dossier à part entière (donc porteuse de sa
   * propre Data Room), créé par le workflow SUCCURSALE_ETR. Permet de la
   * SÉLECTIONNER au lieu de la re-saisir, et d'exclure ces dossiers des workflows
   * de droit marocain (dissolution, modification…).
   * Absent = 'MAROCAINE' (backend non redéployé).
   */
  origine?: 'MAROCAINE' | 'ETRANGERE' | string | null;
  /** Pays du siège de la société mère étrangère (null sinon). */
  pays?: string | null;
  /**
   * Forme juridique RÉELLE du pays d'origine (Ltd, GmbH, BV, Inc…).
   * Une société étrangère n'est PAS une SARL : `formeJuridique` vaut alors
   * `'ETRANGERE'`, et c'est cette valeur-ci qu'il faut afficher.
   * Null pour un dossier marocain.
   */
  formeJuridiqueOrigine?: string | null;
}

/**
 * Forme juridique à AFFICHER pour un dossier : la forme réelle du pays d'origine
 * quand la société est étrangère, la forme marocaine sinon.
 */
export function displayFormeJuridique(
  d: Pick<DossierBrief, 'formeJuridique' | 'formeJuridiqueOrigine'>,
): string | null {
  if (d.formeJuridique === 'ETRANGERE') {
    return d.formeJuridiqueOrigine?.trim() || 'Société étrangère';
  }
  return d.formeJuridique;
}

export interface DocumentSummary {
  id: string;
  dossierId: string;
  ticketId: string | null;
  documentType: DocumentType | string;
  title: string;
  version: number;
  current: boolean;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string;
  replacedAt: string | null;
  /** Sprint 2026-06-23 — raison du remplacement / de la création de version. */
  motif?: string | null;
}

export interface TicketHistoryEntry {
  ticketId: string;
  reference: string;
  titre: string;
  type: string;
  clotureAt: string | null;
  description: string | null;
  replacedDocuments: DocumentSummary[];
  generatedDocuments: DocumentSummary[];
}

export interface DossierJuridiqueView {
  dossierId: string;
  raisonSociale: string;
  formeJuridique: string | null;
  ice: string | null;
  rcNumero: string | null;
  // Fiche client (2026-07-14) — bloc d'identite complet + statut, pour
  // pre-remplir le formulaire « Identifiants » et calculer le preflight.
  rcTribunal: string | null;
  identifiantFiscal: string | null;
  taxeProfessionnelle: string | null;
  cnss: string | null;
  adresseSiege: string | null;
  ville: string | null;
  capitalSocialMad: number | null;
  dateConstitution: string | null;
  statut: string | null;
  documentsEnVigueur: DocumentSummary[];
  historiqueOperations: TicketHistoryEntry[];
}

/**
 * Fiche client (2026-07-14) — payload d'edition des identifiants de la societe
 * (envoye en PATCH a ticket-service via /api/v1/dossiers/{id}/identifiants).
 * Tous les champs sont optionnels (formulaire de completion).
 */
export interface UpdateIdentifiantsPayload {
  ice?: string | null;
  rcNumero?: string | null;
  rcTribunal?: string | null;
  identifiantFiscal?: string | null;
  taxeProfessionnelle?: string | null;
  cnss?: string | null;
  adresseSiege?: string | null;
  ville?: string | null;
  capitalSocialMad?: number | null;
  dateConstitution?: string | null;
}

export interface ComptableTotal {
  categorie: CategorieComptable | string;
  total: number;
}

export interface DossierComptableView {
  dossierId: string;
  annees: number[];
  anneeCourante: number;
  totauxParCategorie: ComptableTotal[];
}

export interface ComptableDocumentSummary {
  id: string;
  annee: number;
  categorie: CategorieComptable | string;
  title: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string;
}

// Lot V -- Espace « Depots » client (depot libre, sans categorie)
export interface DepotSummary {
  id: string;
  title: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

export interface DemandeSummary {
  id: string;
  dossierId: string;
  soumisPar: string | null;
  sujet: string;
  description: string | null;
  statut: DemandeStatut;
  ticketId: string | null;
  prisEnChargePar: string | null;
  noteInterne: string | null;
  traiteAt: string | null;
  createdAt: string;
}

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  STATUTS: 'Statuts',
  PV_AGE: 'PV AGE',
  PV_AGO: 'PV AGO',
  PV_MODIFICATION: 'PV Modification',
  PV_DISSOLUTION: 'PV Dissolution',
  PV_LIQUIDATION: 'PV Liquidation',
  ACTE_NOMINATION: 'Acte de nomination',
  CONTRAT_BAIL: 'Contrat de bail',
  CNIE_GERANT: 'CNIE Gerant',
  ANNONCE_JAL: 'Annonce JAL',
  RC: 'Registre de commerce',
  ICE: 'Identifiant ICE',
  TP: 'Taxe professionnelle',
  CNSS: 'CNSS',
  APOSTILLE: 'Apostille',
  // 2026-06-16 — Types d'archivage d'identité (V16 + DataroomIdentityArchiver).
  CIN_NOUVELLE: 'CIN (nouvelle)',
  CIN_ANCIENNE: 'CIN (ancienne)',
  CN: 'Certificat Negatif',
  // 2026-08-16 — Documents de séance (V23).
  CONVOCATION: 'Convocation',
  FEUILLE_PRESENCE: 'Feuille de presence',
  RAPPORT_GESTION: 'Rapport de gestion',
  RAPPORT_LIQUIDATION: 'Rapport de liquidation',
  AUTRE: 'Autre',
};

/**
 * Ordre d'affichage des sections « Documents en vigueur » (fix DR3, 2026-08-16).
 *
 * La vue était une liste PLATE où le type n'était qu'un libellé en ligne : sur un
 * dossier ayant vécu plusieurs opérations, retrouver « les statuts » demandait de
 * parcourir tout l'historique. On regroupe donc par famille, du plus structurant
 * au plus accessoire. Tout type absent de cette liste retombe dans « Autres ».
 */
export const DOCUMENT_TYPE_ORDER: DocumentType[] = [
  'STATUTS',
  'PV_AGE',
  'PV_AGO',
  'PV_MODIFICATION',
  'PV_DISSOLUTION',
  'PV_LIQUIDATION',
  'CONVOCATION',
  'FEUILLE_PRESENCE',
  'ANNONCE_JAL',
  'ACTE_NOMINATION',
  'RAPPORT_GESTION',
  'RAPPORT_LIQUIDATION',
  'CONTRAT_BAIL',
  'CNIE_GERANT',
  'CIN_NOUVELLE',
  'CIN_ANCIENNE',
  'CN',
  'RC',
  'ICE',
  'TP',
  'CNSS',
  'APOSTILLE',
  'AUTRE',
];

export const CATEGORIE_COMPTABLE_LABELS: Record<CategorieComptable, string> = {
  ACHATS: 'Achats',
  VENTES: 'Ventes',
  BANQUE: 'Banque',
  CAISSE: 'Caisse',
  NDF: 'Notes de frais',
  LA_PAIE: 'La paie',
  AUTRE: 'Autre',
};

export const CATEGORIE_COMPTABLE_ORDER: CategorieComptable[] = [
  'ACHATS',
  'VENTES',
  'BANQUE',
  'CAISSE',
  'NDF',
  'LA_PAIE',
  'AUTRE',
];

export const DEMANDE_STATUT_LABELS: Record<DemandeStatut, string> = {
  NON_TRAITEE: 'Non traitee',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitee',
};

// Lot AG — « Requetes au client » (direction employe -> client), machine a etats.
export type DemandeDirection = 'CLIENT_TO_EMPLOYE' | 'EMPLOYE_TO_CLIENT';
export type TypeRequete = 'PIECE' | 'INFO' | 'SIGNATURE';
export type RequeteStatut = 'OUVERTE' | 'REPONDUE' | 'CLOTUREE' | 'A_COMPLETER';

/** Meme forme JSON que DemandeSummary, mais typee pour la direction EMPLOYE_TO_CLIENT. */
export interface RequeteSummary {
  id: string;
  dossierId: string;
  soumisPar: string | null;
  sujet: string;
  description: string | null;
  statut: RequeteStatut;
  ticketId: string | null;
  prisEnChargePar: string | null;
  noteInterne: string | null;
  traiteAt: string | null;
  createdAt: string;
  direction: DemandeDirection;
  typeRequete: TypeRequete | null;
  reponduAt: string | null;
  clotureAt: string | null;
  noteClient: string | null;
}

export const REQUETE_STATUT_LABELS: Record<RequeteStatut, string> = {
  OUVERTE: 'À faire',
  A_COMPLETER: 'À compléter',
  REPONDUE: 'En attente de validation',
  CLOTUREE: 'Terminé',
};

export const TYPE_REQUETE_LABELS: Record<TypeRequete, string> = {
  PIECE: 'Pièce à fournir',
  INFO: 'Information',
  SIGNATURE: 'Signature',
};

/**
 * Vue SUPERVISEUR enrichie (workspace-wide) d'une demande OU requete : le nom du
 * dataroom (`raisonSociale`) et l'employe responsable (`responsableNom`) sont
 * resolus cote back par jointure (jamais d'UUID a l'ecran). Miroir du record
 * Java `DemandeSupervisionRow`.
 */
export interface DemandeSupervisionRow {
  id: string;
  dossierId: string;
  raisonSociale: string | null;
  responsableId: string | null;
  responsableNom: string | null;
  sujet: string;
  description: string | null;
  statut: string;
  direction: DemandeDirection;
  typeRequete: TypeRequete | null;
  createdAt: string;
}

export interface CreateRequetePayload {
  sujet: string;
  description?: string;
  dossierId: string;
  typeRequete: TypeRequete;
}
export interface RepondreRequetePayload {
  noteClient?: string;
}
export interface ComplementRequetePayload {
  note: string;
}

export type AccessStatus = 'ACTIVE' | 'SUSPENDED';

export interface DataroomSettings {
  dossierId: string;
  accessStatus: AccessStatus;
  permDownload: boolean;
  permPrint: boolean;
  /** V19 (2026-06-30) : le client peut-il DEPOSER des documents ? Defaut false. */
  permDepot: boolean;
  clientLinkToken: string;
  accessCount: number;
  lastAccessedAt: string | null;
}

/**
 * Vue allegee des permissions exposee au CLIENT (GET .../settings/my-permissions).
 * Ne contient que les droits utiles a l'UI client (pas de token/email/compteurs).
 */
export interface ClientPermissions {
  dossierId: string;
  accessStatus: AccessStatus;
  permDownload: boolean;
  permPrint: boolean;
  permDepot: boolean;
}

// =====================================================================
// Sprint 7 / TASK 1 -- Recherche FTS + filtres avances Juridique
// =====================================================================

export type VersionScope = 'CURRENT' | 'OLD' | 'ALL';

export interface SearchJuridiqueParams {
  q?: string;
  types?: (DocumentType | string)[];
  from?: string; // ISO 8601
  to?: string;   // ISO 8601
  versionScope?: VersionScope;
  limit?: number;
  offset?: number;
}

export interface SearchJuridiqueResult {
  items: DocumentSummary[];
  total: number;
}

export const VERSION_SCOPE_LABELS: Record<VersionScope, string> = {
  CURRENT: 'En vigueur uniquement',
  OLD: 'Anciennes versions',
  ALL: 'Tous (en vigueur + anciennes)',
};

// =====================================================================
// Sprint 7 / TASK 5 -- Logs acces client + realtime WebSocket
// =====================================================================

export type AccessLogAction =
  | 'VIEW_DOSSIER'
  | 'PREVIEW_DOC'
  | 'DOWNLOAD_DOC'
  | 'DOWNLOAD_VERSION'
  | 'PRINT_DOC'
  | 'DEPOT_DOC'
  | 'DEMANDE';

export const ACCESS_LOG_ACTION_LABELS: Record<AccessLogAction, string> = {
  VIEW_DOSSIER: 'Consultation',
  PREVIEW_DOC: 'Visualisation',
  DOWNLOAD_DOC: 'Telechargement',
  DOWNLOAD_VERSION: 'Telechargement (version)',
  PRINT_DOC: 'Impression',
  DEPOT_DOC: 'Import (depot)',
  DEMANDE: 'Demande',
};

export interface AccessLogEntry {
  id: string;
  dossierId: string;
  userId: string | null;
  documentId: string | null;
  action: AccessLogAction | string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface AccessLogPage {
  items: AccessLogEntry[];
  total: number;
}

/** Sprint 7 / TASK 5.2 -- payload de l'event WebSocket emis par realtime-service. */
export type DataroomEventKind = 'UPLOADED' | 'DELETED' | 'REPLACED';

export interface DataroomDocumentEvent {
  kind: DataroomEventKind;
  workspaceId: string;
  dossierId: string;
  documentId: string | null;
  documentType: string | null;
  title: string | null;
  byUserId: string | null;
  at: string;
}

// =====================================================================
// Sprint 7 / TASK 6 -- Dossier Fiscal placeholder (Sprint 8 base)
// =====================================================================

export type ExerciceFiscalStatut = 'OUVERT' | 'CLOTURE' | 'VERROUILLE';

export interface ExerciceFiscalSummary {
  id: string;
  annee: number;
  dateDebut: string | null;
  dateFin: string | null;
  statut: ExerciceFiscalStatut | string;
  dateOuverture: string;
  dateCloture: string | null;
}

export interface DossierFiscalView {
  dossierId: string;
  exerciceCourant: string | null;
  exercices: ExerciceFiscalSummary[];
  categoriesCgi: string[];
  message: string;
}

export const CATEGORIE_CGI_LABELS: Record<string, string> = {
  TVA: 'TVA',
  IS: "Impot sur les Societes (IS)",
  IR: 'Impot sur le Revenu (IR)',
  TP_TSC: 'TP / TSC',
  RAS: 'Retenues a la source',
  ATTESTATIONS: 'Attestations',
  CONTENTIEUX: 'Contentieux',
};

// =====================================================================
// Sprint 8 -- Dossier Fiscal complet (7 categories CGI)
// =====================================================================

export type CategorieFiscale =
  | 'TVA'
  | 'IS'
  | 'IR'
  | 'TP_TSC'
  | 'RAS'
  | 'ATTESTATIONS'
  | 'CONTENTIEUX'
  // Prompt G (2026-06-23) — fourre-tout pour les imports d'anciens dossiers.
  | 'AUTRE';

export const CATEGORIES_FISCALES_ORDER: CategorieFiscale[] = [
  'TVA',
  'IS',
  'IR',
  'TP_TSC',
  'RAS',
  'ATTESTATIONS',
  'CONTENTIEUX',
  'AUTRE',
];

export const CATEGORIES_FISCALES_LABELS: Record<CategorieFiscale, string> = {
  TVA: 'TVA',
  IS: 'Impôt sur les sociétés',
  IR: 'Impôt sur le revenu',
  TP_TSC: 'TP / TSC',
  RAS: 'Retenues à la source',
  ATTESTATIONS: 'Attestations',
  CONTENTIEUX: 'Contentieux',
  AUTRE: 'Autre',
};

export interface FiscalDocumentSummary {
  id: string;
  dossierId: string;
  exerciceFiscalId: string;
  categorie: CategorieFiscale | string;
  sousClassification: string;
  title: string;
  commentaire: string | null;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  tifMetadata: string | null;
  numeroDeclaration: string | null;
  periodeDeclaree: string | null;
  comptableDocSource: string | null;
  comptableDocSourceTitle: string | null;
  createdAt: string;
  deletedAt: string | null;
}

export interface FiscalCategoryCount {
  categorie: CategorieFiscale | string;
  total: number;
}

export interface DossierFiscalDetailedView {
  dossierId: string;
  exerciceCourant: string | null;
  exercices: ExerciceFiscalSummary[];
  compteurs: FiscalCategoryCount[];
  categoriesCgi: string[];
}

export interface SubClassificationDef {
  categorie: CategorieFiscale | string;
  values: string[];
}

export interface OpenExerciceRequest {
  annee: number;
  dateDebut?: string | null;
  dateFin?: string | null;
  regimeTvaMensuel: boolean;
  /**
   * RG-DF03 (2026-06-24) — conformité au comptable.
   * - `false`/absent (ouverture manuelle onglet Fiscal) : l'année doit déjà être
   *   tenue en comptabilité, sinon le backend rejette (422).
   * - `true` (finalisation import/création) : l'ancre comptable de l'année est
   *   créée à la volée avant d'ouvrir le fiscal, pour rester conforme sans bloquer.
   */
  autoCreateComptable?: boolean;
}

export interface UnlockExerciceRequest {
  motif: string;
}

export interface EcheanceSummary {
  id: string;
  exerciceFiscalId: string;
  typeEcheance: string;
  dateEcheance: string;
  dateAlerte: string;
  statut: 'PLANIFIEE' | 'ENVOYEE' | 'TRAITEE' | 'EXPIREE' | string;
  documentId: string | null;
  sentAt: string | null;
  traiteAt: string | null;
}

export const TYPE_ECHEANCE_LABELS: Record<string, string> = {
  TVA_MENSUELLE: 'TVA mensuelle',
  TVA_TRIMESTRIELLE: 'TVA trimestrielle',
  IS_ACOMPTE_T1: 'IS acompte T1',
  IS_ACOMPTE_T2: 'IS acompte T2',
  IS_ACOMPTE_T3: 'IS acompte T3',
  IS_ACOMPTE_T4: 'IS acompte T4',
  IS_DECLARATION_ANNUELLE: 'IS declaration annuelle',
  TP_TSC_DECLARATION: 'TP / TSC declaration',
  ETAT_9421: 'Etat 9421',
  IR_DECLARATION_ANNUELLE: 'IR declaration annuelle',
};

export const SOUS_CLASSIFICATION_LABELS: Record<string, string> = {
  DECLARATION_MENSUELLE: 'Declaration mensuelle',
  DECLARATION_TRIMESTRIELLE: 'Declaration trimestrielle',
  PAIEMENT: 'Paiement',
  DEMANDE_REMBOURSEMENT: 'Demande remboursement',
  ATTESTATION: 'Attestation',
  ACOMPTE_T1: 'Acompte T1',
  ACOMPTE_T2: 'Acompte T2',
  ACOMPTE_T3: 'Acompte T3',
  ACOMPTE_T4: 'Acompte T4',
  DECLARATION_ANNUELLE: 'Declaration annuelle',
  ETATS_DE_SYNTHESE: 'Etats de synthese',
  COTISATION_MINIMALE: 'Cotisation minimale',
  RAS_SALARIES_MENSUEL: 'RAS salaries mensuel',
  DECLARATION_IR_PRO: 'Declaration IR pro',
  ETAT_9421: 'Etat 9421',
  ROLE_ANNUEL: 'Role annuel',
  DECLARATION_EXISTENCE: "Declaration d'existence",
  DECLARATION_CESSATION: 'Declaration cessation',
  RECLAMATION: 'Reclamation',
  HONORAIRES_10: 'Honoraires 10%',
  HONORAIRES_20: 'Honoraires 20%',
  DIVIDENDES_15: 'Dividendes 15%',
  INTERETS: 'Interets',
  LOCATIONS: 'Locations',
  OPCVM: 'OPCVM',
  REGULARITE_FISCALE: 'Regularite fiscale',
  QUITUS_FISCAL: 'Quitus fiscal',
  ATTESTATION_IS: 'Attestation IS',
  ATTESTATION_TVA: 'Attestation TVA',
  ETAT_IMPOSITION: "Etat d'imposition",
  NOTIFICATION_DGI: 'Notification DGI',
  AVIS_REDRESSEMENT: 'Avis de redressement',
  ACCORD: 'Accord',
  RECOURS_HIERARCHIQUE: 'Recours hierarchique',
  JUGEMENT_TRIBUNAL: 'Jugement tribunal',
};

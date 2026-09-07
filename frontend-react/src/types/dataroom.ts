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
  /**
   * Lot 3 (2026-09-07) — date de la dernière édition manuelle dans l'éditeur
   * bureautique, ou `null` si le document est tel que généré. L'interface s'en
   * sert pour NOMMER ce qu'une régénération ferait perdre : un avertissement
   * générique se clique sans se lire.
   */
  editeManuellementAt?: string | null;
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
  /** Lot 1 (2026-09-04) — le dossier juridique organise par ticket. */
  dossiersParTicket: DossierTicket[];
}

/** Un groupe de documents dans le dossier d un ticket. */
export interface GroupeDocuments {
  /** ACTES_GENERES | JUSTIFICATIFS_ADMINISTRATIFS | PIECES_CLIENT */
  code: string;
  libelle: string;
  documents: DocumentSummary[];
}

/**
 * Chaque ticket forme un dossier. Le libelle est CALCULE cote serveur
 * (« Creation — T-2026-00841 — 15/06/2026 »), jamais saisi.
 *
 * ticketId vaut null pour le regroupement « Hors ticket », qui recueille les
 * documents anterieurs a ce lot ou deposes hors workflow. Ils restent
 * accessibles : un document mal classe se retrouve, un document invisible est
 * perdu.
 */
export interface DossierTicket {
  ticketId: string | null;
  libelle: string;
  reference: string | null;
  type: string | null;
  statut: string | null;
  ouvertLe: string | null;
  groupes: GroupeDocuments[];
  totalDocuments: number;
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
// Lot 2 (2026-09-07) — TYPES COMPTABLE ET FISCAL RETIRES
//
// Les dossiers comptable et fiscal sont sortis du perimetre produit au lot 1
// (migration dataroom V25 : tables supprimees apres inventaire chiffre valide).
// Les types, libelles, ordres d'affichage et DTO d'exercice restaient ici sans
// plus rien decrire — ni ecran, ni endpoint, ni table. Ils sont retires : du
// code mort qui parle de sections disparues finit par se relire comme une
// specification, et par etre reimplemente.
// =====================================================================

/**
 * Lot 3 (2026-09-07) — une séance d'édition bureautique.
 *
 * `wopiSrc` désigne le backend par son nom de service Docker : c'est Collabora
 * qui l'appelle, le navigateur ne le joint jamais. `editeurUrl` est lue dans le
 * document de découverte de Collabora — son chemin porte une empreinte de
 * version qui change à chaque publication de l'image, elle ne se devine pas.
 */
export interface SeanceEdition {
  sessionId: string;
  wopiSrc: string;
  accessToken: string;
  accessTokenTtlMs: number;
  editeurUrl: string;
  canWrite: boolean;
  /** Date de la dernière édition manuelle, ou null si le document est tel que généré. */
  editeManuellementAt: string | null;
  /**
   * Nom de l'employé qui édite déjà cet acte, ou null. Renseigné, la séance
   * s'ouvre en lecture seule — et on le dit AVANT d'ouvrir l'éditeur, plutôt
   * que de laisser découvrir en fermant que le travail n'a pas été gardé.
   */
  verrouPar: string | null;
  /** Depuis quand, pour que l'appel téléphonique qui suit soit informé. */
  verrouDepuis: string | null;
}

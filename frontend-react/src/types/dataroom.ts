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
  // 2026-09-07 (lot 5) — FORMULAIRES administratifs generes par la plateforme
  // (etapes 19, 20 et 21 du parcours de creation). A ne pas confondre avec les
  // documents que ces formulaires font OBTENIR, deja au catalogue : l'attestation
  // de taxe professionnelle ('TP'), le bulletin d'identification fiscale
  // ('BULLETIN_IF') et le certificat d'immatriculation modele J ('RC'). Sans type
  // dedie ils tombaient en 'AUTRE' — et deux 'AUTRE' de meme titre se
  // dedupliquent (unicite du courant, V23).
  | 'DEMANDE_TAXE_PROFESSIONNELLE'
  | 'DECLARATION_EXISTENCE'
  | 'DECLARATION_IMMATRICULATION_RC'
  // 2026-09-11 (lot B) — les types du parcours du 9 septembre, alignés sur les
  // migrations dataroom V24 et V31. Deux familles :
  //
  //  1. Les JUSTIFICATIFS que le parcours fait archiver. `RECEPISSE_DEPOT`
  //     revient sur ONZE lignes — c'est la pièce rendue au dépôt, celle dont la
  //     date fait courir l'attente du retrait. `AVIS_VERSEMENT_BANQUE` n'est pas
  //     `ATTESTATION_BLOCAGE_CAPITAL` : le premier prouve le versement, la
  //     seconde l'indisponibilité — deux pièces, deux moments.
  | 'RECEPISSE_DEPOT'
  | 'AVIS_VERSEMENT_BANQUE'
  | 'INVESTISSEMENT_ETRANGER'
  | 'ACCUSE_RETRAIT_DEPOT'
  | 'ATTESTATION_ENREGISTREMENT'
  | 'ATTESTATION_BLOCAGE_CAPITAL'
  | 'BULLETIN_IF'
  | 'JOURNAL_ANNONCE'
  | 'PUBLICATION_BO'
  | 'ACCUSE_RBE'
  | 'RIB'
  | 'LIVRES_LEGAUX'
  | 'AUTORISATION_SECTORIELLE'
  | 'IDENTIFIANTS_SIMPL'
  | 'RECEPISSE_CNDP'
  | 'PIECE_IDENTITE'
  | 'VALIDATION_CLIENT'
  | 'TITRE_PROPRIETE'
  //  2. Les DOCUMENTS QUE JURIKA PRODUIT et qui n'avaient pas de type : sans
  //     lui, ils tombaient tous en 'AUTRE', et deux 'AUTRE' de même titre se
  //     dédupliquent (unicité du courant, V23) — un document en effaçait un autre.
  | 'CONTRAT_DOMICILIATION'
  | 'ETAT_ACTES_FORMATION'
  | 'ATTESTATION_SOUSCRIPTION_LIBERATION'
  | 'POUVOIR'
  | 'BORDEREAU_REMISE'
  | 'FICHE_RENSEIGNEMENTS'
  | 'RAPPORT_COMMISSAIRE_APPORTS'
  | 'DEMANDE_AFFILIATION_CNSS'
  | 'DECLARATION_BENEFICIAIRES_EFFECTIFS'
  | 'DEMANDE_DEBLOCAGE_CAPITAL'
  | 'DECLARATION_CNDP'
  | 'DEMANDE_ADHESION_SIMPL'
  | 'NOTE_CONFORMITE'
  | 'NOTE_ANNULATION'
  | 'LETTRE_RETRAIT_DEPOT'
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
  /** Lot L1 (RG-FIC-02) : date de prise d'effet de la taxe professionnelle, facultative. */
  taxeProfessionnelleDateEffet?: string | null;
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
  // 2026-09-07 (lot 5) — formulaires administratifs deposes par le cabinet.
  DEMANDE_TAXE_PROFESSIONNELLE: "Demande d'inscription a la taxe professionnelle",
  DECLARATION_EXISTENCE: "Declaration d'existence",
  DECLARATION_IMMATRICULATION_RC: "Declaration d'immatriculation au RC (modele 2)",
  // 2026-09-11 (lot B) — les justificatifs du parcours du 9 septembre (V24, V31).
  RECEPISSE_DEPOT: 'Recepisse de depot',
  AVIS_VERSEMENT_BANQUE: 'Avis de versement de la banque',
  INVESTISSEMENT_ETRANGER: "Formulaire d'investissement etranger",
  ACCUSE_RETRAIT_DEPOT: 'Accuse de reception (retrait ou regularisation)',
  ATTESTATION_ENREGISTREMENT: "Attestation d'enregistrement",
  ATTESTATION_BLOCAGE_CAPITAL: 'Attestation de blocage du capital',
  BULLETIN_IF: "Bulletin d'identification fiscale",
  JOURNAL_ANNONCE: "Journal d'annonces legales",
  PUBLICATION_BO: 'Publication au Bulletin officiel',
  ACCUSE_RBE: 'Accuse de depot — beneficiaires effectifs',
  RIB: 'RIB',
  LIVRES_LEGAUX: 'Livres legaux cotes et paraphes',
  AUTORISATION_SECTORIELLE: 'Autorisation, licence ou agrement',
  IDENTIFIANTS_SIMPL: "Identifiants d'acces SIMPL",
  RECEPISSE_CNDP: 'Recepisse CNDP',
  PIECE_IDENTITE: "Piece d'identite",
  VALIDATION_CLIENT: 'Validation ecrite du client',
  TITRE_PROPRIETE: 'Titre de propriete',
  // Les documents que JURIKA produit au corpus du 9 septembre.
  CONTRAT_DOMICILIATION: 'Contrat de domiciliation',
  ETAT_ACTES_FORMATION: 'Etat des actes accomplis en formation',
  ATTESTATION_SOUSCRIPTION_LIBERATION: 'Declaration de souscription et de versement',
  POUVOIR: 'Pouvoir pour les formalites',
  BORDEREAU_REMISE: 'Bordereau de remise',
  FICHE_RENSEIGNEMENTS: 'Fiche de renseignements',
  RAPPORT_COMMISSAIRE_APPORTS: 'Rapport du commissaire aux apports',
  DEMANDE_AFFILIATION_CNSS: "Demande d'affiliation CNSS",
  DECLARATION_BENEFICIAIRES_EFFECTIFS: 'Declaration des beneficiaires effectifs',
  DEMANDE_DEBLOCAGE_CAPITAL: 'Demande de deblocage du capital',
  DECLARATION_CNDP: 'Declaration CNDP',
  DEMANDE_ADHESION_SIMPL: "Demande d'adhesion aux teleservices SIMPL",
  NOTE_CONFORMITE: 'Note de conformite des mentions legales',
  NOTE_ANNULATION: "Note d'annulation du dossier",
  LETTRE_RETRAIT_DEPOT: 'Lettre de retrait ou de regularisation',
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
  'DEMANDE_TAXE_PROFESSIONNELLE',
  'DECLARATION_EXISTENCE',
  'DECLARATION_IMMATRICULATION_RC',
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

/** Lot L1 : ligne d'historique des responsables d'un dossier (ticket-service). */
export interface ReaffectationVue {
  id: string;
  dossierId: string;
  raisonSociale: string;
  ancienResponsableId: string | null;
  ancienResponsableNom: string | null;
  nouveauResponsableId: string;
  nouveauResponsableNom: string | null;
  nature: 'ACCEPTEE' | 'FORCEE' | 'RATTRAPAGE';
  auteurId: string | null;
  auteurNom: string | null;
  motif: string | null;
  createdAt: string;
  responsableActuelId: string;
  responsableActuelNom: string | null;
  verifiePar: string | null;
  verifieParNom: string | null;
  verifieLe: string | null;
}

/** Lot L1 : version datee de la taxe professionnelle (RG-FIC-02). */
export interface VersionTaxeProfessionnelle {
  id: string;
  numero: string;
  dateEffet: string | null;
  saisiPar: string;
  saisiLe: string;
  origine: 'SAISIE' | 'REPRISE_V30';
  enVigueur: boolean;
}

/** Lot L1 : droits de l'utilisateur sur un dossier (bouton de suppression). */
export interface DroitsDossier {
  peutSupprimerDocuments: boolean;
  motif: string | null;
}

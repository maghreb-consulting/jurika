export type TicketType =
  | 'CREATION'
  | 'IMPORT'
  | 'MODIFICATION'
  | 'DISSOLUTION'
  | 'LIQUIDATION'
  | 'SUCCURSALE_MA'
  | 'SUCCURSALE_ETR'
  | 'FERMETURE_SUCCURSALE'
  | 'PV_AGO';

export type FormeJuridique = 'SARL' | 'SARL_AU' | 'SA' | 'SAS' | 'SCS' | 'GIE';

/**
 * Types qui declenchent la creation automatique d'un dossier d'entreprise
 * (et donc d'un Data Room) au moment du POST /tickets.
 */
export const TYPES_AUTO_DOSSIER: TicketType[] = ['CREATION', 'IMPORT'];

/** Formes juridiques actuellement supportees en UI (extensible Phase 8+). */
export const FORMES_JURIDIQUES_SUPPORTED: FormeJuridique[] = ['SARL', 'SARL_AU'];

export const FORME_JURIDIQUE_LABELS: Record<FormeJuridique, string> = {
  SARL: 'SARL',
  SARL_AU: 'SARL associe unique (SARL AU)',
  SA: 'SA',
  SAS: 'SAS',
  SCS: 'SCS',
  GIE: 'GIE',
};

export interface CompanyInfo {
  raisonSociale: string;
  formeJuridique: FormeJuridique;
}

/**
 * Les cinq statuts du guide du cabinet (onglet « 2. Workflow ticket »).
 *
 * ANNULE n'est pas la fin du parcours mais une SORTIE LATERALE : il est
 * atteignable depuis n'importe quel statut, y compris CLOTURE_DOSSIER.
 */
export type TicketStatut =
  | 'CREATION_TICKET'
  | 'GENERATION_DOCUMENTS'
  | 'DEROULEMENT_DEMARCHE'
  | 'CLOTURE_DOSSIER'
  | 'ANNULE';

/** Les quatre statuts du parcours nominal, dans l'ordre. */
export const PARCOURS_STATUTS: TicketStatut[] = [
  'CREATION_TICKET',
  'GENERATION_DOCUMENTS',
  'DEROULEMENT_DEMARCHE',
  'CLOTURE_DOSSIER',
];

export type TicketPriorite = 'BASSE' | 'NORMALE' | 'HAUTE' | 'URGENTE';

export type DeboursCategorie =
  | 'FRAIS_TRIBUNAL'
  | 'FRAIS_NOTARIE'
  | 'FRAIS_ENREGISTREMENT'
  | 'PUBLICATION_JAL'
  | 'PUBLICATION_BO'
  | 'HONORAIRES'
  | 'TRANSPORT'
  | 'AUTRE';

export interface Ticket {
  id: string;
  workspaceId: string;
  reference: string;
  titre: string;
  type: TicketType;
  statut: TicketStatut;
  priorite: TicketPriorite;
  dossierId: string | null;
  assigneId: string | null;
  creeParId: string;
  description: string | null;
  deadline: string | null;
  annulationMotif: string | null;
  /**
   * Motif de la reprise la plus recente (transition ANNULE -> EN_COURS), fourni
   * par le backend sur la vue detail. null si le ticket n'a jamais ete repris.
   * Affiche a la place du motif d'annulation quand le ticket n'est plus ANNULE.
   */
  repriseMotif?: string | null;
  clotureAt: string | null;
  annuleAt: string | null;
  createdAt: string;
  /** V10 — vrai si le ticket a ete repris via un transfert de dossier. */
  transferred?: boolean;
  /**
   * Nom de l'employe responsable (assigneId) resolu cote backend depuis la table
   * users, pour affichage direct (Kanban / Tableau). null si non assigne ou
   * introuvable. Utiliser {@link responsableLabel} plutot que ces champs bruts.
   */
  assignePrenom?: string | null;
  assigneNom?: string | null;
}

/**
 * Libelle affichable du responsable d'un ticket : « Prenom Nom » si resolu,
 * sinon « Non assigne ». Ne renvoie JAMAIS d'UUID (RG : pas d'identifiant brut
 * dans l'UI). Le backend laisse les champs a null quand le ticket n'est pas
 * assigne ou que le compte a ete purge.
 */
export function responsableLabel(t: Pick<Ticket, 'assignePrenom' | 'assigneNom'>): string {
  const full = `${t.assignePrenom ?? ''} ${t.assigneNom ?? ''}`.trim();
  return full || 'Non assigne';
}

export interface Debours {
  id: string;
  ticketId: string;
  libelle: string;
  categorie: DeboursCategorie;
  montantMad: number;
  dateEngagement: string;
  pieceJointeUrl: string | null;
  pieceJointeFilename: string | null;
  notes: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

export interface CreateTicketPayload {
  titre: string;
  type: TicketType;
  priorite?: TicketPriorite;
  dossierId?: string;
  assigneId?: string;
  description?: string;
  deadline?: string;
  /** Requis pour type=CREATION ou IMPORT (cree auto dossier + Data Room). */
  companyInfo?: CompanyInfo;
}

export interface UpdateTicketPayload {
  titre?: string;
  description?: string;
  priorite?: TicketPriorite;
  assigneId?: string;
  deadline?: string;
}

export interface TransitionPayload {
  target: TicketStatut;
  comment?: string;
}

export interface DeboursPayload {
  libelle: string;
  categorie: DeboursCategorie;
  montant: number;
  dateEngagement: string;
  pieceJointeUrl?: string;
  pieceJointeFilename?: string;
  notes?: string;
}

export const TICKET_TYPE_LABELS: Record<TicketType, string> = {
  CREATION: 'Creation',
  IMPORT: 'Import dossier',
  MODIFICATION: 'Modification',
  DISSOLUTION: 'Dissolution',
  LIQUIDATION: 'Liquidation',
  SUCCURSALE_MA: 'Succursale marocaine',
  SUCCURSALE_ETR: 'Succursale etrangere',
  FERMETURE_SUCCURSALE: 'Fermeture succursale',
  PV_AGO: 'PV AGO',
};

export const STATUT_LABELS: Record<TicketStatut, string> = {
  // Lot B (2026-09-11) — libelle du parcours du 9 septembre. Le CODE ne change
  // pas : il est porte par la base, l'API et neuf autres workflows.
  CREATION_TICKET: "Création du ticket et collecte d'information",
  GENERATION_DOCUMENTS: 'Génération des documents',
  DEROULEMENT_DEMARCHE: 'Déroulement de la démarche',
  CLOTURE_DOSSIER: 'Clôture de dossier',
  ANNULE: 'Ticket annulé',
};

/** Libellé court, pour les badges et les colonnes étroites. */
export const STATUT_LABELS_COURTS: Record<TicketStatut, string> = {
  CREATION_TICKET: 'Création',
  GENERATION_DOCUMENTS: 'Génération',
  DEROULEMENT_DEMARCHE: 'Démarches',
  CLOTURE_DOSSIER: 'Clôturé',
  ANNULE: 'Annulé',
};

export const PRIORITE_LABELS: Record<TicketPriorite, string> = {
  BASSE: 'Basse',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  URGENTE: 'Urgente',
};

export const CATEGORIE_LABELS: Record<DeboursCategorie, string> = {
  FRAIS_TRIBUNAL: 'Frais tribunal',
  FRAIS_NOTARIE: 'Frais notarie',
  FRAIS_ENREGISTREMENT: 'Frais enregistrement',
  PUBLICATION_JAL: 'Publication JAL',
  PUBLICATION_BO: 'Publication BO',
  HONORAIRES: 'Honoraires',
  TRANSPORT: 'Transport',
  AUTRE: 'Autre',
};

/**
 * Lot L1 (RG-TKT-07) : note interne d'un ticket. `modifieLe` null : jamais
 * enregistree (note vide, ouverte des la creation du ticket).
 */
export interface TicketNote {
  ticketId: string;
  contenu: string;
  modifiePar: string | null;
  modifieLe: string | null;
}

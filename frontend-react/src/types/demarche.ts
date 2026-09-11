import type { TicketStatut } from './ticket';

/**
 * Le référentiel des démarches et leur cochage sur un ticket.
 *
 * Miroir exact de `DemarcheUseCases.Vue` (ticket-service). Le référentiel vit
 * en base — chargé depuis le guide du cabinet par migration — et non dans ce
 * fichier : rien ici ne décrit les 36 étapes.
 */

export type DemarcheEtat = 'A_FAIRE' | 'COCHEE' | 'NON_APPLICABLE';

/** Lot B — les deux volets d'une formalité qui figure sur deux lignes. */
export type FormaliteVolet = 'DEPOT' | 'RETRAIT';

/** Lot B — un geste posé sur une démarche, et conservé. */
export type EvenementType = 'COCHAGE' | 'ANNULATION' | 'HORS_PERIMETRE' | 'REPRISE';

/**
 * Lot B — le journal d'une démarche.
 *
 * Ce n'est pas une case à décocher : une démarche cochée, annulée, puis
 * recochée garde la trace des TROIS événements, et les deux horodatages —
 * cochage et annulation — sont conservés tous les deux.
 */
export interface DemarcheEvenement {
  id: string;
  ticketDemarcheId: string;
  type: EvenementType;
  /** Obligatoire sur une annulation et sur une mise hors périmètre. */
  motif: string | null;
  acteurId: string | null;
  survenuLe: string;
  justificatifs: number;
}

/** DEPASSE | CRITIQUE (J-3) | APPROCHE (J-15) */
export type SeveriteEcheance = 'DEPASSE' | 'CRITIQUE' | 'APPROCHE';

/**
 * Un justificatif attendu au cochage. Le `documentType` vient du référentiel :
 * il n'est jamais choisi par l'employé.
 *
 * Deux justificatifs de même `alternativeGroupe` sont des ALTERNATIVES — l'un
 * suffit (bail OU domiciliation OU titre de propriété). Deux groupes distincts
 * sont CUMULATIFS.
 */
export interface JustificatifAttendu {
  alternativeGroupe: number;
  documentType: string;
  libelle: string;
}

export interface LigneDemarche {
  demarcheId: string;
  ordre: number;
  phaseCode: string;
  phaseLibelle: string;
  libelle: string;
  statutTicket: TicketStatut;
  acteur: string | null;
  organisme: string | null;
  obligatoire: boolean;
  conditionApplication: string | null;
  piecesEntrantes: string | null;
  documentProduit: string | null;
  justificatifsTexte: string | null;
  modeleJurika: string | null;
  delai: string | null;
  coutIndicatif: string | null;
  variablesAlimentees: string | null;
  /**
   * false quand le parcours donne un délai chiffré sans point de départ
   * mécanisable. Une seule ligne est dans ce cas — la 15, « dans les 30 jours
   * de la signature du contrat », qu'aucune ligne cochable ne porte.
   * L'interface doit alors le dire, et ne jamais afficher de date.
   */
  delaiCalculable: boolean;
  /**
   * Lot B — nom de la donnée qu'on attend pour faire courir le délai, `null`
   * dès qu'elle est saisie ou quand le délai n'en dépend pas.
   *
   * Deux lignes en dépendent : la taxe professionnelle et l'affiliation CNSS
   * courent « dans les 30 jours du début d'activité », qui est une date
   * déclarée et non une étape qu'on coche. Sans ce champ, elles afficheraient
   * leur délai sans alerte et sans explication — le produit aurait l'air de
   * savoir calculer et de ne rien dire. Il sait calculer ; il attend la date.
   */
  delaiDepartManquant: string | null;
  justificatifsAttendus: JustificatifAttendu[];
  etat: DemarcheEtat;
  motif: string | null;
  cocheAt: string | null;
  documentsDeposes: string[];
  /** La démarche relève du statut courant du ticket : elle est actionnable. */
  actionnableMaintenant: boolean;
  /** Cochée alors qu'une précédente de la même phase ne l'est pas. Signalé, pas interdit. */
  horsSequence: boolean;
  /**
   * Lot B — les deux lignes d'une même formalité, dépôt puis retrait, portent
   * le même code. `null` pour une ligne unique.
   */
  formaliteCode: string | null;
  formaliteVolet: FormaliteVolet | null;
  /** Pour un RETRAIT : l'ordre de la ligne de dépôt. */
  depotOrdre: number | null;
  /**
   * Pour un RETRAIT : la date à laquelle le dépôt a été coché. C'est elle qui
   * répond à « depuis quand attendons-nous ? » — le parcours ne donne aucune
   * durée pour un retrait, mais il donne le point de départ.
   */
  deposeLe: string | null;
  /** Les gestes posés sur cette démarche, du plus ancien au plus récent. */
  journal: DemarcheEvenement[];
}

export interface PhaseDemarches {
  code: string;
  libelle: string;
  demarches: LigneDemarche[];
  traitees: number;
  total: number;
}

export interface PointAttention {
  ordre: number;
  libelle: string;
  delai: string | null;
  /** Date civile limite, calculée. Jamais fabriquée. */
  echeance: string;
  joursRestants: number;
  severite: SeveriteEcheance;
}

export interface AvancementTicket {
  statutCourant: TicketStatut;
  /** Position dans le parcours (1 à 4) ; -1 pour un ticket annulé. */
  position: number;
  totalStatuts: number;
  traitees: number;
  applicables: number;
  prochainOrdre: number | null;
  prochainLibelle: string | null;
  pointsAttention: PointAttention[];
}

export interface VueDemarches {
  workflowType: string;
  statutCourant: TicketStatut;
  phases: PhaseDemarches[];
  avancement: AvancementTicket;
}

// =====================================================================
//  Lot B — le récapitulatif du ticket, avant de le clore
// =====================================================================

/** Un document rattaché au ticket, tel qu'il est dans la Data Room. */
export interface DocumentProduit {
  id: string;
  documentType: string;
  titre: string;
  /** Le client le voit-il ? Remettre un dossier dont des pièces restent
   *  masquées est une décision, pas un hasard : le récapitulatif le dit. */
  visibleClient: boolean;
}

export interface DemarcheAccomplie {
  ordre: number;
  libelle: string;
  etat: DemarcheEtat;
  cocheAt: string | null;
  motif: string | null;
  journal: DemarcheEvenement[];
}

/**
 * Une pièce attendue par le référentiel.
 *
 * `alternatives` porte les types qui satisferaient le même groupe — « bail OU
 * domiciliation OU titre de propriété ». Un seul suffit.
 */
export interface JustificatifRecapitulatif {
  ordreDemarche: number;
  libelleDemarche: string;
  groupe: number;
  alternatives: string[];
  libelle: string | null;
  archive: boolean;
  documentId: string | null;
}

export interface IdentifiantsSociete {
  rcNumero: string | null;
  identifiantFiscal: string | null;
  ice: string | null;
  taxeProfessionnelle: string | null;
  cnss: string | null;
}

export interface RecapitulatifCloture {
  ticketId: string;
  reference: string;
  statut: TicketStatut;
  /** Aucune pièce ne manque. La clôture reste une décision explicite. */
  clotureEnvisageable: boolean;
  documents: DocumentProduit[];
  demarches: DemarcheAccomplie[];
  justificatifs: JustificatifRecapitulatif[];
  /** Ce qui manque, nommé ligne par ligne. */
  manquants: string[];
  /** La cellule « Justificatif » de la ligne 47 du parcours, verbatim. */
  piecesSelonLeParcours: string | null;
  identifiants: IdentifiantsSociete;
}

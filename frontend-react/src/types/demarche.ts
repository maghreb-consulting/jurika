import type { TicketStatut } from './ticket';

/**
 * Le référentiel des démarches et leur cochage sur un ticket.
 *
 * Miroir exact de `DemarcheUseCases.Vue` (ticket-service). Le référentiel vit
 * en base — chargé depuis le guide du cabinet par migration — et non dans ce
 * fichier : rien ici ne décrit les 36 étapes.
 */

export type DemarcheEtat = 'A_FAIRE' | 'COCHEE' | 'NON_APPLICABLE';

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
   * false quand le guide donne un délai chiffré sans point de départ
   * mécanisable (étapes 16, 19, 26). L'interface doit alors le dire, et ne
   * jamais afficher de date.
   */
  delaiCalculable: boolean;
  justificatifsAttendus: JustificatifAttendu[];
  etat: DemarcheEtat;
  motif: string | null;
  cocheAt: string | null;
  documentsDeposes: string[];
  /** La démarche relève du statut courant du ticket : elle est actionnable. */
  actionnableMaintenant: boolean;
  /** Cochée alors qu'une précédente de la même phase ne l'est pas. Signalé, pas interdit. */
  horsSequence: boolean;
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

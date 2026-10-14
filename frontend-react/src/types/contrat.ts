/**
 * Lot L7 (E6) : contrat d'abonnement (CDC 4.8) et parametres de l'editeur.
 * Miroir des vues de billing-service (ContratService, EditeurParametresService).
 */

export type StatutContrat = 'AUCUN' | 'EN_ATTENTE' | 'ACCEPTE' | 'RESILIE';
export type ObjetAcceptation = 'CONDITIONS_PARTICULIERES' | 'NOUVELLE_VERSION';
export type Forfait = 'ESSENTIEL' | 'PROFESSIONNEL' | 'MAX' | 'SUR_DEVIS';
export type Rythme = 'MENSUEL' | 'ANNUEL';
export type ModePaiement = 'CARTE' | 'VIREMENT' | 'CHEQUE' | 'ESPECES';

export interface ConditionsVue {
  id: string;
  reference: string;
  forfait: Forfait;
  forfaitLibelle: string;
  rythmePaiement: Rythme;
  prixHt: number;
  dateDebut: string;
  dateFin: string;
  echeanceCourante: string;
  modePaiement: ModePaiement | null;
  statut: 'EN_ATTENTE' | 'ACCEPTE' | 'REMPLACE' | 'ANNULE' | 'RESILIE';
  origine: 'SUPER_ADMIN' | 'ABONNEMENT';
  creeLe: string;
}

export interface AcceptationVue {
  id: string;
  conditionsId: string;
  objet: ObjetAcceptation;
  versionCode: string;
  empreinteContrat: string;
  signataire: string;
  signataireQualite: string;
  signataireCompte: string;
  accepteLeCasablanca: string;
  adresseIp: string;
  cabinetEmail: string | null;
}

export interface VersionVue {
  id: string;
  code: string;
  entreeEnVigueur: string;
  empreinteModele: string;
  empreinteCg: string;
  empreinteDpa: string;
  origine: 'INITIALE' | 'SUPER_ADMIN';
  publieeLe: string;
  /** Version retiree (jamais presentee a un cabinet ; visible du seul super-admin). */
  retiree: boolean;
}

/** Reponse du cabinet a une nouvelle version des conditions generales (article 16). */
export interface ReponseVersion {
  id: string;
  conditionsId: string;
  versionCode: string;
  reponse: 'ACCEPTEE' | 'REFUSEE';
  acceptationId: string | null;
  signataireCompte: string;
  reponduLeCasablanca: string;
  adresseIp: string;
}

export interface ResiliationVue {
  conditionsId: string;
  demandeurRole: 'SUPERVISEUR' | 'SUPER_ADMIN';
  dateEffet: string;
  motif: string;
  enregistreeLe: string;
}

export interface Reprise {
  civilite: string | null;
  qualite: string | null;
  formeCabinet: string | null;
  rcVilleCabinet: string | null;
}

export interface EtatContrat {
  statut: StatutContrat;
  acceptationRequise: boolean;
  objetAAccepter: ObjetAcceptation | null;
  enAttente: ConditionsVue | null;
  courant: ConditionsVue | null;
  derniereAcceptation: AcceptationVue | null;
  resiliation: ResiliationVue | null;
  versionEnVigueur: VersionVue | null;
  versionAVenir: VersionVue | null;
  joursAvantEcheance: number | null;
  avertissementEcheance: boolean;
  reprise: Reprise | null;
}

/** Etat reduit servi a l'employe. */
export interface EtatContratEmploye {
  statut: StatutContrat;
  acceptationRequise: boolean;
}

export interface SignataireSaisie {
  civilite: string;
  qualite: string;
  formeCabinet: string;
  rcVilleCabinet: string;
}

export interface ApercuContrat {
  objet: ObjetAcceptation;
  conditions: ConditionsVue;
  versionCode: string;
  texte: string;
  empreinte: string;
  manquants: string[];
}

export interface Renouvellement {
  id: string;
  conditionsId: string;
  ancienneEcheance: string;
  nouvelleEcheance: string;
  renouveleLe: string;
}

export interface AvisContrat {
  id: string;
  workspaceId: string;
  type: 'COPIE_CONTRAT' | 'ECHEANCE_30_JOURS' | 'NOUVELLE_VERSION';
  cle: string;
  destinataire: string;
  statut: 'ENVOYE' | 'ECHEC';
  erreur: string | null;
  envoyeLe: string;
}

export interface HistoriqueContrat {
  conditions: ConditionsVue[];
  acceptations: AcceptationVue[];
  resiliations: ResiliationVue[];
  renouvellements: Renouvellement[];
  avis: AvisContrat[];
  reponses: ReponseVersion[];
}

export interface LigneAdminContrat {
  workspaceId: string;
  statut: StatutContrat;
  enAttente: ConditionsVue | null;
  courant: ConditionsVue | null;
  derniereAcceptation: AcceptationVue | null;
  resiliation: ResiliationVue | null;
  joursAvantEcheance: number | null;
  /** Nouvelle version en vigueur a laquelle le cabinet n'a pas encore repondu. */
  versionSansReponse: string | null;
  derniereReponse: ReponseVersion | null;
}

export interface ConditionsSaisie {
  forfait: Forfait;
  rythmePaiement: Rythme;
  prixHt: number;
  dateDebut: string;
  modePaiement: ModePaiement | null;
}

export type UsageEditeur = 'FACTURES' | 'CONTRAT';

export interface EditeurParametres {
  valeurs: Record<string, string | null>;
  tauxTvaPourcent: number;
  manquants: { champ: string; libelle: string; usages: UsageEditeur[] }[];
  modifieLe: string | null;
  modifiePar: string | null;
}

export interface EditeurTrace {
  champ: string;
  libelle: string;
  ancienneValeur: string | null;
  nouvelleValeur: string | null;
  modifiePar: string | null;
  modifieLe: string;
}

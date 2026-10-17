/**
 * Lot L7 (E5) : types de la facturation (miroir des records Java BillingDtos du billing-service et
 * de la grille publique d'auth-service). Montants en centimes de dirham ; prix du catalogue HORS
 * TAXES ; TVA au taux des parametres de l'editeur. Stripe est retire : le paiement par carte passe
 * par la page hebergee du CMI.
 */

/** Forfaits du cahier des charges, paragraphe 4.3. */
export type PlanCode = 'essentiel' | 'professionnel' | 'max' | 'sur_devis';
/** Forfaits souscriptibles en ligne (Sur Devis : demande de devis). */
export type CheckoutPlanCode = Exclude<PlanCode, 'sur_devis'>;

/** Rythme de paiement (RG-TAR-01, RG-TAR-02). */
export type BillingPeriod = 'monthly' | 'yearly';

/** Modes de paiement (RG-PAY-01). */
export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'CHEQUE' | 'CASH';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type ObjetPaiement = 'SOUSCRIPTION' | 'ECHEANCE' | 'CHANGEMENT_FORFAIT';

// ---------------------------------------------------------------- grille publique

export interface PricingQuotas {
  /** Acces employes en plus du superviseur. */
  maxUsers: number;
  /** Dossiers ouverts par mois d'abonnement. */
  maxDossiers: number;
  maxStorageGb: number;
  usersUnlimited: boolean;
  dossiersUnlimited: boolean;
  storageUnlimited: boolean;
}

export interface FonctionTier {
  code: string;
  libelle: string;
  incluse: boolean;
}

export interface PricingTier {
  id: string;
  code: PlanCode;
  label: string;
  rang: number;
  currency: 'MAD';
  quoteOnly: boolean;
  monthly?: { priceMad: number };
  yearly?: { priceMad: number };
  totalAnnuelEnMensuelMad?: number;
  quotas: PricingQuotas;
  fonctions: FonctionTier[];
}

export interface PricingResponse {
  tiers: PricingTier[];
  currency: 'MAD';
  prixHorsTaxes: boolean;
}

// ---------------------------------------------------------------- usage

/** GET /api/v1/workspace/usage (PlanLimitsService.UsageSnapshot). */
export interface UsageSnapshot {
  planCode: string;
  planLabel: string;
  users: number;
  maxUsers: number;
  usersUnlimited: boolean;
  dossiers: number;
  maxDossiers: number;
  dossiersUnlimited: boolean;
  storageBytes: number;
  maxStorageGb: number;
  storageUnlimited: boolean;
  forfaitDefini?: boolean;
  periodeDebut?: string | null;
  periodeFin?: string | null;
  storageMesure?: boolean;
  /** Recette R1 (RG-ESS-07) : essai sans forfait souscrit ; le forfait affiche est l'essai. */
  essai?: boolean;
  /** Stockage maximal en octets (500 Mo pendant l'essai). */
  maxStorageOctets?: number;
}

// ---------------------------------------------------------------- abonnement

export interface MontantsVue {
  htCentimes: number;
  tauxTva: number;
  tvaCentimes: number;
  ttcCentimes: number;
}

export interface FonctionVue {
  code: string;
  libelle: string;
  incluse: boolean;
  disponibleDes: string;
}

export interface EcheanceVue {
  debut: string;
  fin: string | null;
  forfait: string;
  forfaitLibelle: string;
  rythme: BillingPeriod | null;
  montants: MontantsVue | null;
  motif: string | null;
}

export interface ChangementAVenirVue {
  forfait: string;
  forfaitLibelle: string;
  rythme: BillingPeriod | null;
  effectifLe: string;
}

/**
 * Lot L7 (E8) : AUCUN (jamais souscrit), ACTIF (une échéance impayée dans le délai de 15 jours est
 * décrite par `impaye`), SUSPENDU (impayé : accès suspendu, rétabli au paiement constaté, article
 * 4.4 du contrat), ARCHIVE (forfait arrêté : résiliation ; rien n'est supprimé).
 */
export type StatutAbonnement = 'AUCUN' | 'ACTIF' | 'SUSPENDU' | 'ARCHIVE';

/** Lot L7 (E8), article 4.4 du contrat : échéance impayée en cours. */
export interface ImpayeVue {
  echeance: string;
  /** Jour de la suspension : prévue, ou faite si `suspendu`. */
  suspensionLe: string;
  suspendu: boolean;
  suspenduLe: string | null;
  /** Envoi du rappel (null : pas encore parti). */
  rappelEnvoyeLe: string | null;
  /** Date d'arrêt du forfait si une résiliation est enregistrée (archivage à cette date). */
  arretLe: string | null;
  delaiJours: number;
}

/** Lot L7 (E8) : un fait du journal des impayés. */
export interface ImpayeEvenementVue {
  id: number;
  echeance: string;
  evenement: string;
  destinataire: string | null;
  detail: string | null;
  creeLe: string;
}

export interface AbonnementVue {
  workspaceId: string;
  statut: StatutAbonnement;
  forfait: string | null;
  forfaitLibelle: string | null;
  rythme: BillingPeriod | null;
  engagementDebut: string | null;
  engagementFin: string | null;
  periodeDebut: string | null;
  periodeFin: string | null;
  prochaineEcheance: EcheanceVue | null;
  changementAVenir: ChangementAVenirVue | null;
  archiveMotif: string | null;
  fonctions: FonctionVue[];
  carteDisponible: boolean;
  carteIndisponibleMotif: string | null;
  paiementsEnAttente: number;
  impaye: ImpayeVue | null;
}

export interface ChangementForfaitRequest {
  forfait: PlanCode;
  rythme?: BillingPeriod;
  mode?: PaymentMethod;
}

export type NatureChangement = 'HAUSSE' | 'BAISSE' | 'RYTHME' | 'IDENTIQUE';
export type EffetChangement = 'APRES_PAIEMENT' | 'IMMEDIAT' | 'PROCHAINE_ECHEANCE' | null;

export interface ApercuChangementVue {
  depuis: string;
  vers: string;
  versLibelle: string;
  rythmeActuel: BillingPeriod;
  rythmeVise: BillingPeriod;
  nature: NatureChangement;
  effet: EffetChangement;
  effectifLe: string | null;
  moisRestants: number | null;
  montants: MontantsVue | null;
  accepte: boolean;
  depassements: string[];
  message: string;
}

export interface ChangementForfaitVue {
  resultat: 'IDENTIQUE' | 'PAIEMENT_REQUIS' | 'APPLIQUE' | 'PROGRAMME';
  message: string;
  apercu: ApercuChangementVue;
  paiement: PreparationPaiementVue | null;
}

// ---------------------------------------------------------------- paiement

export interface PreparationPaiementRequest {
  objet: 'SOUSCRIPTION' | 'ECHEANCE';
  forfait?: CheckoutPlanCode;
  rythme?: BillingPeriod;
  mode: PaymentMethod;
  contactEmail?: string;
  inscription?: boolean;
}

/** Formulaire POST auto-soumis vers la page hebergee du CMI. */
export interface CmiFormulaireVue {
  url: string;
  champs: Record<string, string>;
}

export interface InstructionVue {
  libelle: string;
  valeur: string;
}

export interface PaiementVue {
  id: number;
  workspaceId: string;
  objet: ObjetPaiement | null;
  forfait: string;
  forfaitLibelle: string;
  rythme: BillingPeriod;
  mode: PaymentMethod;
  statut: PaymentStatus;
  montants: MontantsVue | null;
  periodeDebut: string | null;
  periodeFin: string | null;
  reference: string | null;
  recuLe: string | null;
  creeLe: string;
  acquisLe: string | null;
  factureId: number | null;
  factureNumero: string | null;
  factureRefus: string | null;
  activationPropagee: boolean;
  notes: string | null;
}

export interface PreparationPaiementVue {
  paiement: PaiementVue;
  cmi: CmiFormulaireVue | null;
  instructions: InstructionVue[];
  message: string;
}

export interface FactureVue {
  id: number;
  numero: string;
  workspaceId: string;
  cabinet: string | null;
  dateEmission: string;
  designation: string;
  periodeDebut: string | null;
  periodeFin: string | null;
  mode: string;
  montants: MontantsVue;
}

export interface ContactSalesRequest {
  workspaceName: string;
  contactEmail: string;
  contactName: string;
  phone: string;
  message: string;
}

export interface ContactSalesResponse {
  accepted: boolean;
  message: string;
}

// ---------------------------------------------------------------- super-admin

export interface EnregistrementPaiementRequest {
  workspaceId: string;
  objet: 'SOUSCRIPTION' | 'ECHEANCE';
  forfait?: PlanCode;
  rythme?: BillingPeriod;
  mode: Exclude<PaymentMethod, 'CARD'>;
  recuLe: string;
  reference: string;
  montantHtCentimes?: number | null;
  notes?: string;
}

export interface ValidationPaiementRequest {
  recuLe: string;
  reference: string;
  notes?: string;
}

export interface SurDevisVue {
  dossiersDuMois: number;
  moisDebut: string | null;
  moisFin: string | null;
  prixParDossierHt: number | null;
  montantHt: number | null;
  motif: string | null;
  repereMaxParDossierMensuel: number | null;
  repereMaxParDossierAnnuel: number | null;
}

export interface AbonnementAdminVue {
  workspaceId: string;
  abonnementId: number;
  statut: 'ACTIF' | 'SUSPENDU' | 'ARCHIVE';
  forfait: string;
  forfaitLibelle: string;
  rythme: BillingPeriod | null;
  engagementDebut: string | null;
  engagementFin: string | null;
  periodeDebut: string | null;
  periodeFin: string | null;
  prochaineEcheance: EcheanceVue | null;
  changementAVenir: ChangementAVenirVue | null;
  archiveMotif: string | null;
  surDevis: SurDevisVue | null;
  impaye: ImpayeVue | null;
  journalImpaye: ImpayeEvenementVue[];
}

export interface BilanTacheVue {
  traites: number;
  reussis: number;
  echecs: number;
  details: string[];
}

// ---------------------------------------------------------------- libelles et formats

export const LIBELLE_MODE: Record<PaymentMethod, string> = {
  CARD: 'Carte bancaire (CMI)',
  BANK_TRANSFER: 'Virement bancaire',
  CHEQUE: 'Chèque',
  CASH: 'Espèces',
};

export const LIBELLE_STATUT_PAIEMENT: Record<PaymentStatus, string> = {
  PENDING: 'En attente',
  COMPLETED: 'Acquis',
  FAILED: 'Refusé',
  CANCELLED: 'Annulé',
};

export const LIBELLE_OBJET: Record<ObjetPaiement, string> = {
  SOUSCRIPTION: 'Souscription',
  ECHEANCE: 'Échéance',
  CHANGEMENT_FORFAIT: 'Changement de forfait',
};

export const LIBELLE_RYTHME: Record<BillingPeriod, string> = {
  monthly: 'Paiement mensuel',
  yearly: 'Paiement annuel',
};

/** Lot L7 (E8) : faits du journal des impayés. */
export const LIBELLE_EVENEMENT_IMPAYE: Record<string, string> = {
  RAPPEL_ENVOYE: 'Rappel envoyé',
  RAPPEL_ECHEC: 'Rappel non envoyé',
  SUSPENSION: 'Accès suspendu',
  SUSPENSION_ECHEC: 'Suspension non faite (erreur)',
  SUSPENSION_SANS_RAPPEL: 'Suspension non faite : aucun rappel envoyé',
  RETABLISSEMENT: 'Accès rétabli (paiement constaté)',
};

export const LIBELLE_MOTIF_ARCHIVAGE: Record<string, string> = {
  IMPAYE: 'échéance impayée',
  FIN_ABONNEMENT: 'abonnement arrivé à échéance sans renouvellement',
  RESILIATION: 'résiliation',
  DECISION: 'décision de JURIKA',
};

/** Montant en centimes MAD vers "1 299,00 DH". */
export function formatMadCents(cents: number): string {
  const entier = Math.floor(Math.abs(cents) / 100);
  const cts = Math.abs(cents) % 100;
  const groupe = entier.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return `${cents < 0 ? '-' : ''}${groupe},${cts.toString().padStart(2, '0')} DH`;
}

/** Prix en dirhams entiers vers "3 750 DH". */
export function formatMad(mad: number): string {
  return `${mad.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ')} DH`;
}

/** Date ISO (AAAA-MM-JJ) vers JJ/MM/AAAA, sans fuseau. */
export function formatDateIso(iso: string | null | undefined): string {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  return `${m[3]}/${m[2]}/${m[1]}`;
}

/** Fin de periode exclue (AAAA-MM-JJ) vers le dernier jour inclus, JJ/MM/AAAA. */
export function formatFinIncluse(iso: string | null | undefined): string {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]) - 1));
  return `${String(d.getUTCDate()).padStart(2, '0')}/${String(d.getUTCMonth() + 1).padStart(2, '0')}/${d.getUTCFullYear()}`;
}

/** Erreur structuree du serveur (refus nomme 422, quota 402...). */
export interface ErreurFacturation {
  code: string | null;
  message: string;
  elements: string[];
}

export function lireErreurFacturation(err: unknown, defaut: string): ErreurFacturation {
  const e = err as { response?: { data?: { code?: string; message?: string; elements?: string[] } } };
  const d = e?.response?.data;
  return {
    code: d?.code ?? null,
    message: d?.message || defaut,
    elements: Array.isArray(d?.elements) ? d.elements : [],
  };
}

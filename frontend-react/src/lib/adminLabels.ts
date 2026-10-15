/**
 * Libelles francais des codes affiches dans l'administration de la plateforme
 * (fiche d'un cabinet, facturation). Un code inconnu
 * s'affiche tel quel : il signale un libelle a ajouter, sans masquer la donnee.
 */

// Lot L6b (E9) : les libelles des actions du journal d'audit ne sont plus ici ; le
// serveur les rend (actionLabel, source unique jurika-common AuditActionLabels) et
// l'ecran les affiche par lib/libelleAction.

/** Statut de l'essai gratuit d'un cabinet (colonne workspaces.trial_status). */
export const STATUT_ESSAI_LABELS: Record<string, string> = {
  TRIAL_ACTIVE: 'En cours',
  TRIAL_EXPIRED: 'Terminé',
  CONVERTED: 'Converti en abonnement',
  CANCELLED: 'Annulé',
};

/** Statut de l'abonnement (miroir des statuts du prestataire de paiement). */
export const STATUT_ABONNEMENT_LABELS: Record<string, string> = {
  active: 'Actif',
  past_due: 'Paiement en retard',
  cancelled: 'Résilié',
  trialing: 'Période d’essai',
  incomplete: 'Paiement à finaliser',
  incomplete_expired: 'Paiement non finalisé',
  unpaid: 'Impayé',
};

/** Libelle d'un forfait a partir de son code (lot L7 : grille du CDC 4.3). */
const FORFAIT_CODE_LABELS: Record<string, string> = {
  essentiel: 'Essentiel',
  professionnel: 'Professionnel',
  max: 'Max',
  sur_devis: 'Sur Devis',
};

export function libelleForfait(code: string | null | undefined): string {
  if (!code) return '—';
  return FORFAIT_CODE_LABELS[code.toLowerCase()] ?? code;
}

export function libelleCode(labels: Record<string, string>, code: string | null | undefined): string | null {
  if (!code) return null;
  return labels[code] ?? code;
}

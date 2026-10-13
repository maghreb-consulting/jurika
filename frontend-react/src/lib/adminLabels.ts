/**
 * Libelles francais des codes affiches dans l'administration de la plateforme
 * (journal d'audit, fiche d'un cabinet, facturation). Un code inconnu
 * s'affiche tel quel : il signale un libelle a ajouter, sans masquer la donnee.
 */

/** Actions tracees du journal d'audit. */
export const ACTION_AUDIT_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Connexion réussie',
  LOGIN_FAILED: 'Connexion échouée',
  LOGOUT: 'Déconnexion',
  PASSWORD_CHANGED: 'Mot de passe changé',
  PASSWORD_RESET_REQUESTED: 'Réinitialisation du mot de passe demandée',
  // Lot L5 (E10) : gestion de son compte.
  USER_NAME_CHANGED: 'Prénom ou nom modifié',
  CONTACT_EMAIL_CHANGE_REQUESTED: 'Changement d’adresse demandé',
  CONTACT_EMAIL_CHANGED: 'Adresse électronique modifiée',
  LOGIN_EMAIL_CHANGE_REQUESTED: 'Changement d’identifiant demandé',
  LOGIN_EMAIL_CHANGED: 'Identifiant de connexion modifié',
  ACCOUNT_CHANGE_CANCELLED: 'Demande de changement annulée',
  WORKSPACE_REGISTER: 'Inscription d’un cabinet',
  WORKSPACE_ACTIVATED: 'Cabinet activé',
  WORKSPACE_SUSPENDED: 'Cabinet suspendu',
  WORKSPACE_DEACTIVATED: 'Cabinet désactivé',
  USER_SUSPENDED: 'Utilisateur suspendu',
  USER_REACTIVATED: 'Utilisateur réactivé',
};

export function libelleActionAudit(action: string): string {
  return ACTION_AUDIT_LABELS[action] ?? action;
}

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

/** Libelle d'un forfait a partir de son code (essentiel, business, entreprise...). */
const FORFAIT_CODE_LABELS: Record<string, string> = {
  essentiel: 'Essentiel',
  business: 'Business',
  professionnel: 'Professionnel',
  entreprise: 'Entreprise',
};

export function libelleForfait(code: string | null | undefined): string {
  if (!code) return '—';
  return FORFAIT_CODE_LABELS[code.toLowerCase()] ?? code;
}

export function libelleCode(labels: Record<string, string>, code: string | null | undefined): string | null {
  if (!code) return null;
  return labels[code] ?? code;
}

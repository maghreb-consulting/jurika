/**
 * Libelles FR partages par les graphiques des tableaux de bord.
 * Source unique de verite pour l'affichage humain des enums backend
 * (statuts tickets, types de workflow, statuts demandes, forfaits, statuts WS).
 */

export const STATUT_TICKET_LABELS: Record<string, string> = {
  NOUVEAU: 'Nouveau',
  EN_COURS: 'En cours',
  CLOTURE: 'Clôturé',
  ANNULE: 'Annulé',
};

export const TYPE_TICKET_LABELS: Record<string, string> = {
  CREATION: 'Création',
  IMPORT: 'Import',
  MODIFICATION: 'Modification',
  DISSOLUTION: 'Dissolution',
  LIQUIDATION: 'Liquidation',
  SUCCURSALE_MA: 'Succursale MA',
  SUCCURSALE_ETR: 'Succursale étr.',
  FERMETURE_SUCCURSALE: 'Fermeture succ.',
  PV_AGO: 'PV AGO',
};

export const STATUT_DEMANDE_LABELS: Record<string, string> = {
  NON_TRAITEE: 'Non traitée',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitée',
};

export const FORFAIT_LABELS: Record<string, string> = {
  essentiel: 'Essentiel',
  professionnel: 'Professionnel',
  entreprise: 'Entreprise',
  non_defini: 'Non défini',
};

export const STATUT_WORKSPACE_LABELS: Record<string, string> = {
  ACTIVE: 'Actif',
  ESSAI: 'Essai',
  SUSPENDED: 'Suspendu',
  DEACTIVATED: 'Désactivé',
};

/** Renvoie le libelle FR d'une cle via une map, avec repli sur la cle brute. */
export function labelOf(map: Record<string, string>, key: string): string {
  return map[key] ?? key;
}

/** Formate un mois "YYYY-MM" en "MMM YY" FR court (ex. "2026-07" -> "juil. 26"). */
export function formatMonth(month: string): string {
  const [y, m] = month.split('-');
  if (!y || !m) return month;
  const d = new Date(Number(y), Number(m) - 1, 1);
  return d.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
}

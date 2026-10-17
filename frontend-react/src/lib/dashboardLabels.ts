/**
 * Libelles FR partages par les graphiques des tableaux de bord.
 * Source unique de verite pour l'affichage humain des enums backend
 * (statuts tickets, types de workflow, statuts demandes, forfaits, statuts WS).
 */

export const STATUT_TICKET_LABELS: Record<string, string> = {
  CREATION_TICKET: "Création du ticket et collecte d'information",
  GENERATION_DOCUMENTS: 'Génération des documents',
  DEROULEMENT_DEMARCHE: 'Déroulement de la démarche',
  CLOTURE_DOSSIER: 'Clôture de dossier',
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
  max: 'Max',
  sur_devis: 'Sur Devis',
  non_defini: 'Non défini',
};

export const STATUT_WORKSPACE_LABELS: Record<string, string> = {
  ACTIVE: 'Actif',
  ESSAI: 'Essai',
  SUSPENDED: 'Suspendu',
  DEACTIVATED: 'Désactivé',
  // Lot L7 (E5), RG-TAR-07 : forfait arrêté, rien n'est supprimé.
  ARCHIVED: 'Archivé',
  // Lot L6b (E17) : statut admis par la contrainte d'auth V44, absent jusque-là (code brut).
  PENDING_VERIFICATION: 'En attente de vérification',
};

/**
 * Lot L6b (E17) : services suivis par le tableau de bord du super-admin
 * (SuperAdminDashboardAggregator.MONITORED_SERVICES), nommés par leur fonction.
 */
export const SERVICE_LABELS: Record<string, string> = {
  'auth-service': 'Comptes et connexions',
  'ticket-service': 'Tickets et démarches',
  'workflow-service': 'Parcours',
  'dataroom-service': 'Data Rooms',
  'ai-service': 'Génération des actes et assistant',
  'supervision-service': 'Supervision',
  'realtime-service': 'Notifications en temps réel',
};

/** Lot L6b (E17) : type d'élément d'une trace du journal (colonne entity_type, en minuscules). */
export const ENTITE_AUDIT_LABELS: Record<string, string> = {
  user: 'Compte utilisateur',
  ticket: 'Ticket',
  workspace: 'Cabinet',
  dossier: 'Dossier',
  document: 'Document',
  dashboard: 'Tableau de bord',
};

/**
 * Lot L6b (E17) : motif d'un dossier à risque rendu par le serveur (SuperviseurDashboardAggregator,
 * texte sans accents) ; un motif inconnu s'affiche tel quel.
 */
export const MOTIF_RISQUE_LABELS: Record<string, string> = {
  'Delai legal depasse': 'Délai légal dépassé',
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

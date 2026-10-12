/**
 * Lot L4d (E4) : usages du cabinet decides par le directeur le 11 octobre 2026.
 * Ce sont des valeurs PROPOSEES (composant ValeurProposee) : l'employe les confirme ou les
 * remplace ; rien n'est transmis sans confirmation.
 *
 * - 4 exemplaires originaux (en chiffres pour les statuts et la refonte, en lettres dans
 *   les PV qui l'impriment ainsi) ;
 * - l'acte est passe a 10 heures ;
 * - a defaut de signataire designe, les gerants signent (regle appliquee par le serveur :
 *   boucle SIGNATAIRES, CreationDirecteurVarsBuilder).
 */
export const USAGES_CABINET = {
  nombreOriginaux: '4',
  nombreOriginauxEnLettres: 'quatre',
  heureActe: '10 heures',
} as const;

export const AIDE_SIGNATAIRES =
  'À défaut de signataire désigné, les gérants signent les actes (usage du cabinet du 11 octobre 2026).';

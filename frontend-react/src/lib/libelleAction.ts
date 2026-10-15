/**
 * Lot L6b (E9) : libelle d'une action tracee (journal d'audit, activite du dossier,
 * activite du client). Le libelle est calcule par le SERVEUR (source unique :
 * jurika-common AuditActionLabels) et affiche tel quel ; l'ecran ne traduit jamais
 * un code lui-meme. S'il manque (reponse d'un serveur plus ancien), l'absence est
 * dite, comme le fait le serveur pour un code inconnu : jamais un faux libelle
 * fabrique a partir du code.
 */
export function libelleAction(actionLabel: string | null | undefined, action: string | null | undefined): string {
  if (actionLabel && actionLabel.trim()) return actionLabel;
  if (!action) return '';
  return `Action enregistrée (code ${action})`;
}

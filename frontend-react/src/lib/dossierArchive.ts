/**
 * Lot DIVERS §A (2026-08-13) — Data Room en LECTURE SEULE quand la societe n'est
 * plus vivante.
 *
 * <p>Miroir exact, cote front, de `DossierArchiveGuard.ARCHIVED_STATUS`
 * (dataroom-service). Le backend reste la seule autorite : ces helpers ne servent
 * qu'a ne pas proposer une action que le serveur refusera de toute facon (400
 * `DOSSIER_ARCHIVED_READ_ONLY`).
 *
 * <p>La regle est <b>derivee</b> du statut du dossier, jamais persistee : si la
 * societe redevient ACTIVE, la Data Room redevient ecrivable sans action
 * corrective.
 */

/** Statuts de societe qui figent la Data Room (miroir backend). */
export const ARCHIVED_DOSSIER_STATUS = [
  'DISSOUTE',
  'EN_LIQUIDATION',
  'LIQUIDEE',
  'RADIE',
] as const;

export type ArchivedDossierStatut = (typeof ARCHIVED_DOSSIER_STATUS)[number];

/** Vrai si la Data Room de ce dossier est en lecture seule. */
export function isDossierArchived(statut?: string | null): boolean {
  if (!statut) return false;
  return (ARCHIVED_DOSSIER_STATUS as readonly string[]).includes(statut);
}

/** Libelle court du statut, pour le bandeau et les tooltips. */
export function archivedStatutLabel(statut?: string | null): string {
  switch (statut) {
    case 'DISSOUTE':
      return 'Societe dissoute';
    case 'EN_LIQUIDATION':
      return 'Societe en liquidation';
    case 'LIQUIDEE':
      return 'Societe liquidee';
    case 'RADIE':
      return 'Societe radiee';
    default:
      return 'Societe archivee';
  }
}

/** Phrase du bandeau Data Room. */
export function dataroomReadOnlyMessage(statut?: string | null): string {
  return `${archivedStatutLabel(statut)} — Data Room en lecture seule`;
}

/**
 * Raison affichee en tooltip sur les actions desactivees (upload, suppression,
 * nouvelle demande…).
 */
export function dataroomReadOnlyReason(statut?: string | null): string {
  return (
    `${archivedStatutLabel(statut)} : la Data Room est une archive legale. ` +
    'Consultation et telechargement restent possibles ; ajout, remplacement et ' +
    'suppression sont desactives.'
  );
}

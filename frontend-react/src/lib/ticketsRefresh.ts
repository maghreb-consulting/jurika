/**
 * Bus léger (window CustomEvent) pour signaler que la liste des tickets doit
 * être rechargée, sans coupler des composants montés sur des routes différentes.
 *
 * Cas d'usage (2026-07-03) : quand un employé ACCEPTE un transfert de dossier
 * depuis le tableau de bord (PendingTransfersPanel), les tickets ouverts du
 * dossier lui sont réassignés côté backend. La page Tickets, si elle est déjà
 * montée, doit se rafraîchir pour les faire apparaître (assigne_id = lui).
 */
export const TICKETS_CHANGED_EVENT = 'jurika:tickets-changed';

/** Signale un changement d'affectation de tickets (ex. après acceptation d'un transfert). */
export function emitTicketsChanged(): void {
  window.dispatchEvent(new CustomEvent(TICKETS_CHANGED_EVENT));
}

/** S'abonne au signal ; renvoie une fonction de désabonnement. */
export function onTicketsChanged(handler: () => void): () => void {
  window.addEventListener(TICKETS_CHANGED_EVENT, handler);
  return () => window.removeEventListener(TICKETS_CHANGED_EVENT, handler);
}

/**
 * Lot L6b (E4) : constantes partagees du harnais visuel (configuration et tests).
 */

/**
 * Adresse de l'API inscrite au build : un domaine reserve (.invalid, RFC 2606) qui ne se
 * resout jamais. Une requete qui echapperait a la simulation ne pourrait atteindre aucun
 * serveur reel (sur le Z440, localhost:8080 est la vraie passerelle).
 */
export const API_SIMULEE = 'http://api.jurika-visuel.invalid/api/v1';

/** Instant fige de toutes les executions (les echeances se calculent par rapport a lui). */
export const MAINTENANT = new Date('2026-10-15T09:00:00+01:00');

/** Largeurs d'ecran verifiees (demande, point 6) et hauteur commune. */
export const LARGEURS = [1366, 1920] as const;
export const HAUTEUR = 900;

export const THEMES = ['clair', 'sombre'] as const;
export type Theme = (typeof THEMES)[number];

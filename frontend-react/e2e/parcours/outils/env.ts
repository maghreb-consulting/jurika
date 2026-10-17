/**
 * Lot L6b (E13) : adresses de la pile REELLE visee par les parcours de bout en bout.
 *
 *   E2E_BASE_URL   origine du front (defaut http://192.168.1.251, le Z440). Les pages s'ouvrent
 *                  toujours depuis cette origine : la passerelle n'autorise (CORS) que l'origine
 *                  du front, jamais 127.0.0.1 si le front est servi ailleurs.
 *   E2E_API_URL    racine de l'API (defaut http://192.168.1.251:8080/api/v1).
 *   E2E_SEED_URL   racine de la route de seed de test (defaut : E2E_API_URL).
 */
export const BASE_URL = (process.env.E2E_BASE_URL ?? 'http://192.168.1.251').replace(/\/+$/, '');
export const API_URL = (process.env.E2E_API_URL ?? 'http://192.168.1.251:8080/api/v1').replace(/\/+$/, '');
export const SEED_URL = (process.env.E2E_SEED_URL ?? API_URL).replace(/\/+$/, '');

/** Dossier des captures de fin de parcours (pour le rapport). */
export const DOSSIER_CAPTURES = process.env.E2E_CAPTURES ?? 'test-results/parcours';

/** Chemin d'API (sans la racine) d'une adresse complete ; null si l'adresse n'est pas l'API. */
export function cheminApi(url: string): string | null {
  if (!url.startsWith(API_URL)) return null;
  return url.slice(API_URL.length).split('?')[0] || '/';
}

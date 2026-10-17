/**
 * Lot L6c (E2, demande du 2026-10-17) : logo anime en boucle de la vitrine.
 *
 * Duree d'un cycle de l'animation en boucle de l'embleme (EmblemeAnime.tsx, vitrine.css :
 * classes v-embleme-reflet, v-embleme-etoile, v-embleme-halo). La demande fixe un cycle de 8
 * a 10 secondes ; LandingMouvement.test.tsx verifie que vitrine.css emploie cette duree.
 */
export const CYCLE_LOGO_S = 9;

/** Delai avant le premier cycle : le dessin d'entree de l'embleme (2 s) est fini. */
export const DEPART_BOUCLE_LOGO_S = 2;

import { CHAMPS_CREATION } from '../workflow/steps/documents-creation.generated';

/**
 * Lot L6b (E7, backlog L6) : libelles des donnees saisies, par nom de variable, pour la page
 * du ticket. Source : le catalogue des champs genere depuis le dictionnaire unique (le meme
 * que l'etape de generation, Step7Generation). Aucun libelle n'est invente : une variable
 * absente du catalogue garde son nom technique (ProvenanceDonnees l'affiche alors en mono).
 */
export const LIBELLES_DONNEES: Record<string, string> = Object.fromEntries(
  CHAMPS_CREATION.map((c) => [c.variable, c.label]),
);

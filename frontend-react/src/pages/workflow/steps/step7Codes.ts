/**
 * Etape 7 de la creation (lot L6) : fonctions pures sorties de Step7Generation.tsx, pour
 * que ce dernier n'exporte que des composants (rafraichissement a chaud de Vite).
 */
import { CHOIX_STATUT_2 } from './documents-creation';

/**
 * Lot B — LES CODES DE MODÈLE QUE LES LIGNES RETENUES IMPLIQUENT.
 *
 * Une ligne du parcours porte un ou deux modèles. Quand elle en porte deux,
 * l'employé ne choisit pas : la forme juridique décide entre SARL et SARL AU, la
 * voie retenue pour le siège décide entre bail et domiciliation. Si la donnée
 * n'est pas encore là, on retient les deux variantes plutôt qu'aucune — mieux
 * vaut un champ de trop qu'un document qu'on ne sait plus produire.
 */
export function codesRetenus(
  lignes: Set<number>,
  formeJuridique: string | undefined,
  voieSiege: string | undefined,
): string[] {
  const out: string[] = [];
  for (const choix of CHOIX_STATUT_2) {
    if (!lignes.has(choix.ligne)) continue;
    if (choix.codes.length === 1) {
      out.push(choix.codes[0]);
      continue;
    }
    const variante = choix.codes.find((code) => {
      if (code === 'STATUTS_SARL') return formeJuridique === 'SARL';
      if (code === 'STATUTS_SARL_AU') return formeJuridique === 'SARL_AU';
      if (code === 'CONTRAT_BAIL') return voieSiege === 'BAIL';
      if (code === 'CONTRAT_DOMICILIATION') return voieSiege === 'DOMICILIATION';
      return false;
    });
    if (variante) out.push(variante);
    else out.push(...choix.codes);
  }
  return out;
}

/** `BENEFICIAIRES_EFFECTIFS` -> `beneficiairesEffectifs`, comme côté serveur. */
export function cleDeBoucle(nom: string): string {
  const mots = nom.toLowerCase().split('_');
  return mots[0] + mots.slice(1).map((m) => m.charAt(0).toUpperCase() + m.slice(1)).join('');
}

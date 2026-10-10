/**
 * Lot L3 (RG-VAR-02, RG-VAR-09 ; D14 de L1) : provenance EXTRAITE.
 *
 * Sur l'objet saisi (etape 1, dirigeant, associe), `_extraits` liste les champs remplis
 * par la lecture d'une piece (CIN, certificat negatif) et confirmes par l'employe. Le
 * serveur les enregistre avec la provenance « extraite » au magasin. Un champ modifie a
 * la main ensuite sort de la liste : il redevient une saisie.
 */
export const CLE_EXTRAITS = '_extraits';

export function ajouterExtraits(existants: unknown, champs: string[]): string[] {
  const base = Array.isArray(existants) ? (existants as string[]) : [];
  return Array.from(new Set([...base, ...champs]));
}

export function retirerExtraits(existants: unknown, champs: string[]): string[] | undefined {
  if (!Array.isArray(existants)) return undefined;
  const reste = (existants as string[]).filter((c) => !champs.includes(c));
  return reste;
}

import type { LiquidateurState } from '../components/workflow/LiquidateurPicker';

/**
 * Lot L4d (E5, décision du directeur du 11 octobre 2026) : liquidateur personne physique ou
 * personne morale — identité, ce qui manque (message nommé), champs propres transmis.
 */
/** Lot L4d (E5) : le liquidateur désigné est-il une personne morale ? */
export function estPersonneMorale(l: LiquidateurState): boolean {
  return l.forme === 'morale';
}

/** Identité lisible du liquidateur : dénomination, ou civilité, prénom et nom. */
export function identiteLiquidateur(l: LiquidateurState): string {
  if (estPersonneMorale(l)) return (l.denomination ?? '').trim();
  return [l.civilite, l.prenom, l.nom].filter((x) => (x ?? '').trim()).join(' ').trim();
}

/**
 * Ce qui manque pour désigner le liquidateur (message nommé), ou null. Les autres données
 * (RC, ICE, capital…) sont nommées à la génération si le modèle les imprime.
 */
export function manqueLiquidateur(l: LiquidateurState): string | null {
  if (estPersonneMorale(l)) {
    if (!(l.denomination ?? '').trim()) return 'Indiquez la dénomination du liquidateur personne morale.';
    if (!(l.siegeSocial ?? '').trim()) return 'Indiquez le siège social du liquidateur personne morale : il figure dans l’annonce légale.';
    if (!(l.representantNom ?? '').trim()) return 'Indiquez le représentant permanent du liquidateur personne morale.';
    return null;
  }
  if (!l.nom.trim()) return 'Désignez le liquidateur (gérant ou associé existant, ou tiers).';
  if (!l.adresse.trim()) return "L'adresse du liquidateur est obligatoire : elle figure dans l'annonce légale.";
  return null;
}

/** Champs propres à la personne morale, à joindre aux charges du liquidateur (vide sinon). */
export function champsPersonneMorale(l: LiquidateurState): Record<string, string> {
  if (!estPersonneMorale(l)) return {};
  return {
    forme: 'morale',
    denomination: (l.denomination ?? '').trim(),
    formeJuridique: (l.formeJuridique ?? '').trim(),
    capital: (l.capital ?? '').trim(),
    siegeSocial: (l.siegeSocial ?? '').trim(),
    rcVille: (l.rcVille ?? '').trim(),
    rcNumero: (l.rcNumero ?? '').trim(),
    ice: (l.ice ?? '').trim(),
    representantNom: (l.representantNom ?? '').trim(),
    representantQualite: (l.representantQualite ?? '').trim(),
  };
}

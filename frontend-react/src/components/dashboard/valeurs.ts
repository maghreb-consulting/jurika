/**
 * Lot L6b (E9) : lecture des donnees des tableaux de bord. Une donnee absente s'affiche comme
 * absente ("--"), jamais par un zero invente (motif 9) ni par un plantage.
 */
export const ABSENT = '--';

/** Nombre affichable : null / undefined / NaN => ABSENT. */
export function valeurOuAbsent(v: number | string | null | undefined): string | number {
  if (v === null || v === undefined) return ABSENT;
  if (typeof v === 'number' && !Number.isFinite(v)) return ABSENT;
  return v;
}

/** Nombre decimal a la francaise ("12,3"), ou ABSENT. */
export function decimal(v: number | null | undefined, chiffres = 1): string {
  if (typeof v !== 'number' || !Number.isFinite(v)) return ABSENT;
  return v.toLocaleString('fr-FR', { minimumFractionDigits: chiffres, maximumFractionDigits: chiffres });
}

/** Longueur d'une liste, ou null si la liste n'a pas ete rendue. */
export function longueur(liste: unknown[] | null | undefined): number | null {
  return Array.isArray(liste) ? liste.length : null;
}

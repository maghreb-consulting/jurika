/**
 * Helpers purs de repartition du capital entre associes — Sprint 2026-06-19.
 *
 * Probleme : lorsque le capital (ou le nombre total de parts) n'est pas
 * divisible egalement entre N associes, l'arrondi standard
 * (parts × valeurNominale) cree un decalage entre Sigma(apports) et le
 * capital social (ex. 72333 + 30999 = 103332 != 103333).
 *
 * Solution adoptee (consigne Cowork) : les N-1 premiers associes gardent
 * l'arrondi standard, le DERNIER absorbe le reliquat — pour les parts
 * comme pour les apports. La sortie satisfait toujours :
 *   - Sigma(distributedShares)     == totalShares
 *   - Sigma(distributedAmounts)    == totalCapital
 *
 * Ces fonctions sont pures (aucune dependance React) et testables en isolation.
 */

/**
 * Repartit equitablement {@code totalShares} parts entre N associes.
 * Les N-1 premiers recoivent {@code floor(totalShares / N)} ; le dernier
 * absorbe le reliquat. Renvoie un tableau de longueur N.
 *
 * Cas particuliers :
 *   - N <= 0   -> tableau vide
 *   - N == 1   -> [totalShares]
 *   - totalShares == 0 -> tableau de zeros
 */
export function distributeSharesEvenly(totalShares: number, n: number): number[] {
  if (n <= 0) return [];
  const safeTotal = Math.max(0, Math.floor(totalShares));
  if (n === 1) return [safeTotal];
  const base = Math.floor(safeTotal / n);
  const out: number[] = new Array(n).fill(base);
  let remainder = safeTotal - base * n;
  // Tout le reliquat va au dernier (consigne Cowork).
  out[n - 1] += remainder;
  return out;
}

/**
 * Calcule les montants d'apport AFFICHES par associe, en honorant la regle
 * "dernier absorbe le reliquat" lorsque cela est applicable.
 *
 * Regles :
 *  1. Si {@code totalCapital} ou {@code valeurNominale} sont manquants /
 *     non positifs : on retombe sur l'arrondi standard (parts*VN) par associe.
 *  2. Si la somme des parts saisies != {@code totalShares} (cible Step3) :
 *     idem, on n'ajuste pas — l'employe doit d'abord equilibrer.
 *  3. Sinon : les N-1 premiers recoivent {@code round2(parts_i * VN)} ;
 *     le dernier recoit {@code totalCapital - Sigma(N-1 premiers)}.
 *
 * Cette fonction garantit, dans le cas (3), que la somme rendue est
 * EXACTEMENT egale a {@code totalCapital} (au centime pres) — meme si la
 * valeur nominale n'est pas entiere.
 *
 * @param sharesPerAssocie nombre de parts saisies par associe (ordre stable)
 * @param valeurNominale   valeur nominale d'une part (MAD)
 * @param totalCapital     capital social total cible (MAD)
 * @param totalShares      nombre total de parts cible (Step3)
 */
export function computeDistributedAmounts(
  sharesPerAssocie: ReadonlyArray<number>,
  valeurNominale: number | undefined,
  totalCapital: number | undefined,
  totalShares: number | undefined,
): number[] {
  const n = sharesPerAssocie.length;
  if (n === 0) return [];

  const fallback = () =>
    sharesPerAssocie.map((p) =>
      valeurNominale && valeurNominale > 0
        ? round2(p * valeurNominale)
        : 0,
    );

  if (!valeurNominale || valeurNominale <= 0) return fallback();
  if (!totalCapital || totalCapital <= 0) return fallback();

  const sumShares = sharesPerAssocie.reduce((s, p) => s + (Number(p) || 0), 0);
  // Si on n'a pas Sigma(parts) == totalShares, on ne peut pas determiner
  // de reliquat coherent — l'employe doit d'abord ajuster.
  if (typeof totalShares === 'number' && totalShares > 0 && sumShares !== totalShares) {
    return fallback();
  }

  // N == 1 : le total = le tout.
  if (n === 1) return [round2(totalCapital)];

  const result: number[] = [];
  let sumAutres = 0;
  for (let i = 0; i < n - 1; i++) {
    const m = round2((Number(sharesPerAssocie[i]) || 0) * valeurNominale);
    result.push(m);
    sumAutres += m;
  }
  const last = round2(totalCapital - sumAutres);
  result.push(last);
  return result;
}

/**
 * Repartit equitablement le capital social entre N associes : combine
 * {@link distributeSharesEvenly} pour les parts et
 * {@link computeDistributedAmounts} pour les montants.
 *
 * Renvoie deux tableaux paralleles ({@code shares}, {@code amounts}) de
 * longueur N. Garantit Sigma(shares) == totalShares ET
 * Sigma(amounts) == totalCapital (le dernier associe absorbe les
 * reliquats parts ET montant).
 */
export function distributeEvenly(
  n: number,
  totalShares: number,
  totalCapital: number,
  valeurNominale: number | undefined,
): { shares: number[]; amounts: number[] } {
  const shares = distributeSharesEvenly(totalShares, n);
  const amounts = computeDistributedAmounts(shares, valeurNominale, totalCapital, totalShares);
  return { shares, amounts };
}

/**
 * Arrondi 2 decimales (centimes) — evite les artefacts flottants
 * du type 30999.000000001.
 */
function round2(x: number): number {
  if (!Number.isFinite(x)) return 0;
  return Math.round(x * 100) / 100;
}

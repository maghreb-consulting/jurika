import { describe, expect, it } from 'vitest';
import {
  computeDistributedAmounts,
  distributeEvenly,
  distributeSharesEvenly,
} from '../capital-distribution';

describe('distributeSharesEvenly — reliquat au dernier', () => {
  it('parts divisibles : tous egaux', () => {
    expect(distributeSharesEvenly(1000, 4)).toEqual([250, 250, 250, 250]);
  });

  it('parts non divisibles : dernier absorbe', () => {
    // 1033 parts / 3 = 344 reste 1 -> [344, 344, 345]
    expect(distributeSharesEvenly(1033, 3)).toEqual([344, 344, 345]);
  });

  it('N=1 : tout au seul associe', () => {
    expect(distributeSharesEvenly(103333, 1)).toEqual([103333]);
  });

  it('N=0 : tableau vide', () => {
    expect(distributeSharesEvenly(1000, 0)).toEqual([]);
  });

  it('total=0 : que des zeros', () => {
    expect(distributeSharesEvenly(0, 3)).toEqual([0, 0, 0]);
  });
});

describe('computeDistributedAmounts — Σ exacte = capital', () => {
  it('valeur nominale entiere divisible : aucun reliquat', () => {
    const amounts = computeDistributedAmounts([600, 400], 100, 100000, 1000);
    expect(amounts).toEqual([60000, 40000]);
    expect(amounts.reduce((s, x) => s + x, 0)).toBe(100000);
  });

  it('cas Cowork 103333 MAD reparti 1033 parts (VN=100.0319...) : dernier absorbe', () => {
    // capital=103333, nbParts=1033, valeurNominale=103333/1033 ≈ 100.0319458...
    const vn = 103333 / 1033;
    // 2 associes : 723 + 310 = 1033 parts
    const amounts = computeDistributedAmounts([723, 310], vn, 103333, 1033);
    // Le 1er a son arrondi standard, le 2eme absorbe le reliquat.
    expect(amounts.length).toBe(2);
    expect(amounts[0]).toBe(Math.round(723 * vn * 100) / 100);
    // Σ = capital exactement
    expect(amounts[0] + amounts[1]).toBe(103333);
  });

  it('reliquat distribue meme avec 3 associes', () => {
    // 1033 parts -> 344, 344, 345 ; capital 103333
    const vn = 103333 / 1033;
    const amounts = computeDistributedAmounts([344, 344, 345], vn, 103333, 1033);
    expect(amounts.reduce((s, x) => s + x, 0)).toBe(103333);
  });

  it('fallback si Σ(parts) != nbPartsTotal', () => {
    // Parts incompletes -> on ne touche pas (l'employe doit equilibrer)
    const amounts = computeDistributedAmounts([500, 400], 100, 100000, 1000);
    expect(amounts).toEqual([50000, 40000]);
  });

  it('fallback si valeurNominale manquante', () => {
    const amounts = computeDistributedAmounts([600, 400], undefined, 100000, 1000);
    expect(amounts).toEqual([0, 0]);
  });

  it('fallback si capital manquant', () => {
    const amounts = computeDistributedAmounts([600, 400], 100, undefined, 1000);
    expect(amounts).toEqual([60000, 40000]);
  });

  it('N=1 : associe unique recoit le capital', () => {
    expect(computeDistributedAmounts([1000], 100, 100000, 1000)).toEqual([100000]);
  });
});

describe('distributeEvenly — repartition auto complete', () => {
  it('103333 MAD / 2 associes : Σ exact + reliquat dernier', () => {
    const vn = 103333 / 1033;
    const { shares, amounts } = distributeEvenly(2, 1033, 103333, vn);
    expect(shares).toEqual([516, 517]);
    expect(shares[0] + shares[1]).toBe(1033);
    expect(amounts[0] + amounts[1]).toBe(103333);
  });

  it('100000 MAD / 3 associes : reliquat aligne sur centimes', () => {
    const { shares, amounts } = distributeEvenly(3, 1000, 100000, 100);
    expect(shares).toEqual([333, 333, 334]);
    expect(amounts.reduce((s, x) => s + x, 0)).toBe(100000);
  });
});

import { describe, expect, it } from 'vitest';
import { ajouterExtraits, retirerExtraits } from '../provenance';

/** Lot L3 (D14 de L1) : la liste des champs extraits suit l'extraction puis la saisie. */
describe('provenance EXTRAITE', () => {
  it('l extraction ajoute ses champs, la saisie manuelle retire le sien', () => {
    const apresExtraction = ajouterExtraits(undefined, ['nom', 'prenom', 'cin']);
    expect(apresExtraction).toEqual(['nom', 'prenom', 'cin']);
    expect(retirerExtraits(apresExtraction, ['prenom'])).toEqual(['nom', 'cin']);
    expect(retirerExtraits(undefined, ['nom'])).toBeUndefined();
    expect(ajouterExtraits(['nom'], ['nom', 'adresse'])).toEqual(['nom', 'adresse']);
  });
});

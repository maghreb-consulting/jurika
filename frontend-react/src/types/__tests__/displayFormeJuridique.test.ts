import { describe, expect, it } from 'vitest';
import { displayFormeJuridique } from '../dataroom';

/** Lot L6b : la forme juridique s'affiche lisiblement, jamais en code (vu a l'ecran : "SARL_AU"). */
describe('displayFormeJuridique', () => {
  it('rend un libelle court lisible pour chaque forme connue', () => {
    expect(displayFormeJuridique({ formeJuridique: 'SARL_AU', formeJuridiqueOrigine: null })).toBe('SARL AU');
    expect(displayFormeJuridique({ formeJuridique: 'SARL', formeJuridiqueOrigine: null })).toBe('SARL');
  });

  it('societe etrangere : la forme d origine, sinon une mention generale', () => {
    expect(displayFormeJuridique({ formeJuridique: 'ETRANGERE', formeJuridiqueOrigine: 'GmbH' })).toBe('GmbH');
    expect(displayFormeJuridique({ formeJuridique: 'ETRANGERE', formeJuridiqueOrigine: null })).toBe('Société étrangère');
  });

  it('un code inconnu n est jamais rendu avec un tiret bas', () => {
    expect(displayFormeJuridique({ formeJuridique: 'SNC_X' as never, formeJuridiqueOrigine: null })).toBe('SNC X');
  });
});

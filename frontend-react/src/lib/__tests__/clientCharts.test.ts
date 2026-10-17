import { describe, expect, it } from 'vitest';
import { REQUETE_A_FAIRE } from '../clientCharts';

// Lot L6b (E17) : les anneaux du tableau de bord client sont retires ; seule reste la regle des
// requetes a traiter par le client (ouverte, ou complement demande).
describe('REQUETE_A_FAIRE', () => {
  it('ne retient que les requetes qui attendent une action du client', () => {
    expect([...REQUETE_A_FAIRE].sort()).toEqual(['A_COMPLETER', 'OUVERTE']);
  });
});

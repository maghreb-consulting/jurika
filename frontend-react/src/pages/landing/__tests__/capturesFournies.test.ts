import { describe, it, expect } from 'vitest';
import { existsSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { fichiersCaptures } from '../captures';

/**
 * Lot L6b (E10, V2) : les captures de la vitrine sont de VRAIES captures de la plateforme,
 * livrees en WebP dans public/vitrine (deux largeurs par capture). Ce test reste rouge tant
 * qu'elles ne sont pas deposees : aucune image provisoire n'est versionnee a leur place.
 */
describe('Vitrine : captures fournies dans public/vitrine', () => {
  it('trouve les douze fichiers WebP attendus', () => {
    const dossier = resolve(__dirname, '../../../../public/vitrine');
    const fichiers = fichiersCaptures();
    expect(fichiers).toHaveLength(12);
    const manquants = fichiers.filter((f) => !existsSync(resolve(dossier, f)) || statSync(resolve(dossier, f)).size === 0);
    expect(manquants, `captures absentes de public/vitrine : ${manquants.join(', ')}`).toEqual([]);
  });
});

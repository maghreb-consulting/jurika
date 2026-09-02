import { describe, expect, it } from 'vitest';
import { buildLiquidateurOptions, emptyLiquidateur } from '../LiquidateurPicker';
import type { DossierParties } from '../../../services/workflow.service';

/**
 * Sélection du liquidateur DEPUIS LA BD d'abord (lot Dissolution 4 étapes) : les
 * gérants/associés du dossier alimentent la liste, sans re-saisie d'identité.
 */
describe('buildLiquidateurOptions', () => {
  const parties: DossierParties = {
    gerants: [{ civilite: 'M.', prenom: 'Ahmed', nom: 'ALAOUI', cin: 'AB1234' }],
    associes: [
      { civilite: 'M.', prenom: 'Ahmed', nom: 'ALAOUI', nombreParts: 600 }, // doublon du gérant
      { civilite: 'Mme', prenom: 'Salma', nom: 'TAZI', nombreParts: 400, adresse: 'RABAT' },
      { typePersonne: 'MORALE', denomination: 'HOLDING SA', nombreParts: 100 },
    ],
  };

  it('propose les gérants AVANT les associés', () => {
    const opts = buildLiquidateurOptions(parties);
    expect(opts[0].role).toBe('gerant');
    expect(opts[0].nom).toBe('ALAOUI');
    expect(opts[1].role).toBe('associe');
    expect(opts[1].nom).toBe('TAZI');
  });

  it('dédoublonne une même personne présente comme gérant ET associé', () => {
    const opts = buildLiquidateurOptions(parties);
    expect(opts.filter((o) => o.nom === 'ALAOUI')).toHaveLength(1);
  });

  it('exclut les personnes morales (le liquidateur est une personne physique)', () => {
    const opts = buildLiquidateurOptions(parties);
    expect(opts.some((o) => o.label.includes('HOLDING'))).toBe(false);
    expect(opts).toHaveLength(2);
  });

  it('reprend civilité, CIN et adresse connus (aucune re-saisie)', () => {
    const opts = buildLiquidateurOptions(parties);
    expect(opts[0]).toMatchObject({ civilite: 'M.', prenom: 'Ahmed', cin: 'AB1234' });
    expect(opts[1]).toMatchObject({ civilite: 'Mme', adresse: 'RABAT' });
  });

  it('renvoie une liste vide sans parties connues (la saisie externe reste possible)', () => {
    expect(buildLiquidateurOptions(null)).toEqual([]);
    expect(buildLiquidateurOptions({ gerants: [], associes: [] })).toEqual([]);
    // Une partie sans nom ni prénom n'est pas proposable.
    expect(buildLiquidateurOptions({ gerants: [{ cin: 'X' }], associes: [] })).toEqual([]);
  });
});

describe('emptyLiquidateur', () => {
  it('part sur une sélection BD vide et la rémunération par défaut du modèle', () => {
    const l = emptyLiquidateur();
    expect(l.source).toBe('BD');
    expect(l.partieKey).toBe('');
    expect(l.nom).toBe('');
    expect(l.remuneration).toBe('exercées à titre gratuit');
  });
});

import { describe, expect, it } from 'vitest';
import { buildLiquidateurOptions, emptyLiquidateur } from '../liquidateurOptions';
import { champsPersonneMorale, identiteLiquidateur, manqueLiquidateur } from '../../../lib/liquidateur';
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

  // Lot L4d (E5) : décision du directeur du 11 octobre 2026, le liquidateur peut être une
  // personne morale (auparavant exclue de la liste).
  it('propose aussi un associé personne morale, désigné par sa dénomination', () => {
    const opts = buildLiquidateurOptions(parties);
    const pm = opts.find((o) => o.label.includes('HOLDING'));
    expect(pm).toMatchObject({ morale: true, denomination: 'HOLDING SA', role: 'associe' });
    expect(pm?.label).toContain('personne morale');
    expect(opts).toHaveLength(3);
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
  it('part sur une sélection BD vide, sans rémunération supposée', () => {
    const l = emptyLiquidateur();
    expect(l.source).toBe('BD');
    expect(l.partieKey).toBe('');
    expect(l.nom).toBe('');
    // L4a (E2) : la rémunération n'est plus supposée ; l'écran la propose, l'employé confirme.
    expect(l.remuneration).toBe('');
  });
});

describe('Liquidateur personne morale (lot L4d, E5)', () => {
  const pm = {
    ...emptyLiquidateur(), source: 'EXTERNE' as const, forme: 'morale' as const, civilite: '',
    denomination: 'FIDUCIAIRE ATLAS', siegeSocial: '10 rue d’Agadir, Casablanca', representantNom: 'M. Karim BENNANI',
    rcNumero: '445566', rcVille: 'Casablanca',
  };

  it('est désigné par sa dénomination', () => {
    expect(identiteLiquidateur(pm)).toBe('FIDUCIAIRE ATLAS');
    expect(manqueLiquidateur(pm)).toBeNull();
  });

  it('nomme ce qui manque : dénomination, siège, représentant permanent', () => {
    expect(manqueLiquidateur({ ...pm, denomination: '' })).toMatch(/dénomination/);
    expect(manqueLiquidateur({ ...pm, siegeSocial: '' })).toMatch(/siège social/);
    expect(manqueLiquidateur({ ...pm, representantNom: '' })).toMatch(/représentant permanent/);
  });

  it('transmet ses champs propres, et rien pour une personne physique', () => {
    expect(champsPersonneMorale(pm)).toMatchObject({ forme: 'morale', denomination: 'FIDUCIAIRE ATLAS', rcNumero: '445566' });
    expect(champsPersonneMorale(emptyLiquidateur())).toEqual({});
  });
});

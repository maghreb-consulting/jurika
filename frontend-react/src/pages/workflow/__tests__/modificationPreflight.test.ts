/// <reference types="vitest" />
import { describe, it, expect } from 'vitest';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  FALLBACK_ANNEXE_TYPES,
  computePreflight,
  computeRefonteMissing,
  convocationDelaiError,
  decisionTypeFor,
  validateModification,
  validateStep1Inputs,
} from '../ModificationWorkflowPage';

describe('Modification — preflight (étape 3)', () => {
  it('FALLBACK_ANNEXE_TYPES contient les 9 types non couverts', () => {
    expect(FALLBACK_ANNEXE_TYPES.size).toBe(9);
    [
      'AUGMENTATION_CAPITAL_NATURE',
      'CESSION_PARTIELLE',
      'CESSION_TOTALE',
      'TRANSMISSION_PARTS',
      'NANTISSEMENT',
      'TRANSFORMATION',
      'FUSION_SCISSION',
      'DESIGNATION_CAC',
      'PACTE_ASSOCIES',
    ].forEach((t) => expect(FALLBACK_ANNEXE_TYPES.has(t)).toBe(true));
  });

  it('validateModification CHANGEMENT_DENOMINATION vide → erreur sur nouvelleDenomination', () => {
    const errs = validateModification('CHANGEMENT_DENOMINATION', {});
    expect(errs.nouvelleDenomination).toBeTruthy();
  });

  it('validateModification CHANGEMENT_DENOMINATION renseigné → aucune erreur', () => {
    const errs = validateModification('CHANGEMENT_DENOMINATION', {
      nouvelleDenomination: 'ATLAS PARTNERS SARL',
    });
    expect(Object.keys(errs)).toHaveLength(0);
  });

  it('preflight bloque la generation si un type COUVERT a un champ manquant', () => {
    const r = computePreflight(['CHANGEMENT_DENOMINATION'], {
      CHANGEMENT_DENOMINATION: {},
    });
    expect(r.canGenerate).toBe(false);
    expect(r.blocking).toHaveLength(1);
    expect(r.blocking[0].typeId).toBe('CHANGEMENT_DENOMINATION');
    expect(r.blocking[0].missing.map((m) => m.name)).toContain('nouvelleDenomination');
  });

  it('preflight autorise la generation si tous les champs sont remplis', () => {
    const r = computePreflight(['CHANGEMENT_DENOMINATION'], {
      CHANGEMENT_DENOMINATION: { nouvelleDenomination: 'ATLAS PARTNERS SARL' },
    });
    expect(r.canGenerate).toBe(true);
    expect(r.blocking).toHaveLength(0);
  });

  it('preflight : types en FALLBACK_ANNEXE → warning seul, jamais blocking', () => {
    // CESSION_PARTIELLE est dans FIELD_SPECS (4 champs requis) ET dans FALLBACK_ANNEXE :
    // les champs manquants doivent partir en warnings (pas en blocking).
    const r = computePreflight(['CESSION_PARTIELLE'], { CESSION_PARTIELLE: {} });
    expect(r.canGenerate).toBe(true);
    expect(r.blocking).toHaveLength(0);
    expect(r.warnings).toHaveLength(1);
    expect(r.warnings[0].typeId).toBe('CESSION_PARTIELLE');

    // NANTISSEMENT n'a pas de FIELD_SPECS → fallback "details" ≥ 10 chars manquant
    // → warning seulement (annexe à fournir).
    const r2 = computePreflight(['NANTISSEMENT'], { NANTISSEMENT: {} });
    expect(r2.canGenerate).toBe(true);
    expect(r2.warnings.map((w) => w.typeId)).toEqual(['NANTISSEMENT']);
  });

  it('preflight : mix couvert manquant + fallback manquant → blocking 1, warnings 1, canGenerate=false', () => {
    const r = computePreflight(['TRANSFERT_SIEGE', 'TRANSFORMATION'], {
      TRANSFERT_SIEGE: { nouvelleVille: 'Casablanca' }, // adresse + date manquent
      TRANSFORMATION: {}, // fallback annexe
    });
    expect(r.canGenerate).toBe(false);
    expect(r.blocking.map((b) => b.typeId)).toEqual(['TRANSFERT_SIEGE']);
    expect(r.warnings.map((w) => w.typeId)).toEqual(['TRANSFORMATION']);
  });

  it('preflight : aucun type sélectionné → canGenerate=true (rien à valider)', () => {
    const r = computePreflight([], {});
    expect(r.canGenerate).toBe(true);
    expect(r.blocking).toHaveLength(0);
    expect(r.warnings).toHaveLength(0);
  });

  // PROMPT F (2026-06-23) — sélecteur de société requis à l'étape 1.

  it('validateStep1Inputs : dossierId manquant → blocage', () => {
    const msg = validateStep1Inputs({
      dossierId: '',
      selectedTypes: ['CHANGEMENT_DENOMINATION'],
      datePV: '2026-07-01',
      decisionType: 'AGE',
    });
    expect(msg).toBeTruthy();
    expect(msg).toMatch(/societe/i);
  });

  it('validateStep1Inputs : dossierId OK + selection OK + date OK → null', () => {
    const msg = validateStep1Inputs({
      dossierId: '11111111-1111-1111-1111-111111111111',
      selectedTypes: ['CHANGEMENT_DENOMINATION'],
      datePV: '2026-07-01',
      decisionType: 'AGE',
    });
    expect(msg).toBeNull();
  });

  it('validateStep1Inputs : dossierId présent mais sélection vide → blocage \"au moins une\"', () => {
    const msg = validateStep1Inputs({
      dossierId: '11111111-1111-1111-1111-111111111111',
      selectedTypes: [],
      datePV: '2026-07-01',
      decisionType: 'AGE',
    });
    expect(msg).toMatch(/au moins une/i);
  });

  it('preflight collecte plusieurs champs manquants sur le meme type', () => {
    const r = computePreflight(['DESIGNATION_GERANT'], {
      DESIGNATION_GERANT: { nom: 'EL FASSI' }, // prenom, cin, dateEffet, nationalite manquent
    });
    expect(r.canGenerate).toBe(false);
    const missing = r.blocking[0].missing.map((m) => m.name);
    expect(missing).toEqual(expect.arrayContaining(['prenom', 'cin', 'dateEffet', 'nationalite']));
  });
});

describe('Modification — preflight statut refondu (étape 3, 2026-06-24)', () => {
  it('fiche vide (société importée scan-only) → tous les champs structurés manquants + gérant', () => {
    const missing = computeRefonteMissing({}).map((f) => f.key);
    expect(missing).toEqual(
      expect.arrayContaining([
        'denomination',
        'objetSocial',
        'adresseSiege',
        'capitalSocial',
        'valeurNominale',
        'dureeAnnees',
        'gerantNom',
      ]),
    );
  });

  it('fiche complète (état structuré + gérant) → aucun champ manquant', () => {
    const missing = computeRefonteMissing({
      denomination: 'ACME SARL',
      objetSocial: 'Conseil',
      adresseSiege: 'Casablanca',
      capitalSocial: 100000,
      valeurNominale: 100,
      dureeAnnees: 99,
      gerants: [{ nom: 'BENNANI' }],
    });
    expect(missing).toHaveLength(0);
  });

  it('gérant fourni via la saisie preflight gerantNom → plus exigé', () => {
    const base = {
      denomination: 'ACME SARL',
      objetSocial: 'Conseil',
      adresseSiege: 'Casablanca',
      capitalSocial: 100000,
      valeurNominale: 100,
      dureeAnnees: 99,
    };
    expect(computeRefonteMissing(base).map((f) => f.key)).toEqual(['gerantNom']);
    expect(computeRefonteMissing({ ...base, gerantNom: 'EL FASSI' })).toHaveLength(0);
  });

  it('valeurs blanches (espaces / vide) comptent comme manquantes', () => {
    const missing = computeRefonteMissing({
      denomination: '   ',
      objetSocial: '',
      adresseSiege: 'Rabat',
      capitalSocial: 50000,
      valeurNominale: 100,
      dureeAnnees: 99,
      gerantNom: 'X',
    }).map((f) => f.key);
    expect(missing).toEqual(expect.arrayContaining(['denomination', 'objetSocial']));
    expect(missing).not.toContain('adresseSiege');
  });
});

describe('Modification — type d\'assemblee simplifie (Ordinaire/Extraordinaire)', () => {
  it('SARL_AU → toujours AU quelle que soit la nature', () => {
    expect(decisionTypeFor(true, 'ordinaire')).toBe('AU');
    expect(decisionTypeFor(true, 'extraordinaire')).toBe('AU');
  });
  it('SARL ordinaire → AGO ; SARL extraordinaire → AGE', () => {
    expect(decisionTypeFor(false, 'ordinaire')).toBe('AGO');
    expect(decisionTypeFor(false, 'extraordinaire')).toBe('AGE');
  });
});

describe('Modification — convocation, regle DURE des 16 jours', () => {
  it('constante = 16 jours', () => {
    expect(CONVOCATION_DELAI_JOURS).toBe(16);
  });
  it('convocation absente (date vide) → pas de controle (null)', () => {
    expect(convocationDelaiError('', '2026-07-01')).toBeNull();
    expect(convocationDelaiError('2026-06-10', '')).toBeNull();
  });
  it('ecart >= 16 jours → OK (null)', () => {
    expect(convocationDelaiError('2026-06-10', '2026-07-01')).toBeNull(); // 21 j
    expect(convocationDelaiError('2026-06-15', '2026-07-01')).toBeNull(); // 16 j
  });
  it('ecart < 16 jours → message d\'erreur mentionnant 16 jours', () => {
    const msg = convocationDelaiError('2026-06-20', '2026-07-01'); // 11 j
    expect(msg).toBeTruthy();
    expect(msg).toContain('16 jours');
  });
  it('convocation posterieure ou egale au PV → erreur', () => {
    expect(convocationDelaiError('2026-07-01', '2026-07-01')).toBeTruthy();
    expect(convocationDelaiError('2026-07-05', '2026-07-01')).toBeTruthy();
  });
  it('addDaysIso ajoute EXACTEMENT 16 jours (aucun décalage de fuseau)', () => {
    expect(addDaysIso('2026-07-01', 16)).toBe('2026-07-17'); // + 16, pas + 15
    expect(addDaysIso('2026-02-20', 16)).toBe('2026-03-08'); // franchit fin de mois
    expect(addDaysIso('2026-12-25', 16)).toBe('2027-01-10'); // franchit fin d'année
    // gap exact = 16 jours entre les deux
    const conv = '2026-05-10';
    expect(convocationDelaiError(conv, addDaysIso(conv, 16))).toBeNull();
  });
  it('addDaysIso vide/invalide → ""', () => {
    expect(addDaysIso('', 16)).toBe('');
    expect(addDaysIso('pas-une-date', 16)).toBe('');
  });
});

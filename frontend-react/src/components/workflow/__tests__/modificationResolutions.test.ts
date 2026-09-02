import { describe, expect, it } from 'vitest';
import {
  PERSON_FIELD_SOURCE,
  RES_SPECS,
  flattenResolution,
  newResolution,
  oldValuesFor,
  personNames,
  seedResolutions,
  type ResolutionState,
} from '../modificationResolutions';

describe('modificationResolutions — seedResolutions', () => {
  const decisions = [
    { id: 'd1', resolutionType: 'transfert_siege', label: 'Transfert du siège' },
    { id: 'd2', resolutionType: 'modification_denomination', label: 'Changement dénomination' },
  ];

  it('crée une résolution par décision, dans l’ordre de la sélection', () => {
    const res = seedResolutions([], decisions);
    expect(res.map((r) => r.id)).toEqual(['d1', 'd2']);
    expect(res.map((r) => r.type)).toEqual(['transfert_siege', 'modification_denomination']);
  });

  it('conserve les valeurs déjà saisies pour une décision maintenue', () => {
    const prev: ResolutionState[] = [
      { id: 'd1', type: 'transfert_siege', objet: 'X', values: { nouveauSiege: 'Rabat' }, rows: {} },
    ];
    const res = seedResolutions(prev, decisions);
    expect(res.find((r) => r.id === 'd1')?.values.nouveauSiege).toBe('Rabat');
  });

  it('retire les résolutions dé-sélectionnées et ajoute les nouvelles', () => {
    const prev: ResolutionState[] = [
      { id: 'd1', type: 'transfert_siege', objet: '', values: {}, rows: {} },
      { id: 'dX', type: 'reduction_capital', objet: '', values: {}, rows: {} },
    ];
    const res = seedResolutions(prev, decisions);
    expect(res.map((r) => r.id)).toEqual(['d1', 'd2']);
    expect(res.some((r) => r.id === 'dX')).toBe(false);
  });

  it('réinitialise si le type d’une décision a changé pour le même id', () => {
    const prev: ResolutionState[] = [
      { id: 'd1', type: 'reduction_capital', objet: '', values: { redcapMontant: '1000' }, rows: {} },
    ];
    const res = seedResolutions(prev, decisions);
    expect(res.find((r) => r.id === 'd1')?.type).toBe('transfert_siege');
    expect(res.find((r) => r.id === 'd1')?.values.redcapMontant).toBeUndefined();
  });
});

describe('modificationResolutions — flattenResolution', () => {
  it('aplati type + champs non vides + objet', () => {
    const r = newResolution('modification_denomination', 'd2', 'Renommage');
    r.values = { nouvelleDenomination: 'ATLAS SARL', articlesModifies: '2', dateEffet: '' };
    const out = flattenResolution(r);
    expect(out).toMatchObject({
      type: 'modification_denomination',
      objet: 'Renommage',
      nouvelleDenomination: 'ATLAS SARL',
      articlesModifies: '2',
    });
    expect(out.dateEffet).toBeUndefined(); // champ vide ignoré
  });

  it('inclut les sous-listes non vides et le nouvel associé', () => {
    const r = newResolution('affectation_resultat', 'd3');
    r.rows = { affectations: [{ poste: 'Réserve légale', montant: '5000' }, {}] };
    r.newAssocie = { nom: 'ALAMI', prenom: 'Sara', cin: 'AB12345' };
    const out = flattenResolution(r);
    expect((out.affectations as unknown[]).length).toBe(1);
    expect(out.nouvelAssocie).toMatchObject({ nom: 'ALAMI', cin: 'AB12345' });
  });
});

describe('modificationResolutions — oldValuesFor', () => {
  const fiche = {
    denomination: 'OLD NAME SARL',
    capitalSocial: 100000,
    valeurNominale: 100,
    nombreParts: 1000,
    adresseSiege: 'Casablanca',
    objetSocial: 'Conseil',
    dureeAnnees: 99,
  };

  it('renvoie la dénomination actuelle pour un changement de dénomination', () => {
    expect(oldValuesFor('modification_denomination', fiche)).toEqual([
      { label: 'Dénomination actuelle', value: 'OLD NAME SARL' },
    ]);
  });

  it('renvoie capital + valeur nominale + parts pour une opération sur le capital', () => {
    const olds = oldValuesFor('augmentation_capital_numeraire', fiche);
    expect(olds.map((o) => o.label)).toEqual([
      'Capital actuel',
      'Valeur nominale actuelle',
      'Nombre de parts actuel',
    ]);
    expect(olds[0].value).toContain('100');
  });

  it('renvoie une liste vide pour un type sans ancienne valeur pertinente', () => {
    expect(oldValuesFor('pouvoirs_formalites', fiche)).toEqual([]);
  });
});

describe('modificationResolutions — RES_SPECS', () => {
  it('couvre les 33 types du moteur directeur', () => {
    expect(Object.keys(RES_SPECS).length).toBe(33);
  });
});

describe('modificationResolutions — P3 associés/gérants BD', () => {
  it('personNames extrait les noms lisibles (PP « prénom nom » + PM dénomination), dédupliqués', () => {
    const list = [
      { typePersonne: 'PHYSIQUE', prenom: 'Karim', nom: 'BENNANI', cin: 'BK1' },
      { typePersonne: 'PHYSIQUE', prenom: 'Salma', nom: 'IDRISSI' },
      { typePersonne: 'MORALE', denomination: 'HOLDING X SARL' },
      { prenom: 'Karim', nom: 'BENNANI' }, // doublon
      null,
      {},
    ];
    expect(personNames(list)).toEqual(['Karim BENNANI', 'Salma IDRISSI', 'HOLDING X SARL']);
    expect(personNames(undefined)).toEqual([]);
  });

  it('mappe les champs « personne existante » vers leur source BD (associé/gérant)', () => {
    expect(PERSON_FIELD_SOURCE.gerantNom).toBe('gerant');
    expect(PERSON_FIELD_SOURCE.gerantSortantNom).toBe('gerant');
    expect(PERSON_FIELD_SOURCE.cedantNom).toBe('associe');
    expect(PERSON_FIELD_SOURCE.nantissementConstituantNom).toBe('associe');
    // Un champ « nouvel entrant » (cessionnaire/apporteur) N'EST PAS une sélection BD.
    expect(PERSON_FIELD_SOURCE.cessionnaireNom).toBeUndefined();
    expect(PERSON_FIELD_SOURCE.apporteurNom).toBeUndefined();
  });
});

import { describe, expect, it } from 'vitest';
import { identifiantsApresCreation } from '../identifiantsFinalisation';
import type { DossierJuridiqueView } from '../../../types/dataroom';

/**
 * Lot L6b (E14) : identifiants envoyes a la fiche du dossier a la fin d'une creation.
 *
 * Constate sur la pile (parcours de creation joue de bout en bout) : apres la finalisation, le
 * dossier n'avait plus ni adresse, ni ville, ni capital, et l'ICE saisi a l'etape 1 n'y etait pas.
 *  - la route PATCH /dossiers/{id}/identifiants REMPLACE tous les champs : l'envoi des seuls
 *    identifiants effacait l'adresse, la ville, le capital et la date de constitution ;
 *  - l'etape 1 est enregistree a plat (`step1.denomination` est la chaine saisie) : l'ICE etait
 *    cherche sous `step1.denomination.ice` et jamais trouve.
 */
const actuel = {
  dossierId: 'd1', raisonSociale: 'EXPLO SARL', formeJuridique: 'SARL',
  ice: null, rcNumero: null, rcTribunal: null, identifiantFiscal: null, taxeProfessionnelle: null, cnss: null,
  adresseSiege: '12 rue de la D\u00e9monstration', ville: 'Casablanca', capitalSocialMad: 10000,
  dateConstitution: '2026-10-16', statut: 'ACTIVE',
  documentsEnVigueur: [], historiqueOperations: [], dossiersParTicket: [],
} satisfies DossierJuridiqueView;

const saisiePlate = {
  step1: { denomination: 'Explo SARL', ice: '001234567000089' },
  step2: { adresse: '12 rue de la D\u00e9monstration', commune: 'Casablanca', villeGreffe: 'Casablanca' },
};

describe('identifiantsApresCreation', () => {
  it('lit l\u2019ICE de l\u2019etape 1 enregistree a plat', () => {
    expect(identifiantsApresCreation(saisiePlate, actuel)?.ice).toBe('001234567000089');
  });

  it('conserve l\u2019adresse, la ville, le capital et la date de constitution du dossier', () => {
    const p = identifiantsApresCreation(saisiePlate, actuel);
    expect(p).toMatchObject({
      adresseSiege: '12 rue de la D\u00e9monstration', ville: 'Casablanca', capitalSocialMad: 10000,
      dateConstitution: '2026-10-16', rcTribunal: 'Casablanca',
    });
  });

  it('ne remplace jamais un identifiant deja connu du dossier par un vide', () => {
    const p = identifiantsApresCreation({ step1: { denomination: 'X' } }, { ...actuel, ice: '001234567000089', rcNumero: '654321' });
    expect(p?.ice ?? '001234567000089').toBe('001234567000089');
    expect(p?.rcNumero ?? '654321').toBe('654321');
  });

  it('n\u2019envoie rien quand le parcours n\u2019apporte aucun identifiant nouveau', () => {
    const tout = { ...actuel, ice: '001234567000089', rcTribunal: 'Casablanca' };
    expect(identifiantsApresCreation(saisiePlate, tout)).toBeNull();
  });
});

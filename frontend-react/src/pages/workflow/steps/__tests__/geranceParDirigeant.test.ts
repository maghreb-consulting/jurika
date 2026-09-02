/// <reference types="vitest" />
import { describe, it, expect } from 'vitest';
import {
  agregerGerance,
  dureeMandatLabel,
  dirigeantLabel,
} from '../Step5Dirigeants';

/**
 * Gérance PAR DIRIGEANT — étape 5 de la création (2026-08-18).
 *
 * Deux défauts corrigés ici :
 *
 * 1. **La durée du mandat était saisie DEUX FOIS.** L'étape portait deux sections,
 *    « Gouvernance de la gerance » (choix structuré illimitée / N années) et
 *    « Designation & pouvoirs de la gerance » (texte libre pré-rempli « 99 années »).
 *    La page de création préférait le texte libre (`dureeGerance ?? dureeMandat`) :
 *    un mandat déclaré ILLIMITÉ ressortait donc « 99 années » dans l'acte de
 *    nomination. Les deux clés portent désormais la même valeur, agrégée.
 *
 * 2. **Une seule gérance pour tous.** Une SARL peut nommer plusieurs gérants aux
 *    mandats distincts. La saisie est passée par dirigeant ; comme les modèles
 *    directeur n'ont qu'un `$DUREE_GERANCE` à la racine (hors boucle GERANTS) et
 *    sont intouchables, l'agrégation énumère nominativement quand les mandats
 *    diffèrent — plutôt que de retenir celui du premier gérant, qui serait faux
 *    sans que rien ne le signale.
 */

type D = Parameters<typeof agregerGerance>[0][number];

function dirigeant(over: Partial<D> = {}): D {
  return {
    id: Math.random().toString(36).slice(2),
    typePersonne: 'PHYSIQUE',
    civilite: 'M',
    nom: 'ALAOUI',
    prenom: 'Ahmed',
    cinNumero: 'BE111111',
    nationalite: 'Marocaine',
    dateNaissance: '',
    lieuNaissance: '',
    pieceValidite: '',
    adresse: '45 BD ZERKTOUNI',
    capitalEntite: 0,
    deliberationDate: '',
    fonction: 'GERANT',
    isStatutaire: true,
    isAssociate: false,
    cinUploaded: true,
    extracting: false,
    denomination: '',
    formeJuridiqueEntite: 'SARL',
    rc: '',
    ice: '',
    dureeMandatType: 'illimitee',
    dureeAnnees: 0,
    remunerationMode: 'non_remunere',
    remunerationMontant: 0,
    ...over,
  } as D;
}

describe('durée du mandat — une seule source', () => {
  it('illimitée : la formule légale, pas « 99 années »', () => {
    // C'est le symptôme d'origine : le texte libre « 99 années » écrasait le choix.
    expect(dureeMandatLabel({ dureeMandatType: 'illimitee', dureeAnnees: 0 })).toBe(
      "illimitée (jusqu'à révocation)",
    );
  });

  it('déterminée : le nombre d’années saisi', () => {
    expect(dureeMandatLabel({ dureeMandatType: 'determinee', dureeAnnees: 3 })).toBe(
      '3 année(s)',
    );
  });

  it('déterminée sans années : retombe sur illimitée, jamais sur une durée inventée', () => {
    expect(dureeMandatLabel({ dureeMandatType: 'determinee', dureeAnnees: 0 })).toBe(
      "illimitée (jusqu'à révocation)",
    );
  });
});

describe('agrégation des mandats vers les variables racine', () => {
  it('mandats identiques : la valeur commune (rendu inchangé)', () => {
    const r = agregerGerance([
      dirigeant({ dureeMandatType: 'determinee', dureeAnnees: 3 }),
      dirigeant({ nom: 'BENJELLOUN', prenom: 'Salma', civilite: 'Mme',
                  dureeMandatType: 'determinee', dureeAnnees: 3 }),
    ]);
    expect(r.dureeGerance).toBe('3 année(s)');
  });

  it('mandats différents : énumération NOMINATIVE, lisible après « pour une durée de »', () => {
    const r = agregerGerance([
      dirigeant({ dureeMandatType: 'determinee', dureeAnnees: 3 }),
      dirigeant({ nom: 'BENJELLOUN', prenom: 'Salma', civilite: 'Mme',
                  dureeMandatType: 'illimitee' }),
    ]);
    expect(r.dureeGerance).toBe(
      "3 année(s) pour M. Ahmed ALAOUI et illimitée (jusqu'à révocation) pour Mme Salma BENJELLOUN",
    );
    // La phrase du modèle reste grammaticale une fois la valeur injectée.
    expect(`pour une durée de ${r.dureeGerance} :`).toContain('pour une durée de 3 année(s) pour');
  });

  it('un seul gérant : sa durée, sans mention de nom superflue', () => {
    const r = agregerGerance([dirigeant({ dureeMandatType: 'illimitee' })]);
    expect(r.dureeGerance).toBe("illimitée (jusqu'à révocation)");
  });

  it('les fiches encore vierges ne faussent pas l’agrégat', () => {
    // Une fiche vide (dirigeant tout juste ajouté) porte les valeurs par défaut :
    // sans ce filtre, elle rendrait « différents » deux mandats identiques.
    const r = agregerGerance([
      dirigeant({ dureeMandatType: 'determinee', dureeAnnees: 5 }),
      dirigeant({ nom: '', prenom: '', denomination: '' }),
    ]);
    expect(r.dureeGerance).toBe('5 année(s)');
  });

  it('rémunération : commune si partagée, neutre si divergente', () => {
    expect(
      agregerGerance([
        dirigeant({ remunerationMode: 'montant_fixe', remunerationMontant: 5000 }),
        dirigeant({ nom: 'BENJELLOUN', remunerationMode: 'montant_fixe', remunerationMontant: 5000 }),
      ]).remunerationMontant,
    ).toBe(5000);
    // Deux montants distincts : aucun ne peut représenter l'autre.
    expect(
      agregerGerance([
        dirigeant({ remunerationMode: 'montant_fixe', remunerationMontant: 5000 }),
        dirigeant({ nom: 'BENJELLOUN', remunerationMode: 'montant_fixe', remunerationMontant: 8000 }),
      ]).remunerationMontant,
    ).toBe(0);
  });

  it("la limitation des pouvoirs ne fait PAS partie de l'agrégat", () => {
    /*
     * Décision du 2026-08-18 : la limitation des pouvoirs reste saisie UNE SEULE
     * FOIS, au niveau de la gérance. C'est une clause statutaire qui borne ce que
     * l'ORGANE peut faire (« les actes suivants requièrent l'accord préalable des
     * associés : … »), pas un attribut de personne. L'énumérer par gérant produisait
     * une clause qui décrivait mal son propre objet.
     */
    const r = agregerGerance([dirigeant(), dirigeant({ nom: 'BENJELLOUN' })]);
    expect(r).not.toHaveProperty('limitationPouvoirs');
  });
});

describe('libellé du dirigeant dans les actes', () => {
  it('personne physique : civilité + prénom + nom', () => {
    expect(dirigeantLabel({ civilite: 'Mme', prenom: 'Salma', nom: 'BENJELLOUN' }, 0))
      .toBe('Mme Salma BENJELLOUN');
  });

  it('personne morale : sa dénomination', () => {
    expect(dirigeantLabel({ typePersonne: 'MORALE', denomination: 'HOLDING ATLAS' }, 0))
      .toBe('HOLDING ATLAS');
  });

  it('fiche sans nom : un repère de rang, jamais une chaîne vide', () => {
    expect(dirigeantLabel({ civilite: 'M', prenom: '', nom: '' }, 1)).toBe('Gérant 2');
  });
});

import { describe, expect, it } from 'vitest';
import {
  CHAMPS_COMPLEMENTAIRES,
  DOC_ACTE_NOMINATION,
  DOC_ANNONCE_LEGALE,
  DOC_DECLARATION_EXISTENCE,
  DOC_DECLARATION_RC,
  DOC_DEMANDE_TP,
  DOC_STATUTS_SARL,
  champsParDocument,
} from '../documents-creation';

/**
 * Lot 5 (2026-09-07) — « un champ n'apparaît que si le document qui le consomme
 * est retenu ». C'est la règle posée par l'utilisateur ; ce fichier la met à
 * l'épreuve sur des valeurs, pas sur l'absence d'erreur.
 */
describe('Champs complémentaires — étape 7 Création', () => {
  it('n’affiche AUCUN champ quand seuls les actes sont retenus', () => {
    const groupes = champsParDocument([
      DOC_STATUTS_SARL,
      DOC_ANNONCE_LEGALE,
      DOC_ACTE_NOMINATION,
    ]);
    expect(groupes).toEqual([]);
  });

  it('n’affiche le régime de TVA que si la déclaration d’existence est retenue', () => {
    const sansDe = champsParDocument([DOC_STATUTS_SARL, DOC_DEMANDE_TP]);
    const clesSansDe = sansDe.flatMap((g) => g.champs.map((c) => c.key));
    expect(clesSansDe).not.toContain('tvaAssujettissement');
    expect(clesSansDe).not.toContain('regimeResultat');

    const avecDe = champsParDocument([DOC_STATUTS_SARL, DOC_DECLARATION_EXISTENCE]);
    const clesAvecDe = avecDe.flatMap((g) => g.champs.map((c) => c.key));
    expect(clesAvecDe).toContain('tvaAssujettissement');
    expect(clesAvecDe).toContain('regimeResultat');
  });

  it('n’affiche l’enseigne que si la déclaration d’immatriculation est retenue', () => {
    const sansRc = champsParDocument([DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE]);
    expect(sansRc.flatMap((g) => g.champs.map((c) => c.key))).not.toContain('enseigne');

    const avecRc = champsParDocument([DOC_DECLARATION_RC]);
    expect(avecRc.flatMap((g) => g.champs.map((c) => c.key))).toContain('enseigne');
  });

  it('ne demande JAMAIS deux fois le même champ, même partagé par deux documents', () => {
    const groupes = champsParDocument([
      DOC_DEMANDE_TP,
      DOC_DECLARATION_EXISTENCE,
      DOC_DECLARATION_RC,
    ]);
    const cles = groupes.flatMap((g) => g.champs.map((c) => c.key));
    expect(new Set(cles).size).toBe(cles.length);
    // Le téléphone sert les DEUX imprimés DGI : il est demandé une seule fois,
    // sous le premier document retenu qui en a besoin.
    expect(groupes[0].code).toBe(DOC_DEMANDE_TP);
    expect(groupes[0].champs.map((c) => c.key)).toContain('telephone');
    const groupeDe = groupes.find((g) => g.code === DOC_DECLARATION_EXISTENCE);
    expect(groupeDe?.champs.map((c) => c.key)).not.toContain('telephone');
  });

  it('groupe chaque champ sous un document qui le consomme réellement', () => {
    const codes = [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE, DOC_DECLARATION_RC];
    for (const groupe of champsParDocument(codes)) {
      for (const champ of groupe.champs) {
        expect(champ.documents).toContain(groupe.code);
      }
    }
  });

  it('ne redemande aucune donnée déjà saisie aux étapes 1 à 6', () => {
    // Règle permanente du projet. Ces clés-là ont une source dans le workflow :
    // les voir apparaître ici serait une saisie en double.
    const dejaEnBase = [
      'denomination',
      'sigle',
      'ice',
      'ifFiscal',
      'siegeAdresse',
      'ville',
      'villeGreffe',
      'tribunalVille',
      'tribunalType',
      'capital',
      'dureeAnnees',
      'objetSocial',
      'activitePrincipale',
      'certificatNegatifNumero',
      'certificatNegatifDate',
      'gerantNom',
      'gerantPrenom',
      'gerantLieuNaissance',
      'gerantDateNaissance',
      'declarantNom',
      'associePrincipalNom',
      'associePrincipalCni',
      'associePrincipalAdresse',
    ];
    const declarees = CHAMPS_COMPLEMENTAIRES.map((c) => c.key);
    for (const cle of dejaEnBase) {
      expect(declarees, `« ${cle} » est déjà collectée : elle ne doit pas être redemandée`)
        .not.toContain(cle);
    }
  });
});

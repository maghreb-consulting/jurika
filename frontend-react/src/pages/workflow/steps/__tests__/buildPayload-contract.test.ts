import { describe, it, expect } from 'vitest';
import { buildPayloadCreationSarl } from '../../CreationSarlWorkflowPage';

/**
 * Phase 4 (contrat §2) — Le formulaire CRÉATION refondu ne collecte QUE les champs
 * du contrat, mais il les collecte TOUS. Ce test prouve que le payload produit par
 * `buildPayloadCreationSarl` alimente chaque variable attendue par les 4 modèles
 * directeur (aucune clé `societe.*` du contrat ne reste `undefined`), y compris les
 * nouvelles variables d'accord/paramètres d'acte, et que les champs hors contrat ne
 * fuitent pas.
 */
describe('buildPayloadCreationSarl — champs = contrat (Phase 4)', () => {
  const fullStepData = () =>
    ({
      step1: {
        denomination: {
          denomination: 'PARACOSME',
          formeJuridique: 'SARL',
          cnNumero: 'CN-123',
          cnDate: '2026-01-10',
          activiteCn: 'conseil',
          beneficiaire: 'M. BENANI',
        },
      },
      step2: {
        siege: {
          adresse: '101 bd Zerktouni',
          commune: 'Casablanca',
          villeGreffe: 'Casablanca',
          dureeSociete: 99,
        },
      },
      step3: {
        capital: {
          capitalSocialMad: 100000,
          nombreParts: 1000,
          valeurNominale: 100,
          dateCommencement: '2026-02-13',
          dateFin: '2026-12-31',
          modeLiberation: 'intégrale',
          depotFondsBloque: true,
          depotBanqueNom: 'Attijariwafa Bank',
          depotNumero: '007-780',
          commissaireApportsNom: '',
        },
      },
      step4: { activite: { description: 'le conseil et l’ingénierie', dateDebutExercice: '2026-01-01' } },
      step5: {
        dirigeants: [
          {
            typePersonne: 'PHYSIQUE',
            civilite: 'M',
            prenom: 'Yassine',
            nom: 'BENANI',
            cinNumero: 'BK12345',
            isStatutaire: true,
          },
        ],
        gerance: {
          gerantModeDesignation: 'statutaire',
          dureeGerance: '99 années',
          limitationPouvoirs: 'Au-delà de 500 000 DH, accord des associés.',
          modeSignature: 'séparée avec plafond',
          signaturePlafond: 50000,
          modeSignatureAdmin: 'identique',
          signatureMandataire: true,
          mandataireNom: 'M. TAZI',
          mandataireActeDelegation: 'acte de délégation du 01/02/2026',
          dureeMandat: 'illimitée',
          remunerationMode: 'non_remunere',
        },
        signataires: [{ nom: 'M. Yassine BENANI', qualite: 'gérant' }],
      },
      step6: {
        associes: [
          {
            typePersonne: 'PHYSIQUE',
            civilite: 'Mme',
            prenom: 'Salma',
            nom: 'IDRISSI',
            genre: 'féminin',
            estGerant: false,
            nombreParts: 1000,
            apports: [{ type: 'NUMERAIRE', parts: 1000 }],
          },
        ],
      },
      step9: {
        acteParams: {
          commissaireComptesNom: '',
          dureeMandatCac: 3,
          exerciceDebut: '1er janvier',
          exerciceFin: '31 décembre',
          premierExerciceCloture: '31 décembre 2026',
          engagementsMandat: 'Bail commercial signé le 01/02/2026',
          lieuSignature: 'Casablanca',
          nombreOriginaux: 6,
          articleDesignationStatuts: '36',
          heureActe: '10 heures',
        },
      },
    }) as Record<string, Record<string, unknown>>;

  it('produit toutes les variables societe.* du contrat (aucune undefined)', () => {
    const payload = buildPayloadCreationSarl(fullStepData());
    const s = payload.societe as Record<string, unknown>;

    // §2.1 Société / §2.2 Capital
    for (const k of [
      'denomination', 'adresseSiege', 'dureeSociete', 'villeGreffe',
      'capitalChiffres', 'nombreParts', 'valeurPart',
      'modeLiberation', 'depotFondsBloque', 'banqueDepositaire', 'compteBancaireNumero',
      // §2.4/2.5 Gérance + signature
      'gerantModeDesignation', 'dureeGerance', 'limitationPouvoirs',
      'modeSignature', 'signaturePlafond', 'modeSignatureAdmin',
      'signatureMandataire', 'mandataireNom', 'mandataireActeDelegation',
      // §2.6 Comptes / exercice
      'dureeMandatCac', 'exerciceDebut', 'exerciceFin', 'premierExerciceCloture',
      // §2.7 Constitution
      'engagementsMandat', 'lieuSignature', 'nombreOriginaux', 'articleDesignationStatuts', 'heureActe',
    ]) {
      expect(s[k], `societe.${k} doit être renseignée`).toBeDefined();
    }

    expect(s.modeSignatureAdmin).toBe('identique');
    expect(s.signatureMandataire).toBe(true);
    expect(s.gerantModeDesignation).toBe('statutaire');
  });

  it('émet les signataires (art. 15) et l’accord genre/estGerant par associé', () => {
    const payload = buildPayloadCreationSarl(fullStepData());
    const signataires = payload.signataires as Array<Record<string, unknown>>;
    expect(signataires).toHaveLength(1);
    expect(signataires[0].qualite).toBe('gérant');

    const associes = payload.associes as Array<Record<string, unknown>>;
    expect(associes[0].genre).toBe('féminin');
    expect(associes[0].estGerant).toBe(false);
  });

  it('dé-dup : la date de commencement de l’exercice a une SOURCE UNIQUE (Step3)', () => {
    // Step3 fournit dateCommencement ; Step4 n'a plus de date ; pas d'acteParams.
    const stepData = {
      step1: { denomination: { denomination: 'ACME', formeJuridique: 'SARL' } },
      step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000, dateCommencement: '2026-04-01' } },
      // Legacy : même si un ancien draft contenait step4.dateDebutExercice, il est IGNORÉ.
      step4: { activite: { description: 'conseil', dateDebutExercice: '2026-01-01' } },
    } as Record<string, Record<string, unknown>>;
    const s = buildPayloadCreationSarl(stepData).societe as Record<string, unknown>;
    // exerciceDebut vient UNIQUEMENT de Step3 (dateCommencement), pas de Step4.
    expect(s.exerciceDebut).toBe('2026-04-01');
  });

  it('ne fait pas fuiter de champ hors contrat (ICE société, fonds de commerce, clauses)', () => {
    const payload = buildPayloadCreationSarl(fullStepData());
    const s = payload.societe as Record<string, unknown>;
    // ICE société non saisi à la constitution -> non renseigné (attribué post-immat).
    expect(s.iceNumero ?? null).toBeNull();
    const associes = payload.associes as Array<Record<string, unknown>>;
    for (const key of ['fondsCommerce', 'clauseAgrementMajorite', 'clausePreemptionDelai', 'delaiConvocationJours']) {
      expect(associes[0][key], `champ hors contrat ${key} ne doit pas être émis`).toBeUndefined();
      expect(s[key], `champ hors contrat ${key} ne doit pas être émis`).toBeUndefined();
    }
  });
});

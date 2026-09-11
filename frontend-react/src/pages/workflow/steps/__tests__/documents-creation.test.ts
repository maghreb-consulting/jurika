import { describe, expect, it } from 'vitest';
import {
  BOUCLES_CREATION,
  bouclesParDocument,
  CHAMPS_CREATION,
  CHOIX_STATUT_2,
  champsParDocument,
  DOCUMENTS_PARCOURS,
  REPRISES_AUTOMATIQUES,
  VARIABLES_SANS_SOURCE,
} from '../documents-creation';
// La selection reelle de l'etape 7 : c'est elle qui traduit les lignes cochees
// en codes de modele, et donc elle qui decide des champs affiches.
import { codesRetenus } from '../Step7Generation';

/**
 * Lot B (2026-09-11) — LE CATALOGUE DU PARCOURS DU 9 SEPTEMBRE.
 *
 * Ces tests portaient, au lot 5, sur sept modèles et une quinzaine de champs
 * écrits à la main. Le corpus du cabinet en compte vingt-trois et 160 champs, et
 * le catalogue est désormais DÉRIVÉ. On ne vérifie donc plus une liste : on
 * vérifie les invariants que l'étape 7 suppose, et que la dérivation pourrait
 * casser en silence à la prochaine livraison du corpus.
 *
 * La règle centrale n'a pas changé, c'est celle posée par l'utilisateur :
 * **un champ n'apparaît que si le document qui le consomme est retenu.**
 */
describe('Le catalogue — ce que le parcours du 9 septembre déclare', () => {
  it('porte les 23 modèles du corpus et les DIX documents du statut 2', () => {
    expect(DOCUMENTS_PARCOURS).toHaveLength(23);
    expect(CHOIX_STATUT_2).toHaveLength(10);
    // Les dix lignes sont bien les lignes 2 à 11 du parcours.
    expect(CHOIX_STATUT_2.map((c) => c.ligne)).toEqual([2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
  });

  it('ouvre les 160 champs du lot A, plus les saisies héritées du lot 5', () => {
    // 160 = le périmètre établi par l'analyse d'écart du lot A.
    // +20 = des variables que le lot A classe « déjà résolues » — et elles le
    // sont, le mapper sait les lire — mais dont la SOURCE est une saisie :
    // régime fiscal, nature de l'activité, assujettissement à la TVA, direction
    // régionale… Elles arrivaient des compléments du lot 5, que ce lot remplace.
    // Sans champ, plus personne ne les renseigne, et la déclaration d'existence
    // part à la DGI sans régime fiscal déclaré. Résolvable n'est pas renseigné.
    const heritees = CHAMPS_CREATION.filter((c) => c.heritee);

    expect(CHAMPS_CREATION).toHaveLength(180);
    expect(heritees).toHaveLength(20);
  });

  it('ouvre la date de début d’activité, que le parcours témoin a trouvée muette', () => {
    // La vingtième héritée, trouvée en LISANT le document : la fiche de
    // renseignements refusait de sortir sur « Date de début d'activité
    // envisagée : . ». Le mapper lit `formulaires.dateDebutActivite` et en
    // dérive $DATE_COMMENCEMENT_EXPLOITATION puis $DATE_DEBUT_ACTIVITE — mais
    // aucun écran ne la produisait.
    const champ = CHAMPS_CREATION.find((c) => c.variable === 'DATE_DEBUT_ACTIVITE');

    expect(champ?.cle).toBe('dateDebutActivite');
    expect(champ?.type).toBe('date');
    expect(champ?.heritee).toBe(true);
    // Cinq documents l'attendent : elle ne se saisit pourtant qu'une fois.
    expect(champ?.documents).toContain('FICHE_RENSEIGNEMENTS_CREATION');
    expect(champ?.documents).toContain('DEMANDE_AFFILIATION_CNSS');
  });

  it('n’affiche AUCUN champ dans le cas par défaut — statuts et annonce légale', () => {
    // Le nombre que l'employé subit, et non les 180 ouverts au total. Les deux
    // documents cochés par défaut ne consomment que des données du dossier :
    // dénomination, capital, associés, gérants, siège, objet. Rien à ressaisir.
    for (const forme of ['SARL', 'SARL_AU'] as const) {
      const codes = codesRetenus(new Set([3, 11]), forme, undefined);
      expect(codes).toHaveLength(2);
      expect(champsParDocument(codes)).toEqual([]);
      expect(bouclesParDocument(codes)).toEqual([]);
    }

    // À l'autre bout : les dix lignes du statut 2 toutes cochées.
    const toutes = new Set(CHOIX_STATUT_2.map((c) => c.ligne));
    const tousCodes = codesRetenus(toutes, 'SARL', 'BAIL');
    const simples = champsParDocument(tousCodes).reduce((n, g) => n + g.champs.length, 0);
    expect(simples).toBe(65);
    expect(bouclesParDocument(tousCodes)).toHaveLength(4);
  });

  it('donne aux cases à cocher les libellés EXACTS du modèle', () => {
    // Le moteur coche la case dont le libellé est exactement égal à la valeur.
    // Une liste bâtie sur le résumé du dictionnaire (« Résultat net réel ») ne
    // cocherait jamais la case du gabarit (« Impôt sur les sociétés — régime du
    // résultat net réel ») : le formulaire partirait vide à l'administration.
    const regime = CHAMPS_CREATION.find((c) => c.variable === 'DE_REGIME_RESULTAT');

    expect(regime?.options).toContain('Impôt sur les sociétés — régime du résultat net réel');
    expect(regime?.type).toBe('select');
  });

  it('n’ouvre AUCUN champ pour les 55 variables sans source', () => {
    expect(VARIABLES_SANS_SOURCE).toHaveLength(55);
    const ouverts = new Set(CHAMPS_CREATION.map((c) => c.variable));
    for (const sansSource of VARIABLES_SANS_SOURCE) {
      expect(ouverts.has(sansSource)).toBe(false);
    }
  });

  it('rattache chaque champ à au moins un document qui le consomme', () => {
    const codes = new Set(DOCUMENTS_PARCOURS.map((d) => d.code));
    for (const champ of CHAMPS_CREATION) {
      expect(champ.documents.length).toBeGreaterThan(0);
      for (const code of champ.documents) expect(codes.has(code)).toBe(true);
    }
  });

  it('ne déclare jamais deux champs sous la même clé de payload', () => {
    // Deux champs de même clé se recouvriraient : la saisie de l'un effacerait
    // l'autre sans erreur, et un document sortirait avec la valeur du voisin.
    const cles = CHAMPS_CREATION.map((c) => c.cle);
    expect(new Set(cles).size).toBe(cles.length);
  });

  it('donne un libellé lisible à chaque champ — jamais le nom brut de la variable', () => {
    for (const champ of CHAMPS_CREATION) {
      expect(champ.label.trim()).not.toBe('');
      // Le souligné est la marque du nom technique. Un sigle laissé tel quel
      // (« RIB », « ICE », « TVA ») se lit, lui, parfaitement.
      expect(champ.label, `champ ${champ.variable}`).not.toContain('_');
    }
  });
});

/**
 * Énoncé 4 — « Au statut 2 : Statuts et Annonce légale cochés par défaut, les
 * huit autres décochés. » Décision du cabinet, non rouvrable.
 */
describe('Les dix documents du statut 2 — ce qui est coché par défaut', () => {
  it('coche Statuts et Annonce légale, et EUX SEULS', () => {
    const parDefaut = CHOIX_STATUT_2.filter((c) => c.cocheParDefaut);

    expect(parDefaut).toHaveLength(2);
    expect(parDefaut.flatMap((c) => c.codes).sort()).toEqual([
      'ANNONCE_LEGALE_CONSTITUTION',
      'STATUTS_SARL',
      'STATUTS_SARL_AU',
    ]);
  });

  it('laisse les huit autres décochés — y compris ceux dits « tous dossiers »', () => {
    const decoches = CHOIX_STATUT_2.filter((c) => !c.cocheParDefaut);

    expect(decoches).toHaveLength(8);
    // La demande de taxe professionnelle porte « Tous dossiers » au parcours, et
    // reste pourtant décochée : c'est l'employé qui décide, pas la condition.
    expect(decoches.map((c) => c.ligne)).toContain(8);
  });

  it('affiche la condition d’application de chaque document, telle qu’elle figure', () => {
    // L'employé décide en connaissance de cause ; le système ne décide pas à sa
    // place. Une case sans sa condition serait une question sans énoncé.
    for (const choix of CHOIX_STATUT_2) {
      expect(choix.condition.trim()).not.toBe('');
    }
    const acteNomination = CHOIX_STATUT_2.find((c) => c.ligne === 5);
    expect(acteNomination?.condition).toContain('gérance n’est pas désignée dans les statuts'
      .replace('’', "'"));
  });

  it('ne laisse pas l’employé choisir entre SARL et SARL AU', () => {
    // Deux lignes portent deux variantes : ce n'est pas un choix offert, c'est
    // une conséquence — de $ASSOCIE_UNIQUE et de la voie retenue pour le siège.
    const aDeuxVariantes = CHOIX_STATUT_2.filter((c) => c.codes.length > 1);
    expect(aDeuxVariantes.map((c) => c.ligne).sort()).toEqual([2, 3]);
  });
});

/**
 * Énoncé 5 — « Un champ conditionnel n'apparaît pas tant que son document n'est
 * pas retenu. »
 */
describe('Un champ n’apparaît que si son document est retenu — énoncé 5', () => {
  it('n’affiche AUCUN champ pour la sélection par défaut', () => {
    // Statuts et Annonce légale ne réclament aucune des 160 : tout ce qu'ils
    // consomment est déjà résolu par la plateforme. C'est un résultat, pas une
    // coïncidence — et il rend le parcours par défaut muet.
    const codesParDefaut = CHOIX_STATUT_2.filter((c) => c.cocheParDefaut).flatMap((c) => c.codes);

    expect(champsParDocument(codesParDefaut)).toEqual([]);
    expect(bouclesParDocument(codesParDefaut)).toEqual([]);
  });

  it('fait apparaître les champs de la déclaration CNDP quand elle est retenue, et eux seuls', () => {
    const sansCndp = champsParDocument(['STATUTS_SARL']).flatMap((g) =>
      g.champs.map((c) => c.cle),
    );
    expect(sansCndp).not.toContain('cndpNatureDeclaration');

    const avecCndp = champsParDocument(['STATUTS_SARL', 'DECLARATION_CNDP']).flatMap((g) =>
      g.champs.map((c) => c.cle),
    );
    expect(avecCndp).toContain('cndpNatureDeclaration');
  });

  it('fait disparaître les champs quand leur document est retiré', () => {
    const avec = champsParDocument(['DEMANDE_TAXE_PROFESSIONNELLE']).flatMap((g) =>
      g.champs.map((c) => c.cle),
    );
    expect(avec).toContain('tpCommune');

    const sans = champsParDocument(['DECLARATION_EXISTENCE']).flatMap((g) =>
      g.champs.map((c) => c.cle),
    );
    expect(sans).not.toContain('tpCommune');
  });

  it('ne demande JAMAIS deux fois un champ partagé par deux documents', () => {
    // `$DECLARANT_PIECE_NUMERO` sert les trois imprimés administratifs.
    const groupes = champsParDocument([
      'DEMANDE_TAXE_PROFESSIONNELLE',
      'DECLARATION_EXISTENCE',
      'DECLARATION_IMMATRICULATION_RC',
    ]);
    const cles = groupes.flatMap((g) => g.champs.map((c) => c.cle));

    expect(cles.filter((c) => c === 'declarantPieceNumero')).toHaveLength(1);
    expect(new Set(cles).size).toBe(cles.length);
  });

  it('groupe chaque champ sous un document qui le consomme réellement', () => {
    const codes = ['CONTRAT_BAIL', 'DEMANDE_TAXE_PROFESSIONNELLE'];
    for (const groupe of champsParDocument(codes)) {
      for (const champ of groupe.champs) {
        expect(champ.documents).toContain(groupe.code);
      }
    }
  });

  it('sort les champs de boucle du flux simple : ils s’affichent par leur boucle', () => {
    const groupes = champsParDocument(['DECLARATION_BENEFICIAIRES_EFFECTIFS']);
    for (const groupe of groupes) {
      for (const champ of groupe.champs) expect(champ.boucle).toBeNull();
    }
    // Et la boucle, elle, est bien proposée.
    expect(bouclesParDocument(['DECLARATION_BENEFICIAIRES_EFFECTIFS']).map((b) => b.nom))
      .toContain('BENEFICIAIRES_EFFECTIFS');
  });
});

/**
 * Énoncé 6 — « Aucun champ ne redemande une donnée déjà en base. »
 */
describe('Aucun champ ne redemande une donnée déjà saisie — énoncé 6', () => {
  /**
   * Les variables que les étapes 1 à 6 produisent déjà, ou que la plateforme
   * dérive. Elles sont « déjà résolues » ou « dérivables » au lot A : en ouvrir
   * un champ serait demander deux fois la même chose.
   */
  const DEJA_EN_BASE = [
    'DENOMINATION',
    'SIGLE',
    'OBJET_SOCIAL',
    'CAPITAL_CHIFFRES',
    'CAPITAL_LETTRES',
    'NOMBRE_PARTS',
    'VALEUR_NOMINALE_PART',
    'SIEGE_SOCIAL',
    'VILLE',
    'FORME_JURIDIQUE',
    'CERTIFICAT_NEGATIF_NUMERO',
    'CERTIFICAT_NEGATIF_DATE',
    'GERANT_NOM',
    'GERANT_PRENOM',
    'GERANT_ADRESSE',
    'GERANT_DATE_NAISSANCE',
    'GERANT_LIEU_NAISSANCE',
    'ASSOCIE_NOM',
    'ASSOCIE_PRENOM',
    'ASSOCIE_NOMBRE_PARTS',
    'ASSOCIE_PIECE_NUMERO',
    'TRIBUNAL_TYPE',
    'TRIBUNAL_VILLE',
    'RC_NUMERO',
    'IDENTIFIANT_FISCAL',
    'ICE',
    // Dérivées, jamais saisies : le dossier les porte ou le calcul les produit.
    'DOSSIER_NUMERO',
    'DOSSIER_DATE_OUVERTURE',
    'DOSSIER_CHARGE',
    'SOUSCRIPTIONS_TOTAL_VERSE',
    'PIECES_NOMBRE_TOTAL',
    'BE_NUMERO',
    'RBE_NOMBRE_BENEFICIAIRES',
  ];

  it('n’ouvre de champ pour aucune de ces variables', () => {
    const ouverts = new Set(CHAMPS_CREATION.map((c) => c.variable));
    const doublons = DEJA_EN_BASE.filter((v) => ouverts.has(v));

    expect(doublons, `redemandé alors que déjà en base : ${doublons.join(', ')}`).toEqual([]);
  });

  it('rappelle à l’écran ce qui est repris sans ressaisie', () => {
    expect(REPRISES_AUTOMATIQUES.length).toBeGreaterThanOrEqual(6);
    const libelles = REPRISES_AUTOMATIQUES.map((r) => r.libelle).join(' | ');
    expect(libelles).toMatch(/Dénomination/);
    expect(libelles).toMatch(/Souscriptions/);
  });
});

describe('Les boucles — une occurrence n’est pas un champ répété à la main', () => {
  it('déclare neuf boucles, toutes rattachées à un document', () => {
    expect(BOUCLES_CREATION.length).toBe(9);
    const codes = new Set(DOCUMENTS_PARCOURS.map((d) => d.code));
    for (const boucle of BOUCLES_CREATION) {
      expect(boucle.documents.length).toBeGreaterThan(0);
      for (const code of boucle.documents) expect(codes.has(code)).toBe(true);
      expect(boucle.champs.length).toBeGreaterThan(0);
    }
  });

  it('ne demande jamais le RANG d’une occurrence', () => {
    // `$BE_NUMERO`, `$TRAITEMENT_NUMERO`, `$PIECE_NUMERO` sont dérivés du rang.
    // Les demander reviendrait à faire compter l'employé — et à lui faire porter
    // une erreur de numérotation.
    const rangs = ['BE_NUMERO', 'TRAITEMENT_NUMERO', 'PIECE_NUMERO', 'ACTE_FORMATION_NUMERO'];
    const ouverts = new Set(CHAMPS_CREATION.map((c) => c.variable));
    for (const rang of rangs) expect(ouverts.has(rang)).toBe(false);
  });
});

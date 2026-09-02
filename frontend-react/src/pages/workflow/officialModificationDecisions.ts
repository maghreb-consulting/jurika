/**
 * Catalogue OFFICIEL des décisions de modification (voie directeur) — Phase 0.
 *
 * Source verbatim des deux inventaires directeur :
 *  - `1_Decisions_AG_SARL_1.docx`            → SARL (AGO / AGE / décision commune)
 *  - `2_Decisions_Associe_Unique_SARL_AU_1`  → SARL AU (ordinaire / extraordinaire / commune)
 *
 * Chaque item porte un `resolutionType` (snake_case) parmi les types gérés par le
 * moteur PV directeur (`ModificationDirecteurMapper.fillTypeVars` /
 * `ModificationOperationForm.RES_SPECS`). Les décisions SANS bloc typé dédié — ou
 * dont le modèle de la forme concernée n'a pas de branche — sont routées vers le
 * type générique (`modification_statuts_autre` / `mise_harmonie_statuts`) afin que le
 * PV rende quand même, et signalées via `generic: true`.
 *
 * RÈGLE : 0 item non mappé (garanti par le test `officialModificationDecisions.test.ts`).
 *
 * NB (2026-08-10) : le modèle `PV_MODIFICATION_SARL_AU` ne possède PAS de branche
 * `operation_restructuration` (28 types AU, restructuration = SARL uniquement). Les
 * fusions/scissions/apports figurent bien dans le doc officiel AU : on les AFFICHE
 * mais on les route vers le générique `modification_statuts_autre` (flag + note).
 */

export type ModForme = 'SARL' | 'SARL_AU';

/** Groupes d'affichage (accordéons de l'étape 1). */
export type ModGroup =
  | 'AGO' // SARL — assemblée générale ordinaire
  | 'AGE' // SARL — assemblée générale extraordinaire
  | 'ORDINAIRE' // SARL AU — décisions de nature ordinaire
  | 'EXTRAORDINAIRE' // SARL AU — décisions de nature extraordinaire
  | 'COMMUNE'; // décision commune (pouvoirs formalités)

export interface OfficialDecision {
  /** Identifiant stable (kebab) — clé de sélection. */
  id: string;
  /** Intitulé VERBATIM du document officiel. */
  label: string;
  /** Groupe d'assemblée. */
  group: ModGroup;
  /** Sous-section numérotée du document officiel (titre de regroupement). */
  subGroup: string;
  /** Type de résolution du moteur directeur (snake_case). */
  resolutionType: string;
  /** true = routé vers un type générique faute de bloc typé dédié (à signaler UI). */
  generic?: boolean;
  /** true = la décision fait entrer un nouvel associé → saisie + OCR CIN (étape 2). */
  newAssocie?: boolean;
  /** Note explicative (routage générique, composite, publicité spécifique…). */
  note?: string;
}

/**
 * Liste canonique des `RESOLUTION_TYPE` gérés par le moteur PV directeur
 * (miroir de `ModificationDirecteurMapper` / `RES_SPECS`). En Phase 2, cette
 * constante sera dérivée du module partagé `RES_SPECS` extrait ; le test de
 * cohérence garantit qu'aucun mapping ne dérive.
 */
export const RESOLUTION_TYPES = new Set<string>([
  // Comptes & résultat
  'approbation_comptes',
  'affectation_resultat',
  'distribution_dividendes',
  'distribution_reserves',
  'acompte_dividendes',
  // Gérance
  'nomination_gerant',
  'renouvellement_gerant',
  'revocation_gerant',
  'remuneration_gerant',
  // Contrôle & conventions
  'conventions_reglementees',
  'commissaire_comptes',
  // Formation & pouvoirs
  'ratification_actes_formation',
  'autorisation_gerance',
  'pouvoirs_formalites',
  // Capital
  'augmentation_capital_numeraire',
  'augmentation_capital_nature',
  'augmentation_capital_incorporation',
  'reduction_capital',
  // Identité & statuts
  'modification_denomination',
  'modification_objet',
  'transfert_siege',
  'prorogation_duree',
  'modification_exercice',
  'mise_harmonie_statuts',
  'modification_statuts_autre',
  // Parts sociales
  'agrement_cession',
  'agrement_transmission',
  'nantissement_parts',
  'cession_parts_pluripersonnelle',
  // Transformation & restructuration
  'transformation',
  'designation_commissaire_transformation',
  'operation_restructuration',
  'capitaux_propres_art86',
]);

// ============================================================================
// SARL — Inventaire des décisions en assemblée générale des associés
// ============================================================================
const SARL_DECISIONS: OfficialDecision[] = [
  // ---- I. Assemblée générale ordinaire (AGO) ----
  // 1. Comptes, résultat et distributions
  {
    id: 'sarl-ago-approbation-comptes',
    label:
      'Approbation des comptes annuels (bilan, CPC, ETIC), du rapport de gestion de la gérance et quitus à la gérance',
    group: 'AGO',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'approbation_comptes',
  },
  {
    id: 'sarl-ago-affectation-resultat',
    label:
      "Affectation du résultat de l'exercice (réserve légale, réserves facultatives, report à nouveau, dividende)",
    group: 'AGO',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'affectation_resultat',
  },
  {
    id: 'sarl-ago-distribution-dividendes',
    label: 'Distribution de dividendes et fixation de la date de mise en paiement',
    group: 'AGO',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'distribution_dividendes',
  },
  {
    id: 'sarl-ago-distribution-reserves',
    label:
      'Distribution exceptionnelle de sommes prélevées sur les réserves facultatives (avec indication des postes de prélèvement)',
    group: 'AGO',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'distribution_reserves',
  },
  {
    id: 'sarl-ago-acompte-dividendes',
    label: "Distribution d'acomptes sur dividendes avant l'approbation des comptes de l'exercice",
    group: 'AGO',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'acompte_dividendes',
  },
  // 2. Gérance
  {
    id: 'sarl-ago-nomination-gerant',
    label:
      "Nomination du ou des gérant(s) (y compris en remplacement d'un gérant décédé ou démissionnaire)",
    group: 'AGO',
    subGroup: 'Gérance',
    resolutionType: 'nomination_gerant',
  },
  {
    id: 'sarl-ago-renouvellement-gerant',
    label: 'Renouvellement du mandat du gérant',
    group: 'AGO',
    subGroup: 'Gérance',
    resolutionType: 'renouvellement_gerant',
  },
  {
    id: 'sarl-ago-revocation-gerant',
    label: "Révocation du gérant ou prise d'acte de sa démission",
    group: 'AGO',
    subGroup: 'Gérance',
    resolutionType: 'revocation_gerant',
  },
  {
    id: 'sarl-ago-remuneration-gerant',
    label: 'Fixation ou modification de la rémunération du gérant (mandat social ou contrat de travail)',
    group: 'AGO',
    subGroup: 'Gérance',
    resolutionType: 'remuneration_gerant',
  },
  // 3. Contrôle et conventions
  {
    id: 'sarl-ago-conventions-reglementees',
    label:
      'Approbation des conventions réglementées conclues entre la société et un gérant ou un associé (art. 64)',
    group: 'AGO',
    subGroup: 'Contrôle et conventions',
    resolutionType: 'conventions_reglementees',
  },
  {
    id: 'sarl-ago-commissaire-comptes',
    label:
      'Nomination, renouvellement ou cessation des fonctions du commissaire aux comptes et fixation de ses honoraires',
    group: 'AGO',
    subGroup: 'Contrôle et conventions',
    resolutionType: 'commissaire_comptes',
  },
  // 4. Autorisations et décisions diverses
  {
    id: 'sarl-ago-ratification-actes-formation',
    label:
      'Ratification des actes accomplis pour le compte de la société en formation (reprise des engagements)',
    group: 'AGO',
    subGroup: 'Autorisations et décisions diverses',
    resolutionType: 'ratification_actes_formation',
  },
  {
    id: 'sarl-ago-autorisation-gerance',
    label:
      "Autorisations données à la gérance : cautions, avals et garanties ; conclusion d'un contrat de location-gérance d'un fonds de commerce ; prise en location-gérance",
    group: 'AGO',
    subGroup: 'Autorisations et décisions diverses',
    resolutionType: 'autorisation_gerance',
  },
  {
    id: 'sarl-ago-autre-decision',
    label: "Toute autre décision n'emportant pas modification des statuts",
    group: 'AGO',
    subGroup: 'Autorisations et décisions diverses',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: "Décision ordinaire sans bloc typé dédié — rendue via le libellé libre.",
  },

  // ---- II. Assemblée générale extraordinaire (AGE) ----
  // 1. Augmentation du capital social
  {
    id: 'sarl-age-augcap-numeraire',
    label: "Augmentation de capital en numéraire, avec ou sans prime d'émission",
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_numeraire',
  },
  {
    id: 'sarl-age-augcap-prime',
    label: "Fixation de la prime d'émission et affectation de son montant",
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_numeraire',
    note: "Volet « prime d'émission » de l'augmentation en numéraire.",
  },
  {
    id: 'sarl-age-augcap-compensation',
    label: 'Augmentation de capital par compensation avec des créances liquides et exigibles sur la société',
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_numeraire',
    note: 'Libération par compensation de créances.',
  },
  {
    id: 'sarl-age-augcap-nature',
    label: 'Augmentation de capital par apport en nature et approbation du rapport du commissaire aux apports',
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_nature',
    newAssocie: true,
  },
  {
    id: 'sarl-age-augcap-fonds-commerce',
    label:
      "Augmentation de capital par apport d'un fonds de commerce (avec publicité spécifique au JAL et au Bulletin officiel)",
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_nature',
    newAssocie: true,
    note: 'Apport en nature = fonds de commerce.',
  },
  {
    id: 'sarl-age-augcap-incorporation',
    label: 'Augmentation de capital par incorporation de réserves, bénéfices ou primes',
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_incorporation',
  },
  {
    id: 'sarl-age-augcap-parts-nouvelles',
    label:
      'Augmentation de capital par création de parts nouvelles ou par élévation de la valeur nominale des parts existantes',
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_numeraire',
    note: "Modalité (création de parts / élévation du nominal) de l'augmentation.",
  },
  {
    id: 'sarl-age-augcap-dps',
    label:
      'Suppression ou renonciation au droit préférentiel de souscription (au profit de personnes dénommées)',
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'augmentation_capital_numeraire',
    note: 'Volet « suppression du DPS » de l\'augmentation en numéraire.',
  },
  {
    id: 'sarl-age-augcap-constatation',
    label: "Constatation de la réalisation définitive de l'augmentation de capital",
    group: 'AGE',
    subGroup: 'Augmentation du capital social',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Constatation — pas de bloc typé dédié.',
  },
  // 2. Réduction du capital social
  {
    id: 'sarl-age-redcap-pertes',
    label: 'Réduction du capital motivée par des pertes (apurement des pertes)',
    group: 'AGE',
    subGroup: 'Réduction du capital social',
    resolutionType: 'reduction_capital',
  },
  {
    id: 'sarl-age-redcap-rachat',
    label: 'Réduction du capital non motivée par des pertes, par voie de rachat de parts sociales',
    group: 'AGE',
    subGroup: 'Réduction du capital social',
    resolutionType: 'reduction_capital',
  },
  {
    id: 'sarl-age-redcap-nominal',
    label: 'Réduction du capital par diminution de la valeur nominale des parts',
    group: 'AGE',
    subGroup: 'Réduction du capital social',
    resolutionType: 'reduction_capital',
  },
  {
    id: 'sarl-age-redcap-nombre-parts',
    label: 'Réduction du capital par réduction du nombre de parts (échange de parts)',
    group: 'AGE',
    subGroup: 'Réduction du capital social',
    resolutionType: 'reduction_capital',
  },
  {
    id: 'sarl-age-coup-accordeon',
    label: "Réduction du capital à zéro suivie d'une augmentation immédiate (« coup d'accordéon »)",
    group: 'AGE',
    subGroup: 'Réduction du capital social',
    resolutionType: 'reduction_capital',
    note: "Opération composite : ajouter aussi une résolution d'augmentation de capital.",
  },
  // 3. Modifications statutaires diverses
  {
    id: 'sarl-age-denomination',
    label: 'Modification de la dénomination sociale',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_denomination',
  },
  {
    id: 'sarl-age-objet-extension',
    label: "Extension de l'objet social",
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_objet',
  },
  {
    id: 'sarl-age-objet-changement',
    label: "Changement (modification) de l'objet social",
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_objet',
  },
  {
    id: 'sarl-age-transfert-siege-meme',
    label: 'Transfert du siège social dans la même préfecture ou province',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'transfert_siege',
  },
  {
    id: 'sarl-age-transfert-siege-autre',
    label: 'Transfert du siège social dans une autre préfecture ou province',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'transfert_siege',
  },
  {
    id: 'sarl-age-prorogation-duree',
    label: 'Prorogation de la durée de la société',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'prorogation_duree',
  },
  {
    id: 'sarl-age-reduction-duree',
    label: 'Réduction de la durée de la société',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Pas de bloc typé dédié — rendu via le libellé libre.',
  },
  {
    id: 'sarl-age-date-cloture',
    label: "Modification de la date de clôture de l'exercice social",
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_exercice',
  },
  {
    id: 'sarl-age-valeur-nominale',
    label: 'Modification de la valeur nominale des parts, division ou regroupement des parts',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Pas de bloc typé dédié — rendu via le libellé libre.',
  },
  {
    id: 'sarl-age-mise-harmonie',
    label: 'Mise en harmonie et refonte des statuts (conformité à la loi n° 5-96)',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'mise_harmonie_statuts',
  },
  {
    id: 'sarl-age-clauses-statutaires',
    label:
      'Modification des clauses statutaires (agrément, cession, répartition des bénéfices, modalités de la gérance, etc.)',
    group: 'AGE',
    subGroup: 'Modifications statutaires diverses',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Clause statutaire libre — rendu via le libellé libre.',
  },
  // 4. Cession, transmission et nantissement de parts
  {
    id: 'sarl-age-agrement-cession',
    label: 'Agrément de cession de parts sociales à des tiers (art. 58)',
    group: 'AGE',
    subGroup: 'Cession, transmission et nantissement de parts',
    resolutionType: 'agrement_cession',
    newAssocie: true,
  },
  {
    id: 'sarl-age-agrement-nouvel-associe',
    label: "Agrément d'un nouvel associé (entrée au capital)",
    group: 'AGE',
    subGroup: 'Cession, transmission et nantissement de parts',
    resolutionType: 'agrement_cession',
    newAssocie: true,
  },
  {
    id: 'sarl-age-agrement-transmission',
    label:
      'Agrément de transmission de parts par voie de succession, de donation ou de liquidation de communauté',
    group: 'AGE',
    subGroup: 'Cession, transmission et nantissement de parts',
    resolutionType: 'agrement_transmission',
    newAssocie: true,
  },
  {
    id: 'sarl-age-nantissement',
    label: 'Agrément du nantissement de parts sociales (consentement au projet de nantissement)',
    group: 'AGE',
    subGroup: 'Cession, transmission et nantissement de parts',
    resolutionType: 'nantissement_parts',
  },
  {
    id: 'sarl-age-rachat-refus-agrement',
    label: "Consentement au rachat des parts en cas de refus d'agrément",
    group: 'AGE',
    subGroup: 'Cession, transmission et nantissement de parts',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Pas de bloc typé dédié — rendu via le libellé libre.',
  },
  // 5. Transformation de la société
  {
    id: 'sarl-age-transfo-sa-ca',
    label: 'Transformation en société anonyme à conseil d\'administration',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'transformation',
  },
  {
    id: 'sarl-age-transfo-sa-directoire',
    label: 'Transformation en société anonyme à directoire et conseil de surveillance',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'transformation',
  },
  {
    id: 'sarl-age-transfo-snc',
    label: 'Transformation en société en nom collectif — consentement unanime des associés',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'transformation',
  },
  {
    id: 'sarl-age-transfo-sas',
    label: 'Transformation en société par actions simplifiée (SAS) — unanimité',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'transformation',
  },
  {
    id: 'sarl-age-transfo-commandite-civile',
    label:
      'Transformation en société en commandite simple, en commandite par actions ou en société civile — unanimité',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'transformation',
  },
  {
    id: 'sarl-age-designation-commissaire-transfo',
    label: 'Désignation du commissaire à la transformation',
    group: 'AGE',
    subGroup: 'Transformation de la société',
    resolutionType: 'designation_commissaire_transformation',
  },
  // 6. Décisions requérant l'unanimité des associés
  {
    id: 'sarl-age-changement-nationalite',
    label: 'Changement de nationalité de la société',
    group: 'AGE',
    subGroup: "Décisions requérant l'unanimité des associés",
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Décision à l\'unanimité — pas de bloc typé dédié.',
  },
  {
    id: 'sarl-age-augmentation-engagements',
    label: 'Augmentation des engagements des associés',
    group: 'AGE',
    subGroup: "Décisions requérant l'unanimité des associés",
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: 'Décision à l\'unanimité — pas de bloc typé dédié.',
  },
  // 7. Opérations de restructuration
  {
    id: 'sarl-age-fusion',
    label: "Fusion (par absorption ou par création d'une société nouvelle)",
    group: 'AGE',
    subGroup: 'Opérations de restructuration',
    resolutionType: 'operation_restructuration',
  },
  {
    id: 'sarl-age-scission',
    label: 'Scission',
    group: 'AGE',
    subGroup: 'Opérations de restructuration',
    resolutionType: 'operation_restructuration',
  },
  {
    id: 'sarl-age-apport-partiel',
    label: "Apport partiel d'actif",
    group: 'AGE',
    subGroup: 'Opérations de restructuration',
    resolutionType: 'operation_restructuration',
  },

  // ---- III. Décision commune à toute assemblée ----
  {
    id: 'sarl-commune-pouvoirs-formalites',
    label:
      "Attribution de tous pouvoirs au porteur d'un original, d'une copie ou d'un extrait du procès-verbal pour l'accomplissement des formalités légales de publicité (dépôt au greffe, insertion au JAL et au Bulletin officiel, inscription modificative au registre du commerce)",
    group: 'COMMUNE',
    subGroup: 'Décision commune',
    resolutionType: 'pouvoirs_formalites',
  },
];

// ============================================================================
// SARL À ASSOCIÉ UNIQUE (SARL AU) — décisions de l'associé unique
// ============================================================================
const SARL_AU_DECISIONS: OfficialDecision[] = [
  // ---- I. Décisions de nature ordinaire ----
  // 1. Comptes, résultat et distributions
  {
    id: 'au-ord-approbation-comptes',
    label: "Approbation des comptes annuels et du rapport de gestion ; affectation du résultat de l'exercice",
    group: 'ORDINAIRE',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'approbation_comptes',
  },
  {
    id: 'au-ord-distribution',
    label:
      'Distribution de dividendes ou de sommes prélevées sur les réserves disponibles (avec indication des postes de prélèvement)',
    group: 'ORDINAIRE',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'distribution_dividendes',
  },
  {
    id: 'au-ord-acompte-dividendes',
    label: "Distribution d'acomptes sur dividendes",
    group: 'ORDINAIRE',
    subGroup: 'Comptes, résultat et distributions',
    resolutionType: 'acompte_dividendes',
  },
  // 2. Gérance, contrôle et conventions
  {
    id: 'au-ord-gerant',
    label: 'Nomination, renouvellement ou révocation du gérant',
    group: 'ORDINAIRE',
    subGroup: 'Gérance, contrôle et conventions',
    resolutionType: 'nomination_gerant',
    note: 'Couvre nomination / renouvellement / révocation.',
  },
  {
    id: 'au-ord-remuneration-gerant',
    label: 'Fixation ou modification de la rémunération du gérant',
    group: 'ORDINAIRE',
    subGroup: 'Gérance, contrôle et conventions',
    resolutionType: 'remuneration_gerant',
  },
  {
    id: 'au-ord-conventions',
    label:
      "Approbation des conventions conclues entre la société et l'associé unique ou le gérant (mention au registre des décisions)",
    group: 'ORDINAIRE',
    subGroup: 'Gérance, contrôle et conventions',
    resolutionType: 'conventions_reglementees',
  },
  {
    id: 'au-ord-commissaire-comptes',
    label: 'Nomination, renouvellement ou cessation des fonctions du commissaire aux comptes',
    group: 'ORDINAIRE',
    subGroup: 'Gérance, contrôle et conventions',
    resolutionType: 'commissaire_comptes',
  },
  // 3. Autorisations et décisions diverses
  {
    id: 'au-ord-ratification',
    label:
      'Ratification des actes accomplis pour le compte de la société en formation (reprise des engagements)',
    group: 'ORDINAIRE',
    subGroup: 'Autorisations et décisions diverses',
    resolutionType: 'ratification_actes_formation',
  },
  {
    id: 'au-ord-autorisation-gerance',
    label: "Autorisations à la gérance : cautions, avals et garanties ; location-gérance d'un fonds de commerce",
    group: 'ORDINAIRE',
    subGroup: 'Autorisations et décisions diverses',
    resolutionType: 'autorisation_gerance',
  },

  // ---- II. Décisions de nature extraordinaire ----
  // 1. Opérations sur le capital
  {
    id: 'au-ext-augcap-numeraire',
    label:
      "Augmentation de capital en numéraire ou par compensation de créances, avec ou sans prime d'émission (souscription réservée à l'associé unique)",
    group: 'EXTRAORDINAIRE',
    subGroup: 'Opérations sur le capital',
    resolutionType: 'augmentation_capital_numeraire',
  },
  {
    id: 'au-ext-augcap-incorporation',
    label:
      'Augmentation de capital par incorporation de réserves, par création de parts nouvelles ou par élévation de la valeur nominale',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Opérations sur le capital',
    resolutionType: 'augmentation_capital_incorporation',
  },
  {
    id: 'au-ext-augcap-nature',
    label:
      "Augmentation de capital par apport en nature (y compris apport d'un fonds de commerce, avec publicité spécifique) et approbation du rapport du commissaire aux apports",
    group: 'EXTRAORDINAIRE',
    subGroup: 'Opérations sur le capital',
    resolutionType: 'augmentation_capital_nature',
  },
  {
    id: 'au-ext-reduction-capital',
    label: 'Réduction du capital social (motivée ou non par des pertes)',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Opérations sur le capital',
    resolutionType: 'reduction_capital',
  },
  // 2. Modifications statutaires
  {
    id: 'au-ext-denomination',
    label: 'Changement de la dénomination sociale',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'modification_denomination',
  },
  {
    id: 'au-ext-objet',
    label: "Extension ou changement de l'objet social",
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'modification_objet',
  },
  {
    id: 'au-ext-transfert-siege',
    label: 'Transfert du siège social',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'transfert_siege',
  },
  {
    id: 'au-ext-prorogation-duree',
    label: 'Prorogation de la durée de la société',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'prorogation_duree',
  },
  {
    id: 'au-ext-date-cloture',
    label: "Changement de la date de clôture de l'exercice social",
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'modification_exercice',
  },
  {
    id: 'au-ext-mise-harmonie',
    label: 'Mise en harmonie ou modification des statuts',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Modifications statutaires',
    resolutionType: 'mise_harmonie_statuts',
  },
  // 3. Transformation et structure
  {
    id: 'au-ext-transformation',
    label: 'Transformation de la société (en SA, SAS, SNC ou autre forme)',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Transformation et structure',
    resolutionType: 'transformation',
  },
  {
    id: 'au-ext-cession-pluripersonnelle',
    label:
      "Cession de parts sociales entraînant l'entrée d'un ou plusieurs nouveaux associés (passage en SARL pluripersonnelle et mise à jour des statuts)",
    group: 'EXTRAORDINAIRE',
    subGroup: 'Transformation et structure',
    resolutionType: 'cession_parts_pluripersonnelle',
    newAssocie: true,
  },
  {
    id: 'au-ext-restructuration',
    label: 'Fusion, scission ou apport partiel d\'actif',
    group: 'EXTRAORDINAIRE',
    subGroup: 'Transformation et structure',
    resolutionType: 'modification_statuts_autre',
    generic: true,
    note: "Le modèle PV SARL AU n'a pas de bloc « restructuration » — rendu via le libellé libre.",
  },

  // ---- III. Décision commune ----
  {
    id: 'au-commune-pouvoirs-formalites',
    label:
      "Attribution de tous pouvoirs au porteur d'un original, d'une copie ou d'un extrait du procès-verbal de décisions pour l'accomplissement des formalités légales de publicité (dépôt au greffe, insertion au JAL et au Bulletin officiel, inscription modificative au registre du commerce)",
    group: 'COMMUNE',
    subGroup: 'Décision commune',
    resolutionType: 'pouvoirs_formalites',
  },
];

/** Catalogue par forme juridique. */
export const OFFICIAL_DECISIONS: Record<ModForme, OfficialDecision[]> = {
  SARL: SARL_DECISIONS,
  SARL_AU: SARL_AU_DECISIONS,
};

/** Ordre d'affichage des groupes par forme. */
export const GROUP_ORDER: Record<ModForme, ModGroup[]> = {
  SARL: ['AGO', 'AGE', 'COMMUNE'],
  SARL_AU: ['ORDINAIRE', 'EXTRAORDINAIRE', 'COMMUNE'],
};

/** Libellés lisibles des groupes. */
export const GROUP_LABELS: Record<ModGroup, string> = {
  AGO: 'Assemblée générale ordinaire (AGO)',
  AGE: 'Assemblée générale extraordinaire (AGE)',
  ORDINAIRE: 'Décisions de nature ordinaire',
  EXTRAORDINAIRE: 'Décisions de nature extraordinaire',
  COMMUNE: 'Décision commune',
};

/** Décisions applicables à une forme donnée. */
export function decisionsForForme(forme: ModForme | null | undefined): OfficialDecision[] {
  return OFFICIAL_DECISIONS[forme === 'SARL_AU' ? 'SARL_AU' : 'SARL'];
}

/** Regroupe les décisions d'une forme par groupe puis sous-groupe (ordre du doc officiel). */
export function groupedDecisions(
  forme: ModForme | null | undefined,
): { group: ModGroup; label: string; subGroups: { title: string; items: OfficialDecision[] }[] }[] {
  const list = decisionsForForme(forme);
  const key = forme === 'SARL_AU' ? 'SARL_AU' : 'SARL';
  return GROUP_ORDER[key]
    .map((group) => {
      const inGroup = list.filter((d) => d.group === group);
      const subGroups: { title: string; items: OfficialDecision[] }[] = [];
      for (const d of inGroup) {
        let sg = subGroups.find((s) => s.title === d.subGroup);
        if (!sg) {
          sg = { title: d.subGroup, items: [] };
          subGroups.push(sg);
        }
        sg.items.push(d);
      }
      return { group, label: GROUP_LABELS[group], subGroups };
    })
    .filter((g) => g.subGroups.length > 0);
}

/** Résout une décision par id (toutes formes confondues). */
export function findDecision(id: string): OfficialDecision | undefined {
  return SARL_DECISIONS.find((d) => d.id === id) ?? SARL_AU_DECISIONS.find((d) => d.id === id);
}

/** Décisions non mappées (resolutionType inconnu) — doit rester VIDE (test-garanti). */
export function unmappedDecisions(): OfficialDecision[] {
  return [...SARL_DECISIONS, ...SARL_AU_DECISIONS].filter(
    (d) => !RESOLUTION_TYPES.has(d.resolutionType),
  );
}

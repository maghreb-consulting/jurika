/**
 * Etapes des parcours et aides integrees (CDC 15.4, lot L6 E7).
 *
 * Pour chaque parcours : le libelle de chaque etape (barre de progression) et son aide
 * "ce qu'il faut faire, ce qui manque, ce qui vient ensuite", affichee en tete d'etape
 * par `AideEtape`. Textes destines aux utilisateurs : francais accentue.
 */

export interface AideEtapeTexte {
  faire: string;
  manque: string;
  ensuite: string;
}

export interface EtapeParcours {
  number: number;
  label: string;
  aide: AideEtapeTexte;
}

const MANQUE_DEFAUT =
  'les champs obligatoires sont marqués d’un astérisque (*) ; s’il en reste un vide, la validation est refusée et le champ est signalé en rouge.';

const PIECES_FACULTATIVES: AideEtapeTexte = {
  faire:
    'déposez les versions signées, légalisées ou publiées des documents produits (procès-verbal signé, annonce publiée, récépissé du greffe…). Chaque dépôt est classé et versionné dans la Data Room de la société.',
  manque: 'rien d’obligatoire : cette étape est facultative et peut rester vide.',
  ensuite: 'cliquez sur « Valider l’étape » pour afficher la synthèse ; vous pourrez revenir déposer une pièce plus tard.',
};

export const ETAPES_DISSOLUTION: EtapeParcours[] = [
  {
    number: 1,
    label: 'Saisie',
    aide: {
      faire:
        'choisissez la société à dissoudre (statut « active »), puis renseignez la date de l’assemblée, la nature et le motif de la dissolution, le liquidateur et le siège de la liquidation. La convocation est facultative.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » : les actes de dissolution seront produits à l’étape suivante.',
    },
  },
  {
    number: 2,
    label: 'Génération des actes',
    aide: {
      faire:
        'générez puis validez le procès-verbal de dissolution et l’annonce légale de dissolution. Les numéro et date de dépôt légal peuvent être complétés plus tard.',
      manque: 'le procès-verbal et l’annonce doivent être générés et validés avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées ou publiées.',
    },
  },
  { number: 3, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 4,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif de la dissolution et des documents déposés.',
      manque: 'si une donnée est erronée, revenez à l’étape concernée par la barre de progression.',
      ensuite:
        'cliquez sur « Finaliser » pour terminer le parcours, puis clôturez-le. La liquidation s’ouvre par un nouveau ticket « Liquidation ».',
    },
  },
];

export const ETAPES_LIQUIDATION: EtapeParcours[] = [
  {
    number: 1,
    label: 'Saisie',
    aide: {
      faire:
        'choisissez la société dissoute à liquider, puis renseignez la date de l’assemblée de clôture et les totaux d’actif et de passif. La date de dissolution et le liquidateur sont repris du dossier ; complétez seulement ce qui manque.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » : le procès-verbal de clôture, le rapport du liquidateur et l’annonce seront produits à l’étape suivante.',
    },
  },
  {
    number: 2,
    label: 'Génération des actes',
    aide: {
      faire:
        'saisissez les opérations de liquidation du rapport du liquidateur, puis générez et validez le procès-verbal de clôture, le rapport et l’annonce légale de clôture.',
      manque: 'le procès-verbal, le rapport et l’annonce doivent être générés et validés avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées ou publiées.',
    },
  },
  { number: 3, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 4,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif de la liquidation et des documents déposés.',
      manque: 'la finalisation est refusée tant qu’une succursale de la société reste ouverte : fermez-la d’abord par le parcours « Fermeture de succursale ».',
      ensuite: 'cliquez sur « Finaliser » pour terminer le parcours, puis clôturez-le ; reste la radiation au registre du commerce.',
    },
  },
];

export const ETAPES_FERMETURE_SUCCURSALE: EtapeParcours[] = [
  {
    number: 1,
    label: 'Sélection',
    aide: {
      faire:
        'choisissez la société mère puis la succursale à fermer : ses données sont reprises de la base. Renseignez la date d’effet, le motif de la fermeture et la date de l’assemblée. La convocation est facultative.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » : le procès-verbal et l’annonce de fermeture seront produits à l’étape suivante.',
    },
  },
  {
    number: 2,
    label: 'Génération des actes',
    aide: {
      faire: 'générez puis validez le procès-verbal de fermeture et l’annonce légale de fermeture.',
      manque: 'le procès-verbal et l’annonce doivent être générés et validés avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées ou publiées.',
    },
  },
  { number: 3, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 4,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif de la fermeture et des documents déposés.',
      manque: 'si une donnée est erronée, revenez à l’étape concernée par la barre de progression.',
      ensuite: 'cliquez sur « Finaliser » : la succursale passe au statut « fermée » ; clôturez ensuite le parcours.',
    },
  },
];

const SUCCURSALE_ETAPES_SUIVANTES: EtapeParcours[] = [
  {
    number: 2,
    label: 'Succursale',
    aide: {
      faire:
        'renseignez l’enseigne, l’adresse, la ville, l’activité et la date d’ouverture de la succursale, la ville du greffe où elle sera immatriculée et, le cas échéant, la dotation et le responsable.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » : le procès-verbal de création et l’annonce d’ouverture seront produits à l’étape suivante.',
    },
  },
  {
    number: 3,
    label: 'Génération des actes',
    aide: {
      faire: 'générez puis validez le procès-verbal de création de la succursale et l’annonce légale d’ouverture.',
      manque: 'le procès-verbal et l’annonce doivent être générés et validés avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées ou publiées.',
    },
  },
  { number: 4, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 5,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif de la société mère, de la succursale et des documents.',
      manque: 'si une donnée est erronée, revenez à l’étape concernée par la barre de progression.',
      ensuite: 'cliquez sur « Finaliser » : la succursale est enregistrée et rattachée à sa société mère ; clôturez ensuite le parcours.',
    },
  },
];

export const ETAPES_SUCCURSALE_MA: EtapeParcours[] = [
  {
    number: 1,
    label: 'Société mère',
    aide: {
      faire:
        'choisissez la société mère (son identité est lue dans le dossier), puis la date et le type de l’assemblée qui décide l’ouverture. La convocation est facultative.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » pour saisir les données de la succursale.',
    },
  },
  ...SUCCURSALE_ETAPES_SUIVANTES,
];

export const ETAPES_SUCCURSALE_ETR: EtapeParcours[] = [
  {
    number: 1,
    label: 'Société mère',
    aide: {
      faire:
        'choisissez une société mère étrangère déjà enregistrée ou saisissez son identité, puis la date et le type de décision de son organe compétent, et cochez les contrôles de conformité. La convocation est facultative.',
      manque: 'tous les contrôles de conformité doivent être cochés ; les champs obligatoires sont marqués d’un astérisque (*).',
      ensuite: 'cliquez sur « Valider l’étape » pour saisir les données de la succursale.',
    },
  },
  ...SUCCURSALE_ETAPES_SUIVANTES,
];

export const ETAPES_PV_AGO: EtapeParcours[] = [
  {
    number: 1,
    label: 'Société et assemblée',
    aide: {
      faire:
        'choisissez la société, puis renseignez l’exercice approuvé et la séance (date, lieu, président, présence des associés). La convocation est facultative.',
      manque: MANQUE_DEFAUT,
      ensuite: 'cliquez sur « Valider l’étape » pour saisir les comptes et les décisions de l’assemblée.',
    },
  },
  {
    number: 2,
    label: 'Données du procès-verbal',
    aide: {
      faire:
        'renseignez le résultat de l’exercice, son affectation, les dividendes éventuels, le quitus de la gérance et les conventions réglementées.',
      manque: 'la somme des affectations doit être égale au résultat de l’exercice ; les champs en rouge sont à corriger.',
      ensuite: 'cliquez sur « Valider l’étape » : les documents de l’assemblée seront produits à l’étape suivante.',
    },
  },
  {
    number: 3,
    label: 'Génération des actes',
    aide: {
      faire: 'générez puis validez le procès-verbal d’approbation des comptes et, le cas échéant, le rapport de gestion.',
      manque: 'chaque document obligatoire doit être généré et validé avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées.',
    },
  },
  { number: 4, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 5,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif de l’assemblée et des documents.',
      manque: 'si une donnée est erronée, revenez à l’étape concernée par la barre de progression.',
      ensuite: 'cliquez sur « Finaliser » pour terminer le parcours, puis clôturez-le.',
    },
  },
];

export const ETAPES_MODIFICATION: EtapeParcours[] = [
  {
    number: 1,
    label: 'Sélection',
    aide: {
      faire:
        'choisissez la société, cochez les modifications décidées (une ou plusieurs), puis renseignez la date et la nature de l’assemblée. La convocation est facultative.',
      manque: 'au moins une modification doit être cochée ; la société et la date de l’assemblée sont obligatoires.',
      ensuite: 'cliquez sur « Valider l’étape » pour saisir les nouvelles valeurs de chaque modification.',
    },
  },
  {
    number: 2,
    label: 'Saisie',
    aide: {
      faire: 'pour chaque modification, saisissez la nouvelle valeur ; l’ancienne valeur, lue dans le dossier, est affichée à côté.',
      manque: 'chaque résolution doit être complète ; les champs en rouge sont à corriger.',
      ensuite: 'cliquez sur « Valider l’étape » : le procès-verbal, les statuts mis à jour et l’annonce seront produits à l’étape suivante.',
    },
  },
  {
    number: 3,
    label: 'Génération des actes',
    aide: {
      faire: 'générez puis validez le procès-verbal de modification, les statuts mis à jour et l’annonce légale.',
      manque: 'chaque document obligatoire doit être généré et validé avant de continuer.',
      ensuite: 'à l’étape suivante, vous pourrez déposer les versions signées ou publiées.',
    },
  },
  { number: 4, label: 'Pièces jointes', aide: PIECES_FACULTATIVES },
  {
    number: 5,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le tableau des changements (ancienne et nouvelle valeur) et les documents produits.',
      manque: 'si une donnée est erronée, revenez à l’étape concernée par la barre de progression.',
      ensuite: 'cliquez sur « Finaliser » : la fiche de la société est mise à jour ; clôturez ensuite le parcours.',
    },
  },
];

export const ETAPES_CREATION: EtapeParcours[] = [
  {
    number: 1,
    label: 'Dénomination',
    aide: {
      faire: 'saisissez la dénomination choisie et, si vous l’avez, le certificat négatif.',
      manque: MANQUE_DEFAUT,
      ensuite: 'validez l’étape pour renseigner le siège social.',
    },
  },
  {
    number: 2,
    label: 'Siège social',
    aide: {
      faire: 'renseignez l’adresse du siège, la ville du greffe et le mode d’occupation (bail ou domiciliation).',
      manque: MANQUE_DEFAUT,
      ensuite: 'validez l’étape pour renseigner le capital.',
    },
  },
  {
    number: 3,
    label: 'Capital',
    aide: {
      faire: 'renseignez le capital social, le nombre de parts, la valeur nominale et la nature des apports.',
      manque: MANQUE_DEFAUT,
      ensuite: 'validez l’étape pour renseigner l’activité.',
    },
  },
  {
    number: 4,
    label: 'Activité',
    aide: {
      faire: 'décrivez l’objet social et l’activité exercée.',
      manque: MANQUE_DEFAUT,
      ensuite: 'validez l’étape pour désigner les gérants.',
    },
  },
  {
    number: 5,
    label: 'Dirigeants',
    aide: {
      faire: 'ajoutez chaque dirigeant (gérant) avec sa pièce d’identité et indiquez s’il est aussi associé : il sera alors repris à l’étape suivante.',
      manque: MANQUE_DEFAUT,
      ensuite: 'validez l’étape pour répartir les parts entre les associés.',
    },
  },
  {
    number: 6,
    label: 'Associés',
    aide: {
      faire: 'ajoutez les associés et répartissez les parts ; les gérants associés sont repris de l’étape précédente.',
      manque: 'le total des parts réparties doit être égal au nombre de parts de l’étape « Capital ».',
      ensuite: 'validez l’étape pour produire les actes de la création.',
    },
  },
  {
    number: 7,
    label: 'Génération des actes',
    aide: {
      faire: 'générez, relisez puis validez chaque acte de la création (statuts, annonce légale, formulaires…).',
      manque: 'une donnée manquante est nommée au moment de la génération : complétez-la à l’étape indiquée.',
      ensuite: 'validez l’étape pour déposer les pièces du dossier.',
    },
  },
  {
    number: 8,
    label: 'Pièces jointes',
    aide: {
      faire: 'déposez les pièces demandées (pièces d’identité, justificatif du siège, attestation de blocage…).',
      manque: 'les pièces encore attendues sont signalées dans la liste.',
      ensuite: 'validez l’étape pour afficher la synthèse.',
    },
  },
  {
    number: 9,
    label: 'Synthèse',
    aide: {
      faire: 'vérifiez le récapitulatif, puis renseignez les identifiants obtenus après l’immatriculation (RC, ICE, IF…) dès que vous les recevez.',
      manque: 'les identifiants délivrés par les administrations peuvent être complétés plus tard.',
      ensuite: 'finalisez la création : la fiche de la société est créée et ses identifiants sont reportés au dossier.',
    },
  },
];

export const ETAPES_IMPORT: EtapeParcours[] = [
  ...ETAPES_CREATION.slice(0, 6).map((e) => ({
    ...e,
    aide: {
      ...e.aide,
      faire: `reprenez les données de la société existante : ${e.aide.faire}`,
    },
  })),
  {
    number: 7,
    label: 'Actes juridiques',
    aide: {
      faire: 'déposez les actes juridiques existants de la société (statuts, procès-verbaux, annonces…).',
      manque: 'les statuts en vigueur sont attendus ; les autres actes sont recommandés.',
      ensuite: 'validez l’étape pour déposer les autres pièces.',
    },
  },
  {
    number: 8,
    label: 'Pièces comptables et fiscales',
    aide: {
      faire: 'déposez, si vous les avez, les pièces comptables et fiscales : elles sont classées telles quelles dans l’espace « Dépôt client » de la Data Room.',
      manque: 'rien d’obligatoire : cette étape est facultative.',
      ensuite: 'validez l’étape pour contrôler la reprise.',
    },
  },
  {
    number: 9,
    label: 'Validation',
    aide: {
      faire: 'contrôlez la fiche reprise et les documents déposés.',
      manque: 'les données encore manquantes sont listées dans le récapitulatif.',
      ensuite: 'validez la reprise : la société rejoint vos dossiers comme une société créée par le cabinet.',
    },
  },
];

/** Etapes d'un parcours sans ecran dedie. */
export const AIDE_PARCOURS_GENERIQUE: AideEtapeTexte = {
  faire: 'ce parcours n’a pas encore d’écran dédié : saisissez les données de l’étape au format attendu par la plateforme.',
  manque: 'un contenu mal formé est refusé, avec le motif du refus.',
  ensuite: 'validez l’étape pour passer à la suivante ; la dernière étape termine le parcours.',
};

/** Libelles des etapes pour la barre de progression. */
export function libellesEtapes(etapes: EtapeParcours[]): Record<number, string> {
  return Object.fromEntries(etapes.map((e) => [e.number, e.label]));
}

export function etapesRoadmap(etapes: EtapeParcours[]): { number: number; label: string }[] {
  return etapes.map((e) => ({ number: e.number, label: e.label }));
}

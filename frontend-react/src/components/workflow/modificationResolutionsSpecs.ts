/**
 * Catalogue des types de resolutions du moteur PV directeur (lot L6) : types, specifications
 * et fonctions pures, separes des composants d'edition (modificationResolutions.tsx) pour que
 * ce dernier n'exporte que des composants (rafraichissement a chaud de Vite).
 *
 * Aligne 1:1 sur {@code ModificationDirecteurMapper.fillTypeVars}.
 */

export type FieldKind = 'text' | 'number' | 'date' | 'textarea' | 'select';

/** Forme pour laquelle un champ (ou une sous-liste) est demande : l'autre forme ne l'imprime pas. */
export type FormeCible = 'SARL' | 'SARL_AU';

export interface FieldSpec {
  key: string;
  label: string;
  kind: FieldKind;
  options?: { value: string; label: string }[];
  placeholder?: string;
  /** L3 (D6) : valeur d'usage PROPOSEE, transmise seulement une fois confirmee. */
  proposition?: string;
  /** L4d (E11c) : aide contextuelle (InfoBulle) : quoi saisir, comment le proces-verbal l'imprime. */
  aide?: string;
  /** L4d (E11c) : champ propre au proces-verbal d'une forme (SARL ou SARL AU). */
  seulement?: FormeCible;
}

export interface SubListSpec {
  key: string;
  label: string;
  columns: { key: string; label: string; kind: FieldKind }[];
  /** L4d (E11c) : sous-liste propre au proces-verbal d'une forme. */
  seulement?: FormeCible;
  /** L5 (E5) : aide contextuelle de la liste (InfoBulle). */
  aide?: string;
}

export interface TypeSpec {
  label: string;
  family: string;
  fields: FieldSpec[];
  sublist?: SubListSpec;
  /** true = disponible uniquement en SARL AU. */
  auOnly?: boolean;
  /** true = indisponible en SARL AU. */
  auExclu?: boolean;
  /**
   * L4d (E11c) : decision que le modele du cabinet ne sait pas encore rediger pour cette forme :
   * la generation du proces-verbal est refusee ; le texte l'explique avant la saisie.
   */
  nonCouvert?: Partial<Record<FormeCible, string>>;
  /**
   * L5 (E5) : texte d'aide de la decision pour une forme (TexteAide, masquable) : ce qu'il faut
   * saisir, ce qui est calcule, ce qui vient ensuite. `cle` : cle de memorisation du masquage.
   */
  note?: Partial<Record<FormeCible, { cle: string; texte: string }>>;
}

export const OUI_NON = [
  { value: 'non', label: 'Non' },
  { value: 'oui', label: 'Oui' },
];

export const FAM_IDENTITE = 'Identité et statuts';
export const FAM_CAPITAL = 'Capital';
export const FAM_PARTS = 'Parts sociales';
export const FAM_GERANCE = 'Gérance';
export const FAM_TRANSFO = 'Transformation et restructuration';
export const FAM_CONTROLE = 'Contrôle et conventions';
export const FAM_COMPTES = 'Comptes et résultat';
export const FAM_DIVERS = 'Formation et pouvoirs';

/** Le proces-verbal de l'associe unique ne cite pas d'article des statuts : champ propre au PV d'assemblee. */
const ARTICLES_AIDE =
  'Numéros des articles des statuts modifiés, tels qu’ils suivent « l’article » dans le procès-verbal (par exemple « 4 » ou « 6 et 7 »).';

/** L4d (E11c) : textes des decisions que le modele du cabinet ne sait pas encore rediger. */
const NON_COUVERT_MODELE =
  'Le modèle du cabinet ne permet pas encore de rédiger cette décision sans phrase inexacte : la génération du procès-verbal sera refusée. La correction du modèle est demandée au cabinet.';

/** L5 (E5) : champs du modele des decisions de l'associe unique (retenue a la source). */
const RETENUE_SOURCE: FieldSpec[] = [
  { key: 'retenueSourceTaux', label: 'Taux de la retenue à la source (%)', kind: 'text', seulement: 'SARL_AU',
    placeholder: 'ex. : 10',
    aide: 'Taux de droit commun applicable à la distribution, en pourcentage. Il n’est jamais prérempli : vérifiez le taux en vigueur. Le montant de la retenue est calculé (montant distribué × taux, au centime).' },
  { key: 'distributionExerciceOrigine', label: 'Exercice d’origine des sommes distribuées', kind: 'text', seulement: 'SARL_AU',
    placeholder: 'ex. : 2025',
    aide: 'Le texte suit « l’exercice d’origine des sommes distribuées étant l’exercice » : saisissez l’année de l’exercice.' },
  { key: 'retenueSourceExoneration', label: 'Associé unique exonéré de la retenue (société soumise à l’IS) ?', kind: 'select',
    options: OUI_NON, seulement: 'SARL_AU',
    aide: 'Oui seulement si l’associé unique est une société résidente soumise à l’impôt sur les sociétés (article 6-I-C-1° du CGI) : la décision rappelle l’exonération et l’attestation de propriété des titres à remettre. Pour une personne physique, la génération refuse « oui ».' },
];

const MODE_AUGMENTATION = {
  key: 'augcapMode',
  label: 'Réalisation',
  kind: 'select' as const,
  options: [{ value: 'création de parts nouvelles', label: 'Création de parts nouvelles' }],
  aide: 'L’augmentation par élévation du nominal n’est pas encore rédigée par le modèle du cabinet. Le nouveau capital, la numérotation et la valeur nominale des parts nouvelles sont calculés à partir du capital et des parts actuels.',
};

const SOUSCRIPTION_TIERS: FieldSpec = {
  key: 'souscriptionTiers',
  label: 'Une autre personne que l’associé unique souscrit-elle ?',
  kind: 'select',
  options: OUI_NON,
  seulement: 'SARL_AU',
  aide: 'Oui : la société perd son caractère unipersonnel à la réalisation de l’augmentation, et les statuts doivent être refondus en SARL. Non : l’associé unique souscrit seul.',
};

export const RES_SPECS: Record<string, TypeSpec> = {
  // ---- Identité & statuts ----
  transfert_siege: {
    label: 'Transfert du siège social', family: FAM_IDENTITE,
    fields: [
      { key: 'siegeMemePrefecture', label: 'Même préfecture / province ?', kind: 'select', options: OUI_NON },
      { key: 'nouveauSiege', label: 'Nouveau siège', kind: 'text' },
      { key: 'dateEffet', label: 'Date d’effet', kind: 'date' },
      { key: 'villeGreffeArrivee', label: 'Ville du greffe du nouveau siège (autre préfecture)', kind: 'text',
        aide: 'À renseigner si le siège change de préfecture ou de province : le procès-verbal cite le greffe de l’ancien siège (repris de la fiche de la société) et celui du nouveau siège.' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '4', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  modification_denomination: {
    label: 'Changement de dénomination', family: FAM_IDENTITE,
    fields: [
      { key: 'nouvelleDenomination', label: 'Nouvelle dénomination', kind: 'text' },
      { key: 'dateEffet', label: 'Date d’effet', kind: 'date' },
      { key: 'enseigneModifiee', label: 'Mandat à la gérance pour harmoniser l’enseigne et les papiers ?', kind: 'select', options: OUI_NON,
        aide: 'Oui : le procès-verbal donne mandat à la gérance de mettre en harmonie l’enseigne, le nom commercial, les papiers d’affaires et les comptes bancaires avec la nouvelle dénomination.' },
      { key: 'certificatNegatifNumero', label: 'N° du certificat négatif (facultatif)', kind: 'text',
        aide: 'Si le certificat négatif de l’OMPIC est déjà délivré, saisissez son numéro et sa date : le procès-verbal mentionne la vérification de la disponibilité de la dénomination. Sinon, laissez vide.' },
      { key: 'certificatNegatifDate', label: 'Date du certificat négatif', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '2', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  modification_objet: {
    label: 'Modification de l’objet social', family: FAM_IDENTITE,
    fields: [
      { key: 'objetAction', label: 'Action', kind: 'select', options: [
        { value: 'modification', label: 'Modification (nouvel objet)' },
        { value: 'extension', label: 'Extension (ajout d’activités)' },
      ] },
      { key: 'objetModification', label: 'Texte (nouvel objet / activités ajoutées)', kind: 'textarea',
        aide: 'Le texte suit « aux opérations suivantes : » (extension) ou « qui sera désormais le suivant : » (modification).' },
      { key: 'dateEffet', label: 'Date d’effet', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '3', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  modification_exercice: {
    label: 'Modification de la date de clôture de l’exercice', family: FAM_IDENTITE,
    fields: [
      { key: 'nouvelleDateCloture', label: 'Nouvelle date de clôture', kind: 'text', placeholder: '31 décembre' },
      { key: 'exerciceTransitoireDuree', label: 'Durée exercice transitoire (mois)', kind: 'number' },
      { key: 'exerciceTransitoireDebut', label: 'Début exercice transitoire', kind: 'date' },
      { key: 'exerciceTransitoireFin', label: 'Fin exercice transitoire', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '24', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  prorogation_duree: {
    label: 'Prorogation de la durée', family: FAM_IDENTITE,
    fields: [
      { key: 'dateExpirationInitiale', label: 'Date d’expiration initiale', kind: 'date' },
      { key: 'prorogationDuree', label: 'Prorogation (années)', kind: 'number' },
      { key: 'nouvelleDureeEcheance', label: 'Nouvelle échéance', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '5', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  mise_harmonie_statuts: {
    label: 'Mise en harmonie des statuts (loi 5-96)', family: FAM_IDENTITE,
    fields: [
      { key: 'refonteMotif', label: 'Motif de la refonte des statuts', kind: 'text', seulement: 'SARL_AU',
        proposition: 'la mise en harmonie des statuts avec la loi n° 5-96 telle que modifiée',
        aide: 'Le procès-verbal de l’associé unique décide « la refonte intégrale des statuts pour le motif suivant : … ».' },
    ],
  },
  modification_statuts_autre: {
    label: 'Autre modification statutaire', family: FAM_IDENTITE,
    fields: [
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', aide: ARTICLES_AIDE },
      { key: 'statutsNouveauLibelle', label: 'Nouveau libellé', kind: 'textarea' },
    ],
  },
  // ---- Capital ----
  augmentation_capital_numeraire: {
    label: 'Augmentation de capital en numéraire', family: FAM_CAPITAL,
    fields: [
      { key: 'augcapMontantChiffres', label: 'Montant de l’augmentation (MAD)', kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number',
        aide: 'Le nouveau capital doit être égal au capital actuel augmenté du montant : une saisie différente est refusée à la génération.' },
      MODE_AUGMENTATION,
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'augcapLiberationMode', label: 'Libération', kind: 'select', options: [
        { value: 'numéraire', label: 'En numéraire' },
        { value: 'compensation', label: 'Par compensation de créances' },
      ] },
      { key: 'primeEmission', label: 'Prime d’émission ?', kind: 'select', options: OUI_NON },
      { key: 'primeEmissionMontant', label: 'Prime par part (MAD)', kind: 'number' },
      { key: 'dpsSuppression', label: 'Suppression du DPS ?', kind: 'select', options: OUI_NON, seulement: 'SARL',
        aide: 'Oui : indiquez ci-dessous les bénéficiaires de la souscription réservée et leur nombre de parts.' },
      SOUSCRIPTION_TIERS,
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '6 et 7', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
    sublist: {
      key: 'beneficiairesDps', label: 'Bénéficiaires (si DPS supprimé)', seulement: 'SARL',
      columns: [
        { key: 'nom', label: 'Nom', kind: 'text' },
        { key: 'nbParts', label: 'Nb de parts', kind: 'number' },
      ],
    },
  },
  augmentation_capital_nature: {
    label: 'Augmentation de capital par apport en nature', family: FAM_CAPITAL,
    fields: [
      { key: 'commissaireApportsNom', label: 'Commissaire aux apports', kind: 'text' },
      { key: 'apporteurNom', label: "Apporteur", kind: 'text' },
      { key: 'apportNatureDescription', label: 'Description de l’apport', kind: 'textarea',
        aide: 'Le texte suit « par apport en nature, à concurrence de … dirhams, portant sur » : par exemple « un véhicule utilitaire immatriculé 12345-A-6 ».' },
      { key: 'augcapMontantChiffres', label: 'Montant de l’augmentation (MAD)', kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number',
        aide: 'Le nouveau capital doit être égal au capital actuel augmenté du montant : une saisie différente est refusée à la génération.' },
      MODE_AUGMENTATION,
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'fondsCommerce', label: 'Apport = fonds de commerce ?', kind: 'select', options: OUI_NON },
      { key: 'primeEmission', label: 'Prime d’émission ?', kind: 'select', options: OUI_NON },
      { key: 'primeEmissionMontant', label: 'Prime par part (MAD)', kind: 'number' },
      { key: 'dpsSuppression', label: 'Suppression du DPS ?', kind: 'select', options: OUI_NON, seulement: 'SARL',
        aide: 'Oui : sans autre bénéficiaire saisi, la souscription est réservée à l’apporteur pour toutes les parts nouvelles.' },
      SOUSCRIPTION_TIERS,
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '6 et 7', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  augmentation_capital_incorporation: {
    label: 'Augmentation de capital par incorporation de réserves', family: FAM_CAPITAL,
    nonCouvert: { SARL: NON_COUVERT_MODELE, SARL_AU: NON_COUVERT_MODELE },
    fields: [
      { key: 'augcapMontantChiffres', label: 'Montant de l’augmentation (MAD)', kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number' },
      { key: 'incorporationPoste', label: 'Poste incorporé', kind: 'text', placeholder: 'ex. : Report à nouveau' },
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'nouvelleValeurNominale', label: 'Nouvelle valeur nominale (si élévation)', kind: 'number' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '6 et 7', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  reduction_capital: {
    label: 'Réduction de capital', family: FAM_CAPITAL,
    fields: [
      { key: 'redcapMotif', label: 'Motif', kind: 'select', options: [
        { value: 'pertes', label: 'Apurement de pertes' },
        { value: 'non motivée par des pertes', label: 'Non motivée par des pertes' },
      ] },
      { key: 'redcapMontant', label: 'Montant de la réduction (MAD)', kind: 'number' },
      { key: 'redcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number',
        aide: 'Le nouveau capital doit être égal au capital actuel diminué du montant : une saisie différente est refusée à la génération.' },
      { key: 'redcapMode', label: 'Mode', kind: 'select', options: [
        { value: 'diminution du nominal', label: 'Diminution du nominal' },
        { value: 'réduction du nombre de parts', label: 'Réduction du nombre de parts' },
        { value: 'annulation de parts rachetées', label: 'Annulation de parts rachetées' },
      ], aide: 'Le nombre de parts annulées (ou la nouvelle valeur nominale) est calculé à partir du montant et de la valeur nominale actuelle. Une réduction non motivée par des pertes se rédige, dans le procès-verbal d’assemblée, par rachat et annulation de parts.' },
      { key: 'nouvelleValeurNominale', label: 'Nouvelle valeur nominale (si diminution)', kind: 'number' },
      { key: 'redcapNbPartsNouvelles', label: 'Nb de parts après la réduction (si annulation)', kind: 'number',
        aide: 'Repris dans les statuts mis à jour ; il doit correspondre au calcul (parts actuelles moins parts annulées), sinon la génération du procès-verbal est refusée.' },
      { key: 'rachatPrixPart', label: 'Prix de rachat par part (MAD)', kind: 'number',
        aide: 'Pour un rachat de parts : prix payé par part. Au-delà de la valeur nominale, indiquez sur quel poste la différence est imputée.' },
      { key: 'primeRachatImputation', label: 'Imputation de la différence de rachat', kind: 'text', placeholder: 'ex. : le poste « Réserves facultatives »' },
      { key: 'rachatDateLimite', label: 'Date limite de rachat', kind: 'date', seulement: 'SARL' },
      { key: 'reductionSuivieAugmentation', label: 'Réduction suivie d’une augmentation de capital ?', kind: 'select', options: OUI_NON, seulement: 'SARL_AU',
        aide: 'Oui : la réduction est indissociable de l’augmentation décidée par la décision suivante (« coup d’accordéon ») et ne sera réalisée qu’avec elle.' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '6 et 7', seulement: 'SARL', aide: ARTICLES_AIDE },
    ],
  },
  // ---- Parts sociales ----
  agrement_cession: {
    label: 'Agrément d’une cession de parts', family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'cessionNbParts', label: 'Nb de parts cédées', kind: 'number' },
      { key: 'cessionPartsDe', label: 'Parts numérotées de', kind: 'number' },
      { key: 'cessionPartsA', label: 'à', kind: 'number' },
      { key: 'cedantNom', label: 'Cédant', kind: 'text' },
      { key: 'cessionnaireNom', label: 'Cessionnaire', kind: 'text' },
      { key: 'cessionPrix', label: 'Prix global (MAD)', kind: 'number' },
      { key: 'cessionnaireTiers', label: 'Cessionnaire tiers (non associé) ?', kind: 'select', options: OUI_NON,
        aide: 'L’agrément ne vise qu’un cessionnaire tiers à la société : une cession entre associés n’y est pas soumise et sa génération est refusée.' },
    ],
  },
  agrement_transmission: {
    label: 'Agrément d’une transmission de parts', family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'cessionNbParts', label: 'Nb de parts transmises', kind: 'number' },
      { key: 'transmissionOrigine', label: 'Origine', kind: 'text', placeholder: 'ex. : la succession de M. Ahmed ALAOUI',
        aide: 'Le texte suit « parts sociales dépendant de » : saisissez-le avec son article (« la succession de… », « la donation consentie par… »).' },
      { key: 'cessionnaireNom', label: 'Attributaire', kind: 'text' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', proposition: '6 et 7', aide: ARTICLES_AIDE },
    ],
  },
  nantissement_parts: {
    label: 'Consentement à un nantissement de parts', family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'nantissementNbParts', label: 'Nb de parts nanties', kind: 'number' },
      { key: 'nantissementConstituantNom', label: 'Constituant (associé)', kind: 'text' },
      { key: 'nantissementCreancierNom', label: 'Créancier nanti', kind: 'text' },
      { key: 'nantissementCreanceObjet', label: 'Créance garantie', kind: 'text', placeholder: 'ex. : la créance née du prêt du 2 mai 2026',
        aide: 'Le texte suit « en garantie de » : saisissez-le avec son article (« la créance née du prêt… »).' },
    ],
  },
  cession_parts_pluripersonnelle: {
    label: 'Cession de parts — passage en SARL pluripersonnelle', family: FAM_PARTS, auOnly: true,
    fields: [
      { key: 'cessionDate', label: 'Date de la cession', kind: 'date' },
      { key: 'cessionNbParts', label: 'Nb de parts cédées', kind: 'number' },
      { key: 'cessionPartsDe', label: 'Parts numérotées de', kind: 'number',
        aide: 'Le bloc cédé commence à la première part ou finit à la dernière : la nouvelle répartition du capital en est déduite.' },
      { key: 'cessionPartsA', label: 'à', kind: 'number' },
      { key: 'cessionnaireNom', label: 'Cessionnaire', kind: 'text' },
      { key: 'cessionPrix', label: 'Prix global (MAD)', kind: 'number',
        aide: 'Le prix par part est calculé à partir du prix global et du nombre de parts cédées.' },
    ],
  },
  // ---- Gérance ----
  nomination_gerant: {
    label: 'Nomination d’un gérant', family: FAM_GERANCE,
    fields: [
      { key: 'gerantSortant', label: 'Gérant sortant ?', kind: 'select', options: OUI_NON },
      { key: 'gerantSortantNom', label: 'Nom du gérant sortant', kind: 'text' },
      { key: 'gerantSortantMotif', label: 'Motif de cessation', kind: 'text', placeholder: 'ex. : sa démission' },
      { key: 'gerantSortantDateEffet', label: 'Date d’effet cessation', kind: 'date' },
      { key: 'dureeGerance', label: 'Durée du mandat', kind: 'text', placeholder: 'ex. : trois ans',
        aide: 'Le texte suit « pour une durée de » : par exemple « trois ans ». Le modèle du cabinet ne sait pas écrire une durée illimitée.' },
    ],
    sublist: {
      key: 'gerants', label: 'Gérant(s) nommé(s)',
      columns: [
        { key: 'civilite', label: 'Civilité', kind: 'text' },
        { key: 'prenom', label: 'Prénom', kind: 'text' },
        { key: 'nom', label: 'Nom', kind: 'text' },
        { key: 'nationalite', label: 'Nationalité', kind: 'text' },
        { key: 'dateNaissance', label: 'Né(e) le', kind: 'date' },
        { key: 'adresse', label: 'Adresse', kind: 'text' },
        { key: 'pieceType', label: 'Pièce', kind: 'text' },
        { key: 'pieceNumero', label: 'N° pièce', kind: 'text' },
      ],
    },
  },
  renouvellement_gerant: {
    label: 'Renouvellement du mandat de gérant', family: FAM_GERANCE,
    fields: [
      { key: 'gerantNom', label: 'Gérant', kind: 'text' },
      { key: 'dureeGerance', label: 'Durée du mandat', kind: 'text', placeholder: 'ex. : trois ans',
        aide: 'Le texte suit « pour une durée de » : par exemple « trois ans ». Le modèle du cabinet ne sait pas écrire une durée illimitée.' },
    ],
  },
  revocation_gerant: {
    label: 'Révocation d’un gérant', family: FAM_GERANCE,
    fields: [
      { key: 'gerantSortantNom', label: 'Gérant révoqué', kind: 'text' },
      { key: 'gerantSortantDateEffet', label: 'Date d’effet de la révocation', kind: 'date', seulement: 'SARL_AU' },
      { key: 'gerantSortantMotif', label: 'Motifs de la révocation (facultatif)', kind: 'textarea', seulement: 'SARL_AU',
        aide: 'Facultatif : saisis, les motifs sont repris dans la décision (« La révocation est fondée sur les motifs suivants : … »).' },
    ],
  },
  remuneration_gerant: {
    label: 'Rémunération de la gérance', family: FAM_GERANCE,
    fields: [
      { key: 'gerantNom', label: 'Gérant', kind: 'text' },
      { key: 'remunerationPeriodicite', label: 'Périodicité', kind: 'text', proposition: 'mensuelle' },
      { key: 'remunerationMontant', label: 'Montant (MAD)', kind: 'number' },
      { key: 'remunerationDateEffet', label: 'Date d’effet', kind: 'date' },
    ],
  },
  autorisation_gerance: {
    label: 'Autorisation donnée à la gérance', family: FAM_GERANCE,
    fields: [{ key: 'autorisationObjet', label: 'Objet de l’autorisation', kind: 'textarea',
      placeholder: 'caution, aval, garantie, location-gérance…',
      aide: 'Le texte suit « autorise la gérance à » : commencez par un verbe (« consentir une caution au profit de… »).' }],
  },
  // ---- Transformation & restructuration ----
  transformation: {
    label: 'Transformation de la société', family: FAM_TRANSFO,
    fields: [
      { key: 'commissaireTransformationExiste', label: 'Commissaire à la transformation ?', kind: 'select', options: OUI_NON },
      { key: 'commissaireTransformationNom', label: 'Commissaire à la transformation', kind: 'text' },
      { key: 'transformationForme', label: 'Forme nouvelle', kind: 'text', placeholder: 'ex. : société anonyme' },
      { key: 'transformationOrganes', label: 'Organes de la forme nouvelle', kind: 'textarea' },
    ],
  },
  operation_restructuration: {
    label: 'Opération de restructuration (fusion / scission…)', family: FAM_TRANSFO, auExclu: true,
    fields: [
      { key: 'restructurationNature', label: 'Nature', kind: 'select', options: [
        { value: 'fusion', label: 'Fusion' },
        { value: 'scission', label: 'Scission' },
        { value: "apport partiel d'actif", label: 'Apport partiel d’actif' },
      ] },
      { key: 'restructurationModalites', label: 'Modalités', kind: 'textarea' },
    ],
  },
  capitaux_propres_art86: {
    label: 'Capitaux propres < ¼ du capital (art. 86) — poursuite', family: FAM_TRANSFO,
    nonCouvert: { SARL: NON_COUVERT_MODELE },
    fields: [
      { key: 'exerciceClosDate', label: 'Date de clôture de l’exercice', kind: 'date' },
      { key: 'approbationComptesDate', label: 'Date d’approbation des comptes', kind: 'date', seulement: 'SARL_AU',
        aide: 'La décision est prise dans les trois mois qui suivent l’approbation des comptes : une date hors de ce délai est refusée.' },
      { key: 'situationNetteMontant', label: 'Situation nette (MAD)', kind: 'number', seulement: 'SARL_AU' },
    ],
  },
  designation_commissaire_transformation: {
    label: 'Désignation du commissaire à la transformation', family: FAM_TRANSFO, auExclu: true,
    fields: [{ key: 'commissaireTransformationNom', label: 'Commissaire à la transformation', kind: 'text' }],
  },
  // ---- Contrôle & conventions ----
  commissaire_comptes: {
    label: 'Commissaire aux comptes (nomination / renouvellement / fin)', family: FAM_CONTROLE,
    fields: [
      { key: 'cacAction', label: 'Action', kind: 'select', options: [
        { value: 'nomination', label: 'Nomination' },
        { value: 'renouvellement', label: 'Renouvellement' },
        { value: 'fin de mandat', label: 'Fin de mandat' },
      ] },
      { key: 'commissaireComptesNom', label: 'Commissaire aux comptes', kind: 'text' },
      { key: 'dureeMandatCac', label: 'Durée du mandat (exercices)', kind: 'text', proposition: 'trois' },
      { key: 'cacFinMandatDate', label: 'Exercice clos (fin de mandat)', kind: 'date' },
      { key: 'commissaireComptesAdresse', label: 'Adresse ou siège du commissaire (nomination)', kind: 'text', seulement: 'SARL_AU' },
      { key: 'cacHonoraires', label: 'Honoraires annuels (MAD, facultatif)', kind: 'number', seulement: 'SARL_AU',
        aide: 'Facultatif : saisis, les honoraires sont fixés dans la décision de nomination ou de renouvellement.' },
      { key: 'cacCessationDateEffet', label: 'Date d’effet de la cessation (fin de mandat)', kind: 'date', seulement: 'SARL_AU' },
      { key: 'cacCessationMotif', label: 'Motif de la cessation (fin de mandat)', kind: 'text', seulement: 'SARL_AU',
        placeholder: 'ex. : la démission du commissaire' },
    ],
  },
  conventions_reglementees: {
    label: 'Approbation des conventions réglementées', family: FAM_CONTROLE, fields: [],
    sublist: {
      key: 'conventions', label: 'Conventions approuvées', seulement: 'SARL_AU',
      columns: [
        { key: 'date', label: 'Date', kind: 'date' },
        { key: 'partieInteressee', label: 'Partie intéressée', kind: 'text' },
        { key: 'objet', label: 'Objet', kind: 'text' },
      ],
    },
  },
  // ---- Comptes & résultat ----
  approbation_comptes: {
    label: 'Approbation des comptes', family: FAM_COMPTES,
    fields: [
      { key: 'exerciceClosDate', label: 'Date de clôture de l’exercice', kind: 'date' },
      { key: 'resultatSens', label: 'Résultat', kind: 'select', options: [
        { value: 'bénéfice', label: 'Bénéfice' },
        { value: 'perte', label: 'Perte' },
      ] },
      { key: 'resultatMontant', label: 'Montant du résultat (MAD)', kind: 'number' },
    ],
  },
  affectation_resultat: {
    label: 'Affectation du résultat', family: FAM_COMPTES,
    note: { SARL_AU: { cle: 'pv-modification-au-affectation', texte:
      'Indiquez le sens et le montant du résultat. Un bénéfice : précisez si une dotation à la réserve légale est due (quotité, plafond et montant, tels que vous les avez arrêtés), puis répartissez le reste dans la liste des affectations ; le total doit être égal au bénéfice, sinon la génération est refusée. Une perte : elle est affectée en totalité au report à nouveau ; indiquez seulement si la situation nette est devenue inférieure au quart du capital. Les montants en lettres sont calculés. Ensuite : générez le procès-verbal à l’étape suivante.' } },
    fields: [
      { key: 'exerciceClosDate', label: 'Date de clôture de l’exercice', kind: 'date' },
      { key: 'resultatSens', label: 'Résultat', kind: 'select', seulement: 'SARL_AU', options: [
        { value: 'bénéfice', label: 'Bénéfice' },
        { value: 'perte', label: 'Perte' },
      ], aide: 'Bénéfice : la décision affecte le bénéfice net selon la liste ci-dessous. Perte : la décision l’affecte au report à nouveau, sans distribution.' },
      { key: 'resultatMontant', label: 'Montant du résultat (MAD)', kind: 'number' },
      { key: 'reserveLegaleApplicable', label: 'Dotation à la réserve légale due ? (bénéfice)', kind: 'select', options: OUI_NON,
        seulement: 'SARL_AU',
        aide: 'Oui : la décision dote la réserve légale avant les autres affectations. Ne la répétez pas dans la liste des affectations.' },
      { key: 'reserveLegaleDotationTaux', label: 'Quotité de la dotation (réserve légale)', kind: 'text', seulement: 'SARL_AU',
        placeholder: 'ex. : 5 %',
        aide: 'Le texte suit « à hauteur de … du bénéfice net de l’exercice ». La loi n° 5-96 ne fixe pas la réserve légale en SARL : saisissez la quotité retenue par les statuts ou le dossier.' },
      { key: 'reserveLegalePlafond', label: 'Plafond de la réserve légale', kind: 'text', seulement: 'SARL_AU',
        placeholder: 'ex. : le dixième du capital social',
        aide: 'Le texte suit « le prélèvement cessant d’être obligatoire lorsque la réserve atteint » : saisissez le plafond retenu par les statuts.' },
      { key: 'reserveLegaleDotationMontant', label: 'Montant de la dotation (MAD)', kind: 'number', seulement: 'SARL_AU' },
      { key: 'capitauxPropresSousQuart', label: 'Situation nette inférieure au quart du capital ? (perte)', kind: 'select',
        options: OUI_NON, seulement: 'SARL_AU',
        aide: 'Pour une perte seulement. Oui : la décision rappelle l’obligation de statuer, dans les trois mois de l’approbation des comptes, sur la dissolution anticipée (article 86 de la loi n° 5-96).' },
    ],
    sublist: {
      key: 'affectations', label: 'Affectations',
      aide: 'Une ligne par poste (« Dividendes », « Autres réserves », « Report à nouveau »…). Pour l’associé unique, la réserve légale se saisit dans ses propres champs ; laissez la liste vide pour une perte.',
      columns: [
        { key: 'poste', label: 'Poste', kind: 'text' },
        { key: 'montant', label: 'Montant (MAD)', kind: 'number' },
      ],
    },
  },
  distribution_dividendes: {
    label: 'Distribution de dividendes', family: FAM_COMPTES,
    note: { SARL_AU: { cle: 'pv-modification-au-dividendes', texte:
      'Saisissez le bénéfice distribuable arrêté au vu des comptes approuvés, le montant distribué, la date de mise en paiement et la retenue à la source. Le montant par part, la date limite légale de mise en paiement (neuf mois après la clôture) et le montant de la retenue sont calculés à la génération ; un montant supérieur au bénéfice distribuable ou une date de paiement au-delà du délai est refusé. Ensuite : générez le procès-verbal à l’étape suivante.' } },
    fields: [
      { key: 'exerciceClosDate', label: 'Date de clôture de l’exercice', kind: 'date', seulement: 'SARL_AU' },
      { key: 'approbationComptesDate', label: 'Date d’approbation des comptes', kind: 'date', seulement: 'SARL_AU' },
      { key: 'beneficeDistribuable', label: 'Bénéfice distribuable (MAD)', kind: 'number', seulement: 'SARL_AU',
        aide: 'Bénéfice distribuable arrêté au vu des états de synthèse approuvés : le montant distribué ne peut pas le dépasser.' },
      { key: 'dividendeTotal', label: 'Dividende global (MAD)', kind: 'number' },
      { key: 'dividendeParPart', label: 'Dividende par part (MAD)', kind: 'number', seulement: 'SARL' },
      { key: 'dateMisePaiement', label: 'Date de mise en paiement', kind: 'date',
        aide: 'Au plus tard neuf mois après la clôture de l’exercice (article 83 bis de la loi n° 5-96), sauf prolongation par ordonnance.' },
      { key: 'prolongationOrdonnance', label: 'Délai prolongé par ordonnance du président du tribunal ?', kind: 'select',
        options: OUI_NON, seulement: 'SARL_AU',
        aide: 'Oui : saisissez la date de l’ordonnance et la nouvelle date limite ; le tribunal de commerce est celui du registre de la société.' },
      { key: 'prolongationOrdonnanceDate', label: 'Date de l’ordonnance (si prolongation)', kind: 'date', seulement: 'SARL_AU' },
      { key: 'prolongationDateNouvelle', label: 'Nouvelle date limite (si prolongation)', kind: 'date', seulement: 'SARL_AU' },
      ...RETENUE_SOURCE,
    ],
  },
  distribution_reserves: {
    label: 'Distribution de réserves', family: FAM_COMPTES,
    note: { SARL_AU: { cle: 'pv-modification-au-reserves', texte:
      'Listez chaque poste de réserves disponibles prélevé, avec son solde avant le prélèvement. Le total distribué, le solde après prélèvement, le montant par part et la retenue à la source sont calculés à la génération. La réserve légale, indisponible, ne peut pas être distribuée. Ensuite : générez le procès-verbal à l’étape suivante.' } },
    fields: [
      { key: 'prelevementMontant', label: 'Montant prélevé (MAD)', kind: 'number', seulement: 'SARL' },
      { key: 'prelevementPoste', label: 'Poste de réserve', kind: 'text', seulement: 'SARL' },
      { key: 'dividendeParPart', label: 'Par part (MAD)', kind: 'number', seulement: 'SARL' },
      { key: 'dateMisePaiement', label: 'Date de mise en paiement', kind: 'date' },
      ...RETENUE_SOURCE,
    ],
    sublist: {
      key: 'reservesPrelevees', label: 'Postes de réserves prélevés', seulement: 'SARL_AU',
      aide: 'Une ligne par poste : intitulé exact (« Réserves facultatives »…), montant prélevé et solde du poste avant le prélèvement. Le solde après est calculé ; un prélèvement supérieur au solde est refusé.',
      columns: [
        { key: 'libelle', label: 'Intitulé du poste', kind: 'text' },
        { key: 'prelevement', label: 'Prélèvement (MAD)', kind: 'number' },
        { key: 'soldeAvant', label: 'Solde avant prélèvement (MAD)', kind: 'number' },
      ],
    },
  },
  acompte_dividendes: {
    label: 'Acompte sur dividendes', family: FAM_COMPTES,
    nonCouvert: { SARL_AU: 'Le modèle des décisions de l’associé unique ne prévoit pas d’acompte sur dividendes : la génération du procès-verbal sera refusée.' },
    fields: [
      { key: 'acompteMontant', label: 'Acompte global (MAD)', kind: 'number' },
      { key: 'acompteParPart', label: 'Acompte par part (MAD)', kind: 'number' },
    ],
  },
  // ---- Formation & pouvoirs ----
  ratification_actes_formation: {
    label: 'Ratification des actes de la période de formation', family: FAM_DIVERS,
    fields: [{ key: 'engagementsMandat', label: 'État des engagements', kind: 'textarea' }],
  },
  pouvoirs_formalites: {
    label: 'Pouvoirs pour les formalités', family: FAM_DIVERS,
    fields: [
      { key: 'pouvoirMandataireNom', label: 'Mandataire désigné (facultatif)', kind: 'text', seulement: 'SARL_AU',
        aide: 'Facultatif : saisi, le mandataire reçoit les pouvoirs en plus du porteur d’un original du procès-verbal.' },
    ],
  },
};

/** Ordre d'affichage des familles dans le sélecteur de type. */
export const FAMILY_ORDER = [
  FAM_IDENTITE, FAM_CAPITAL, FAM_PARTS, FAM_GERANCE,
  FAM_TRANSFO, FAM_CONTROLE, FAM_COMPTES, FAM_DIVERS,
];

// --------------------------------------------------------------------------
// État d'une résolution + helpers
// --------------------------------------------------------------------------
export interface ResolutionState {
  /** Identifiant stable (id de la décision officielle seedée, ou généré). */
  id: string;
  /** Type de résolution du moteur directeur (snake_case). */
  type: string;
  objet: string;
  values: Record<string, string>;
  rows: Record<string, Array<Record<string, string>>>;
  /** Nouvel associé saisi via OCR CIN (décisions d'entrée au capital). */
  newAssocie?: Record<string, string>;
}

/** Crée une résolution vierge d'un type donné. */
export function newResolution(type: string, id: string, objet = ''): ResolutionState {
  return { id, type, objet, values: {}, rows: {} };
}

/**
 * Réconcilie l'état des résolutions avec la sélection courante : conserve les
 * valeurs déjà saisies pour les décisions maintenues, ajoute les nouvelles
 * (une résolution par décision), retire celles dé-sélectionnées, en préservant
 * l'ordre de la sélection. Pure / testable.
 */
export function seedResolutions(
  prev: ResolutionState[],
  decisions: { id: string; resolutionType: string; label: string }[],
): ResolutionState[] {
  const byId = new Map(prev.map((r) => [r.id, r]));
  return decisions.map((d) => {
    const existing = byId.get(d.id);
    if (existing && existing.type === d.resolutionType) return existing;
    return newResolution(d.resolutionType, d.id, d.label);
  });
}

/** Aplati une résolution en payload attendu par ModificationDirecteurMapper. */
export function flattenResolution(r: ResolutionState): Record<string, unknown> {
  const out: Record<string, unknown> = { type: r.type };
  if (r.objet.trim()) out.objet = r.objet.trim();
  const spec = RES_SPECS[r.type];
  for (const f of spec?.fields ?? []) {
    const v = r.values[f.key];
    if (v != null && v !== '') out[f.key] = v;
  }
  if (spec?.sublist) {
    const rows = (r.rows[spec.sublist.key] ?? []).filter((row) =>
      Object.values(row).some((v) => (v ?? '').toString().trim() !== ''),
    );
    if (rows.length) out[spec.sublist.key] = rows;
  }
  if (r.newAssocie && Object.values(r.newAssocie).some((v) => (v ?? '').trim() !== '')) {
    out.nouvelAssocie = r.newAssocie;
  }
  return out;
}

/** Types disponibles pour une forme (groupés par famille). */
export function availableTypesFor(isAU: boolean): Record<string, { value: string; label: string }[]> {
  const byFamily: Record<string, { value: string; label: string }[]> = {};
  for (const [value, spec] of Object.entries(RES_SPECS)) {
    if (isAU && spec.auExclu) continue;
    if (!isAU && spec.auOnly) continue;
    (byFamily[spec.family] ??= []).push({ value, label: spec.label });
  }
  return byFamily;
}

/** Une paire ancienne-valeur (lecture seule) affichée à côté des nouveaux champs. */
export interface OldValue {
  label: string;
  value: string;
}

function fmt(v: unknown): string {
  if (v === null || v === undefined || `${v}`.trim() === '') return '—';
  return `${v}`;
}
function fmtMad(v: unknown): string {
  if (v === null || v === undefined || `${v}`.trim() === '') return '—';
  const n = Number(v);
  return Number.isFinite(n) ? `${n.toLocaleString('fr-FR')} MAD` : `${v}`;
}

/**
 * Anciennes valeurs pertinentes pour un `resolutionType`, à afficher en lecture
 * seule à côté des nouveaux champs. Source : fiche EFFECTIVE (BD ⊕ overrides).
 */
export function oldValuesFor(
  resolutionType: string,
  fiche: Record<string, unknown> | undefined,
): OldValue[] {
  const f = fiche ?? {};
  switch (resolutionType) {
    case 'modification_denomination':
      return [{ label: 'Dénomination actuelle', value: fmt(f.denomination) }];
    case 'modification_objet':
      return [{ label: 'Objet social actuel', value: fmt(f.objetSocial) }];
    case 'transfert_siege':
      return [{ label: 'Siège actuel', value: fmt(f.adresseSiege) }];
    case 'prorogation_duree':
      return [{ label: 'Durée actuelle (années)', value: fmt(f.dureeAnnees) }];
    case 'modification_exercice':
      return [{ label: 'Clôture actuelle', value: fmt(f.dateClotureExercice) }];
    case 'augmentation_capital_numeraire':
    case 'augmentation_capital_nature':
    case 'augmentation_capital_incorporation':
    case 'reduction_capital':
      return [
        { label: 'Capital actuel', value: fmtMad(f.capitalSocial) },
        { label: 'Valeur nominale actuelle', value: fmtMad(f.valeurNominale) },
        { label: 'Nombre de parts actuel', value: fmt(f.nombreParts) },
      ];
    case 'agrement_cession':
    case 'agrement_transmission':
    case 'nantissement_parts':
    case 'cession_parts_pluripersonnelle':
      return [
        { label: 'Capital actuel', value: fmtMad(f.capitalSocial) },
        { label: 'Nombre de parts actuel', value: fmt(f.nombreParts) },
      ];
    default:
      return [];
  }
}

// --------------------------------------------------------------------------
// P3 (2026-08-10) — associés & gérants EXISTANTS = sélection dans une liste BD,
// JAMAIS de re-saisie d'identité. Seul un NOUVEL associé entrant passe par l'OCR.
// --------------------------------------------------------------------------

/** Listes de noms existants (fiche BD) pour peupler les dropdowns personne. */
export interface PersonLists {
  associe: string[];
  gerant: string[];
}

/** Champs de résolution qui référencent une personne EXISTANTE (→ dropdown BD). */
export const PERSON_FIELD_SOURCE: Record<string, keyof PersonLists> = {
  gerantNom: 'gerant',
  gerantSortantNom: 'gerant',
  cedantNom: 'associe',
  nantissementConstituantNom: 'associe',
};

/** Extrait les noms lisibles d'une liste d'associés / gérants de la fiche BD. */
export function personNames(list: unknown): string[] {
  if (!Array.isArray(list)) return [];
  const out: string[] = [];
  for (const p of list) {
    if (!p || typeof p !== 'object') continue;
    const o = p as Record<string, unknown>;
    const full = [o.prenom, o.nom]
      .filter((x) => x != null && String(x).trim() !== '')
      .map(String)
      .join(' ')
      .trim();
    const name =
      full || (o.denomination ? String(o.denomination) : '') || (o.nom ? String(o.nom) : '');
    if (name && !out.includes(name)) out.push(name);
  }
  return out;
}

/* eslint-disable */
/**
 * FICHIER GENERE — ne pas editer a la main.
 *   node scripts/lotB/derive-champs-creation.mjs
 *
 * Le catalogue des documents du parcours de creation et des champs que
 * chacun reclame. La regle qu'il sert, posee par l'utilisateur :
 * **un champ n'apparait que si le document qui le consomme est retenu.**
 *
 * Un champ servant PLUSIEURS documents est declare une seule fois, avec la
 * liste des documents qui le reclament, et ne s affiche qu une fois — sous le
 * premier document retenu qui en a besoin.
 *
 * Aucun champ n est marque obligatoire : le refus de generer sur un blanc au
 * milieu d une phrase est prononce par le SERVEUR, sur le document rendu
 * (ControleCompletude). Voir l en-tete du script pour le detail.
 */

export type ChampType = 'text' | 'date' | 'select' | 'textarea' | 'number';

export interface RepriseDocument {
  /** Ligne du parcours ou le document reparait. */
  ligne: number;
  statut: string;
  /** Emploi nomme par le parcours (bordereau : recapitulatif / remise / restitution). */
  emploi: string | null;
  libelle: string;
}

export interface DocumentParcours {
  /** Code du modele au manifeste. */
  code: string;
  /** Ligne du parcours qui le GENERE, ou null (hors parcours). */
  ligneGeneration: number | null;
  statutGeneration: string;
  libelle: string;
  /** Condition d'application, telle qu'elle figure au parcours. */
  condition: string;
  /** Statuts et Annonce legale seulement — decision du cabinet. */
  cocheParDefaut: boolean;
  emploi: string | null;
  /**
   * Les autres lignes du parcours ou le MEME document reparait : son depot
   * aupres de l administration, sa mise a jour, ou un autre de ses emplois.
   * Ce ne sont pas des documents distincts — un seul fichier, plusieurs temps.
   */
  reprises: RepriseDocument[];
  note?: string;
}

export interface ChoixDocument {
  ligne: number;
  libelle: string;
  documentProduit: string;
  condition: string;
  cocheParDefaut: boolean;
  /** Une ou deux variantes : bail / domiciliation, SARL / SARL AU. */
  codes: string[];
}

export interface ChampCreation {
  /** Variable du corpus, sans le $. */
  variable: string;
  /** Cle envoyee au backend sous `payload.creation`. */
  cle: string;
  label: string;
  type: ChampType;
  /** Valeurs admises, reprises MOT POUR MOT des cases du modele. */
  options: string[] | null;
  /** La ligne du dictionnaire qui decrit la variable, verbatim. */
  aide: string | null;
  section: string | null;
  /** Codes des documents qui consomment ce champ. */
  documents: string[];
  /** Nom de la boucle du modele, si le champ se repete. */
  boucle: string | null;
  /**
   * Saisie HERITEE du lot 5 : le lot A la classe « deja resolue » — et elle
   * l'est, le mapper sait la lire — mais sa SOURCE est une saisie. Sans
   * champ, plus personne ne la renseigne.
   */
  heritee?: boolean;
}

export interface BoucleCreation {
  nom: string;
  label: string;
  documents: string[];
  /** Cles des champs de la boucle, dans l ordre du catalogue. */
  champs: string[];
}

export const DOCUMENTS_PARCOURS: DocumentParcours[] = [
  {
    "code": "FICHE_RENSEIGNEMENTS_CREATION",
    "ligneGeneration": 1,
    "statutGeneration": "Création du ticket et collecte d'information",
    "libelle": "Fiche de renseignements et accusé d'ouverture de dossier",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "CONTRAT_BAIL",
    "ligneGeneration": 2,
    "statutGeneration": "Génération des documents",
    "libelle": "Contrat de bail commercial ou contrat de domiciliation",
    "condition": "Selon la voie retenue pour le siège",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "CONTRAT_DOMICILIATION",
    "ligneGeneration": 2,
    "statutGeneration": "Génération des documents",
    "libelle": "Contrat de bail commercial ou contrat de domiciliation",
    "condition": "Selon la voie retenue pour le siège",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "STATUTS_SARL",
    "ligneGeneration": 3,
    "statutGeneration": "Génération des documents",
    "libelle": "Statuts de la société",
    "condition": "Tous dossiers — SARL ou SARL AU selon $ASSOCIE_UNIQUE. Contrôles bloquants au lancement de la génération : certificat négatif obtenu ; pièces d'identité et de capacité des associés et des gérants réunies ; rapport du commissaire aux apports obtenu lorsque les apports en nature y sont soumis.",
    "cocheParDefaut": true,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "STATUTS_SARL_AU",
    "ligneGeneration": 3,
    "statutGeneration": "Génération des documents",
    "libelle": "Statuts de la société",
    "condition": "Tous dossiers — SARL ou SARL AU selon $ASSOCIE_UNIQUE. Contrôles bloquants au lancement de la génération : certificat négatif obtenu ; pièces d'identité et de capacité des associés et des gérants réunies ; rapport du commissaire aux apports obtenu lorsque les apports en nature y sont soumis.",
    "cocheParDefaut": true,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "ETAT_ACTES_SOCIETE_EN_FORMATION",
    "ligneGeneration": 4,
    "statutGeneration": "Génération des documents",
    "libelle": "État des actes accomplis, annexé aux statuts",
    "condition": "Si des engagements ont été pris avant l'immatriculation — annexe des statuts",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "ACTE_NOMINATION_GERANT",
    "ligneGeneration": 5,
    "statutGeneration": "Génération des documents",
    "libelle": "Acte de nomination du gérant",
    "condition": "Si la gérance n'est pas désignée dans les statuts",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "ATTESTATION_SOUSCRIPTION_LIBERATION",
    "ligneGeneration": 6,
    "statutGeneration": "Génération des documents",
    "libelle": "Déclaration de souscription et de versement",
    "condition": "Tous dossiers — pièce remise à la banque à l'appui du dépôt du capital",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": [
      {
        "ligne": 17,
        "statut": "Déroulement de la démarche",
        "emploi": null,
        "libelle": "Déclaration de souscription et de versement remise à la banque"
      }
    ]
  },
  {
    "code": "POUVOIR_FORMALITES_CREATION",
    "ligneGeneration": 7,
    "statutGeneration": "Génération des documents",
    "libelle": "Pouvoir donné au mandataire chargé des formalités",
    "condition": "Tous dossiers où le cabinet dépose au nom du client",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "DEMANDE_TAXE_PROFESSIONNELLE",
    "ligneGeneration": 8,
    "statutGeneration": "Génération des documents",
    "libelle": "Demande d'inscription à la taxe professionnelle (formulaire DGI AAC050B)",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": [
      {
        "ligne": 23,
        "statut": "Déroulement de la démarche",
        "emploi": null,
        "libelle": "Demande d'inscription à la taxe professionnelle"
      }
    ]
  },
  {
    "code": "DECLARATION_IMMATRICULATION_RC",
    "ligneGeneration": 9,
    "statutGeneration": "Génération des documents",
    "libelle": "Déclaration d'immatriculation au RC (modèle 2), en-tête de tribunal conditionnel",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": [
      {
        "ligne": 27,
        "statut": "Déroulement de la démarche",
        "emploi": null,
        "libelle": "Déclaration d'immatriculation au RC (modèle 2)"
      }
    ]
  },
  {
    "code": "DECLARATION_EXISTENCE",
    "ligneGeneration": 10,
    "statutGeneration": "Génération des documents",
    "libelle": "Déclaration d'existence (formulaire DGI ADP050B, art. 148 CGI)",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": [
      {
        "ligne": 25,
        "statut": "Déroulement de la démarche",
        "emploi": null,
        "libelle": "Déclaration d'existence"
      }
    ]
  },
  {
    "code": "ANNONCE_LEGALE_CONSTITUTION",
    "ligneGeneration": 11,
    "statutGeneration": "Génération des documents",
    "libelle": "Avis de constitution destiné au JAL et au Bulletin officiel",
    "condition": "Tous dossiers — généré ici, puis complété du numéro RC et modifiable pendant le déroulement de la démarche",
    "cocheParDefaut": true,
    "emploi": null,
    "reprises": [
      {
        "ligne": 30,
        "statut": "Déroulement de la démarche",
        "emploi": null,
        "libelle": "Avis de constitution complété du numéro RC"
      }
    ]
  },
  {
    "code": "BORDEREAU_REMISE_DOSSIER",
    "ligneGeneration": 12,
    "statutGeneration": "Génération des documents",
    "libelle": "Bordereau récapitulatif des documents générés",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": "récapitulatif",
    "reprises": [
      {
        "ligne": 46,
        "statut": "Déroulement de la démarche",
        "emploi": "remise",
        "libelle": "Bordereau de remise"
      },
      {
        "ligne": 51,
        "statut": "Ticket annulé",
        "emploi": "restitution",
        "libelle": "Bordereau de restitution"
      }
    ]
  },
  {
    "code": "DEMANDE_AFFILIATION_CNSS",
    "ligneGeneration": 32,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Demande d'affiliation employeur",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "DECLARATION_BENEFICIAIRES_EFFECTIFS",
    "ligneGeneration": 34,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Déclaration des bénéficiaires effectifs",
    "condition": "Toutes les sociétés",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "DEMANDE_DEBLOCAGE_CAPITAL",
    "ligneGeneration": 35,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Demande de déblocage du capital",
    "condition": "Tous dossiers ; le déblocage ne concerne que les capitaux bloqués",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "DECLARATION_CNDP",
    "ligneGeneration": 39,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Déclaration CNDP",
    "condition": "Si la société traite des données personnelles",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "DEMANDE_ADHESION_SIMPL",
    "ligneGeneration": 43,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Demande d'adhésion aux téléservices",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "NOTE_CONFORMITE_MENTIONS_LEGALES",
    "ligneGeneration": 45,
    "statutGeneration": "Déroulement de la démarche",
    "libelle": "Note de conformité des mentions obligatoires",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "NOTE_ANNULATION_DOSSIER",
    "ligneGeneration": 49,
    "statutGeneration": "Ticket annulé",
    "libelle": "Note d'annulation motivée",
    "condition": "Abandon du client, dossier sans suite, refus non surmontable ou non-paiement",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "LETTRE_RETRAIT_DEPOT",
    "ligneGeneration": 50,
    "statutGeneration": "Ticket annulé",
    "libelle": "Lettres de retrait ou de régularisation",
    "condition": "Si un dépôt a déjà été effectué",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": []
  },
  {
    "code": "RAPPORT_COMMISSAIRE_APPORTS",
    "ligneGeneration": null,
    "statutGeneration": "GENERATION_DOCUMENTS",
    "libelle": "Rapport du commissaire aux apports",
    "condition": "Si des apports en nature sont soumis a l'evaluation d'un commissaire",
    "cocheParDefaut": false,
    "emploi": null,
    "reprises": [],
    "note": "Piece du controle bloquant § 18, pas une ligne du parcours."
  }
];

/**
 * LES DIX DOCUMENTS DU STATUT 2, tels que le parcours les numerote de 1 a 10.
 * Statuts et Annonce legale sont coches par defaut ; les huit autres sont
 * decoches, et l'employe choisit — avec, sous les yeux, la condition
 * d application que le parcours enonce. Le systeme ne decide pas a sa place.
 */
export const CHOIX_STATUT_2: ChoixDocument[] = [
  {
    "ligne": 2,
    "libelle": "Contrat de bail ou contrat de domiciliation",
    "documentProduit": "Contrat de bail commercial ou contrat de domiciliation",
    "condition": "Selon la voie retenue pour le siège",
    "cocheParDefaut": false,
    "codes": [
      "CONTRAT_BAIL",
      "CONTRAT_DOMICILIATION"
    ]
  },
  {
    "ligne": 3,
    "libelle": "Statuts",
    "documentProduit": "Statuts de la société",
    "condition": "Tous dossiers — SARL ou SARL AU selon $ASSOCIE_UNIQUE. Contrôles bloquants au lancement de la génération : certificat négatif obtenu ; pièces d'identité et de capacité des associés et des gérants réunies ; rapport du commissaire aux apports obtenu lorsque les apports en nature y sont soumis.",
    "cocheParDefaut": true,
    "codes": [
      "STATUTS_SARL",
      "STATUTS_SARL_AU"
    ]
  },
  {
    "ligne": 4,
    "libelle": "État des actes accomplis pour le compte de la société en formation",
    "documentProduit": "État des actes accomplis, annexé aux statuts",
    "condition": "Si des engagements ont été pris avant l'immatriculation — annexe des statuts",
    "cocheParDefaut": false,
    "codes": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ]
  },
  {
    "ligne": 5,
    "libelle": "Acte de nomination du ou des gérants",
    "documentProduit": "Acte de nomination du gérant",
    "condition": "Si la gérance n'est pas désignée dans les statuts",
    "cocheParDefaut": false,
    "codes": [
      "ACTE_NOMINATION_GERANT"
    ]
  },
  {
    "ligne": 6,
    "libelle": "Déclaration de souscription et de versement",
    "documentProduit": "Déclaration de souscription et de versement",
    "condition": "Tous dossiers — pièce remise à la banque à l'appui du dépôt du capital",
    "cocheParDefaut": false,
    "codes": [
      "ATTESTATION_SOUSCRIPTION_LIBERATION"
    ]
  },
  {
    "ligne": 7,
    "libelle": "Pouvoir pour l'accomplissement des formalités",
    "documentProduit": "Pouvoir donné au mandataire chargé des formalités",
    "condition": "Tous dossiers où le cabinet dépose au nom du client",
    "cocheParDefaut": false,
    "codes": [
      "POUVOIR_FORMALITES_CREATION"
    ]
  },
  {
    "ligne": 8,
    "libelle": "Demande d'inscription à la taxe professionnelle",
    "documentProduit": "Demande d'inscription à la taxe professionnelle (formulaire DGI AAC050B)",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "codes": [
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ]
  },
  {
    "ligne": 9,
    "libelle": "Demande d'immatriculation au registre du commerce",
    "documentProduit": "Déclaration d'immatriculation au RC (modèle 2), en-tête de tribunal conditionnel",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "codes": [
      "DECLARATION_IMMATRICULATION_RC"
    ]
  },
  {
    "ligne": 10,
    "libelle": "Déclaration d'existence",
    "documentProduit": "Déclaration d'existence (formulaire DGI ADP050B, art. 148 CGI)",
    "condition": "Tous dossiers",
    "cocheParDefaut": false,
    "codes": [
      "DECLARATION_EXISTENCE"
    ]
  },
  {
    "ligne": 11,
    "libelle": "Avis de publicité au journal d'annonces légales",
    "documentProduit": "Avis de constitution destiné au JAL et au Bulletin officiel",
    "condition": "Tous dossiers — généré ici, puis complété du numéro RC et modifiable pendant le déroulement de la démarche",
    "cocheParDefaut": true,
    "codes": [
      "ANNONCE_LEGALE_CONSTITUTION"
    ]
  }
];

export const CHAMPS_CREATION: ChampCreation[] = [
  {
    "variable": "ACTE_FORMATION_AUTEUR",
    "cle": "acteFormationAuteur",
    "label": "Acte formation auteur",
    "type": "text",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_COCONTRACTANT",
    "cle": "acteFormationCocontractant",
    "label": "Acte formation cocontractant",
    "type": "text",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_DATE",
    "cle": "acteFormationDate",
    "label": "Acte formation date",
    "type": "date",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_ECHEANCE",
    "cle": "acteFormationEcheance",
    "label": "Acte formation échéance",
    "type": "text",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_MONTANT",
    "cle": "acteFormationMontant",
    "label": "Acte formation montant",
    "type": "number",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_NATURE",
    "cle": "acteFormationNature",
    "label": "Acte formation nature",
    "type": "text",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTE_FORMATION_OBJET",
    "cle": "acteFormationObjet",
    "label": "Acte formation objet",
    "type": "text",
    "options": null,
    "aide": "Boucle ACTES_EN_FORMATION : $ACTE_FORMATION_NUMERO, $ACTE_FORMATION_DATE, $ACTE_FORMATION_NATURE, $ACTE_FORMATION_COCONTRACTANT, $ACTE_FORMATION_OBJET, $ACTE_FORMATION_MONTANT, $ACTE_FORMATION_AUTEUR, $ACTE_FORMATION_ECHEANCE.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": "ACTES_EN_FORMATION"
  },
  {
    "variable": "ACTES_EN_FORMATION_EXISTE",
    "cle": "actesEnFormationExiste",
    "label": "Actes en formation existe",
    "type": "text",
    "options": null,
    "aide": "$ACTES_EN_FORMATION_EXISTE — « oui » / « non ». Déclenche l'établissement de l'état annexé aux statuts.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "ACTES_EN_FORMATION_MANDAT",
    "cle": "actesEnFormationMandat",
    "label": "Actes en formation mandat",
    "type": "text",
    "options": null,
    "aide": "$ACTES_EN_FORMATION_MANDAT, $ACTES_EN_FORMATION_MANDAT_OBJET — Mandat donné pour souscrire d'autres engagements jusqu'à l'immatriculation.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": null
  },
  {
    "variable": "ACTES_EN_FORMATION_MANDAT_OBJET",
    "cle": "actesEnFormationMandatObjet",
    "label": "Actes en formation mandat objet",
    "type": "text",
    "options": null,
    "aide": "$ACTES_EN_FORMATION_MANDAT, $ACTES_EN_FORMATION_MANDAT_OBJET — Mandat donné pour souscrire d'autres engagements jusqu'à l'immatriculation.",
    "section": "9. Actes accomplis pour le compte de la société en formation — [2026-09-03]",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "boucle": null
  },
  {
    "variable": "ACTIVITE_REGLEMENTEE",
    "cle": "activiteReglementee",
    "label": "Activité réglementée",
    "type": "text",
    "options": null,
    "aide": "$ACTIVITE_REGLEMENTEE — **[2026-09-03]** « oui » / « non ». L'activité requiert une autorisation ou un agrément préalable.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "ACTIVITE_REGLEMENTEE_PRECISION",
    "cle": "activiteReglementeePrecision",
    "label": "Activité réglementée précision",
    "type": "textarea",
    "options": null,
    "aide": "$ACTIVITE_REGLEMENTEE_PRECISION — **[2026-09-03]** Nature de l'autorisation ou de l'agrément requis.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "ANNULATION_DEPOTS_EN_COURS",
    "cle": "annulationDepotsEnCours",
    "label": "Annulation dépôts en cours",
    "type": "text",
    "options": null,
    "aide": "$ANNULATION_DEPOTS_EN_COURS — « oui » / « non ». Déclenche la boucle des démarches à retirer ou à régulariser.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "NOTE_ANNULATION_DOSSIER"
    ],
    "boucle": null
  },
  {
    "variable": "ANNULATION_OBSERVATIONS",
    "cle": "annulationObservations",
    "label": "Annulation observations",
    "type": "textarea",
    "options": null,
    "aide": "$ANNULATION_OBSERVATIONS — Observations propres au dossier.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "NOTE_ANNULATION_DOSSIER"
    ],
    "boucle": null
  },
  {
    "variable": "ANNULATION_ORIGINE",
    "cle": "annulationOrigine",
    "label": "Annulation origine",
    "type": "select",
    "options": [
      "Décision du client",
      "Décision du cabinet",
      "Refus de l'administration",
      "Absence de suite du client"
    ],
    "aide": "$ANNULATION_ORIGINE — Case à cocher : « Décision du client » / « Décision du cabinet » / « Refus de l'administration » / « Absence de suite du client ».",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "NOTE_ANNULATION_DOSSIER"
    ],
    "boucle": null
  },
  {
    "variable": "ASSOCIE_PRINCIPAL_EMAIL",
    "cle": "associePrincipalEmail",
    "label": "Associé principal courriel",
    "type": "text",
    "options": null,
    "aide": "**Associé principal (art. 26 CGI) :** $ASSOCIE_PRINCIPAL_NOM, $ASSOCIE_PRINCIPAL_CNI, $ASSOCIE_PRINCIPAL_IF, $ASSOCIE_PRINCIPAL_ADRESSE, $ASSOCIE_PRINCIPAL_VILLE, $ASSOCIE_PRINCIPAL_TEL, $ASSOCIE_PRINCIPAL_FAX, $ASSOCIE_PRINCIPAL_EMAIL.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ASSOCIE_PRINCIPAL_FAX",
    "cle": "associePrincipalFax",
    "label": "Associé principal fax",
    "type": "text",
    "options": null,
    "aide": "**Associé principal (art. 26 CGI) :** $ASSOCIE_PRINCIPAL_NOM, $ASSOCIE_PRINCIPAL_CNI, $ASSOCIE_PRINCIPAL_IF, $ASSOCIE_PRINCIPAL_ADRESSE, $ASSOCIE_PRINCIPAL_VILLE, $ASSOCIE_PRINCIPAL_TEL, $ASSOCIE_PRINCIPAL_FAX, $ASSOCIE_PRINCIPAL_EMAIL.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ASSOCIE_PRINCIPAL_IF",
    "cle": "associePrincipalIf",
    "label": "Associé principal identifiant fiscal",
    "type": "text",
    "options": null,
    "aide": "**Associé principal (art. 26 CGI) :** $ASSOCIE_PRINCIPAL_NOM, $ASSOCIE_PRINCIPAL_CNI, $ASSOCIE_PRINCIPAL_IF, $ASSOCIE_PRINCIPAL_ADRESSE, $ASSOCIE_PRINCIPAL_VILLE, $ASSOCIE_PRINCIPAL_TEL, $ASSOCIE_PRINCIPAL_FAX, $ASSOCIE_PRINCIPAL_EMAIL.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ASSOCIE_PRINCIPAL_TEL",
    "cle": "associePrincipalTel",
    "label": "Associé principal tel",
    "type": "text",
    "options": null,
    "aide": "**Associé principal (art. 26 CGI) :** $ASSOCIE_PRINCIPAL_NOM, $ASSOCIE_PRINCIPAL_CNI, $ASSOCIE_PRINCIPAL_IF, $ASSOCIE_PRINCIPAL_ADRESSE, $ASSOCIE_PRINCIPAL_VILLE, $ASSOCIE_PRINCIPAL_TEL, $ASSOCIE_PRINCIPAL_FAX, $ASSOCIE_PRINCIPAL_EMAIL.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ASSOCIE_PRINCIPAL_VILLE",
    "cle": "associePrincipalVille",
    "label": "Associé principal ville",
    "type": "text",
    "options": null,
    "aide": "**Associé principal (art. 26 CGI) :** $ASSOCIE_PRINCIPAL_NOM, $ASSOCIE_PRINCIPAL_CNI, $ASSOCIE_PRINCIPAL_IF, $ASSOCIE_PRINCIPAL_ADRESSE, $ASSOCIE_PRINCIPAL_VILLE, $ASSOCIE_PRINCIPAL_TEL, $ASSOCIE_PRINCIPAL_FAX, $ASSOCIE_PRINCIPAL_EMAIL.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ASSOCIE_RESIDENCE",
    "cle": "associeResidence",
    "label": "Résidence",
    "type": "text",
    "options": null,
    "aide": "$ASSOCIE_RESIDENCE — **[2026-09-09]** « résident » / « non-résident » au sens de la réglementation des changes. Pilote les pièces exigées (passeport et justificatif de résidence, traduction et légalisation des documents sociaux d'une personne morale étrangère) et déclenche la déclaration d'investissement étranger à l'Office des changes.",
    "section": "5. Associés — boucle `ASSOCIES`",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC"
    ],
    "boucle": "ASSOCIES"
  },
  {
    "variable": "BAIL_DATE_EFFET",
    "cle": "bailDateEffet",
    "label": "Bail date effet",
    "type": "date",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_DEPOT_GARANTIE",
    "cle": "bailDepotGarantie",
    "label": "Bail dépôt garantie",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_DESTINATION",
    "cle": "bailDestination",
    "label": "Bail destination",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_DUREE",
    "cle": "bailDuree",
    "label": "Bail durée",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_FRAIS_CHARGE",
    "cle": "bailFraisCharge",
    "label": "Bail frais charge",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_FRANCHISE",
    "cle": "bailFranchise",
    "label": "Bail franchise",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_LOCAL_ADRESSE",
    "cle": "bailLocalAdresse",
    "label": "Local adresse",
    "type": "text",
    "options": null,
    "aide": "Boucle BAIL_LOCAUX : $BAIL_LOCAL_DESIGNATION, $BAIL_LOCAL_SUPERFICIE, $BAIL_LOCAL_ADRESSE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": "BAIL_LOCAUX"
  },
  {
    "variable": "BAIL_LOCAL_DESIGNATION",
    "cle": "bailLocalDesignation",
    "label": "Local désignation",
    "type": "text",
    "options": null,
    "aide": "Boucle BAIL_LOCAUX : $BAIL_LOCAL_DESIGNATION, $BAIL_LOCAL_SUPERFICIE, $BAIL_LOCAL_ADRESSE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": "BAIL_LOCAUX"
  },
  {
    "variable": "BAIL_LOCAL_SUPERFICIE",
    "cle": "bailLocalSuperficie",
    "label": "Local superficie",
    "type": "number",
    "options": null,
    "aide": "Boucle BAIL_LOCAUX : $BAIL_LOCAL_DESIGNATION, $BAIL_LOCAL_SUPERFICIE, $BAIL_LOCAL_ADRESSE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": "BAIL_LOCAUX"
  },
  {
    "variable": "BAIL_LOYER_CHIFFRES",
    "cle": "bailLoyerChiffres",
    "label": "Bail loyer chiffres",
    "type": "number",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_LOYER_MODALITES",
    "cle": "bailLoyerModalites",
    "label": "Bail loyer modalités",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_LOYER_PERIODICITE",
    "cle": "bailLoyerPeriodicite",
    "label": "Bail loyer périodicité",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_SOUS_LOCATION",
    "cle": "bailSousLocation",
    "label": "Bail sous location",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_TRAVAUX_PRENEUR",
    "cle": "bailTravauxPreneur",
    "label": "Bail travaux preneur",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BAIL_TRIBUNAL",
    "cle": "bailTribunal",
    "label": "Bail tribunal",
    "type": "text",
    "options": null,
    "aide": "Contrat : $BAIL_TITRE_FONCIER, $BAIL_TITRE_FONCIER_NOM, $BAIL_DESTINATION, $BAIL_DUREE, $BAIL_DATE_EFFET, $BAIL_DATE_FIN, $BAIL_LOYER_CHIFFRES, $BAIL_LOYER_LETTRES, $BAIL_LOYER_PERIODICITE, $BAIL_LOYER_MODALITES, $BAIL_FRANCHISE, $BAIL_DEPOT_GARANTIE, $BAIL_TRAVAUX_PRENEUR, $BAIL_SOUS_LOCATION (« autorisée » / « interdite »), $BAIL_TRIBUNAL, $BAIL_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_BAIL"
    ],
    "boucle": null
  },
  {
    "variable": "BANQUE_ADRESSE",
    "cle": "banqueAdresse",
    "label": "Banque adresse",
    "type": "text",
    "options": null,
    "aide": "$BANQUE_ADRESSE — **[2026-09-03]** Adresse postale de l'agence (destinataire de la demande de déblocage).",
    "section": "4. Capital et parts",
    "documents": [
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "BANQUE_AGENCE",
    "cle": "banqueAgence",
    "label": "Banque agence",
    "type": "text",
    "options": null,
    "aide": "$BANQUE_AGENCE — **[2026-09-03]** Agence de l'établissement dépositaire.",
    "section": "4. Capital et parts",
    "documents": [
      "ATTESTATION_SOUSCRIPTION_LIBERATION",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "BE_ADRESSE",
    "cle": "beAdresse",
    "label": "Adresse",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_CIVILITE",
    "cle": "beCivilite",
    "label": "Civilité",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_CRITERE",
    "cle": "beCritere",
    "label": "Critère",
    "type": "select",
    "options": [
      "Détention, directe ou indirecte, d'au moins vingt-cinq pour cent du capital",
      "Détention, directe ou indirecte, d'au moins vingt-cinq pour cent des droits de vote",
      "Exercice d'un contrôle par tout autre moyen",
      "Qualité de dirigeant, à défaut de toute autre personne identifiable"
    ],
    "aide": "$BE_CRITERE — Case à cocher : « Détention, directe ou indirecte, d'au moins vingt-cinq pour cent du capital » / « … des droits de vote » / « Exercice d'un contrôle par tout autre moyen » / « Qualité de dirigeant, à défaut de toute autre personne identifiable ».",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_DATE_ACQUISITION",
    "cle": "beDateAcquisition",
    "label": "Date acquisition",
    "type": "date",
    "options": null,
    "aide": "$BE_POURCENTAGE, $BE_POURCENTAGE_VOTE, $BE_NOMBRE_PARTS, $BE_DATE_ACQUISITION — Étendue du contrôle.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_DATE_NAISSANCE",
    "cle": "beDateNaissance",
    "label": "Date naissance",
    "type": "date",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_EMAIL",
    "cle": "beEmail",
    "label": "Courriel",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_FONCTION",
    "cle": "beFonction",
    "label": "Fonction",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_GENRE",
    "cle": "beGenre",
    "label": "Genre",
    "type": "select",
    "options": [
      "Masculin",
      "Féminin"
    ],
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_IDENTIFIANT_FISCAL",
    "cle": "beIdentifiantFiscal",
    "label": "Identifiant fiscal",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_LIEU_NAISSANCE",
    "cle": "beLieuNaissance",
    "label": "Lieu naissance",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_MODE_DETENTION",
    "cle": "beModeDetention",
    "label": "Mode détention",
    "type": "select",
    "options": [
      "Directe",
      "Indirecte"
    ],
    "aide": "$BE_MODE_DETENTION — Case à cocher : « Directe » / « Indirecte ».",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_NATIONALITE",
    "cle": "beNationalite",
    "label": "Nationalité",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_NATIONALITE_AUTRE",
    "cle": "beNationaliteAutre",
    "label": "Nationalité autre",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_NOM",
    "cle": "beNom",
    "label": "Nom",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_OBSERVATIONS",
    "cle": "beObservations",
    "label": "Observations",
    "type": "textarea",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_PAYS_RESIDENCE",
    "cle": "bePaysResidence",
    "label": "Pays résidence",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_PIECE_DATE_DELIVRANCE",
    "cle": "bePieceDateDelivrance",
    "label": "Pièce date délivrance",
    "type": "date",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_PIECE_NUMERO",
    "cle": "bePieceNumero",
    "label": "Pièce numéro",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_PIECE_TYPE",
    "cle": "bePieceType",
    "label": "Pièce type",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_POURCENTAGE_VOTE",
    "cle": "bePourcentageVote",
    "label": "Pourcentage vote",
    "type": "text",
    "options": null,
    "aide": "$BE_POURCENTAGE, $BE_POURCENTAGE_VOTE, $BE_NOMBRE_PARTS, $BE_DATE_ACQUISITION — Étendue du contrôle.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_PRENOM",
    "cle": "bePrenom",
    "label": "Prénom",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BE_TELEPHONE",
    "cle": "beTelephone",
    "label": "Téléphone",
    "type": "text",
    "options": null,
    "aide": "Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, $BE_CIVILITE, $BE_PRENOM, $BE_NOM, $BE_GENRE (case à cocher « Masculin » / « Féminin »), $BE_DATE_NAISSANCE, $BE_LIEU_NAISSANCE, $BE_NATIONALITE, $BE_NATIONALITE_AUTRE, $BE_PAYS_RESIDENCE, $BE_ADRESSE, $BE_PIECE_TYPE, $BE_PIECE_NUMERO, $BE_PIECE_DATE_DELIVRANCE, $BE_IDENTIFIANT_FISCAL, $BE_TELEPHONE, $BE_EMAIL, $BE_FONCTION, $BE_OBSERVATIONS.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": "BENEFICIAIRES_EFFECTIFS"
  },
  {
    "variable": "BORDEREAU_OBJET",
    "cle": "bordereauObjet",
    "label": "Bordereau objet",
    "type": "text",
    "options": null,
    "aide": "$BORDEREAU_OBJET — « remise » / « restitution » / « récapitulatif ». Pilote les trois emplois du bordereau.",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER"
    ],
    "boucle": null
  },
  {
    "variable": "CLIENT_SIGNATAIRE_NOM",
    "cle": "clientSignataireNom",
    "label": "Client signataire nom",
    "type": "text",
    "options": null,
    "aide": "$CLIENT_SIGNATAIRE_NOM — Personne signant la décharge de remise des pièces.",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER",
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": null
  },
  {
    "variable": "CN_PROPOSITION_1",
    "cle": "cnProposition1",
    "label": "Certificat négatif proposition 1",
    "type": "text",
    "options": null,
    "aide": "$CN_PROPOSITION_1, $CN_PROPOSITION_2, $CN_PROPOSITION_3 — **[2026-09-03]** Les trois dénominations proposées à l'OMPIC, par ordre de préférence.",
    "section": "2. Identifiants",
    "documents": [
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "CN_PROPOSITION_2",
    "cle": "cnProposition2",
    "label": "Certificat négatif proposition 2",
    "type": "text",
    "options": null,
    "aide": "$CN_PROPOSITION_1, $CN_PROPOSITION_2, $CN_PROPOSITION_3 — **[2026-09-03]** Les trois dénominations proposées à l'OMPIC, par ordre de préférence.",
    "section": "2. Identifiants",
    "documents": [
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "CN_PROPOSITION_3",
    "cle": "cnProposition3",
    "label": "Certificat négatif proposition 3",
    "type": "text",
    "options": null,
    "aide": "$CN_PROPOSITION_1, $CN_PROPOSITION_2, $CN_PROPOSITION_3 — **[2026-09-03]** Les trois dénominations proposées à l'OMPIC, par ordre de préférence.",
    "section": "2. Identifiants",
    "documents": [
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "CNDP_CONTACT_DROITS",
    "cle": "cndpContactDroits",
    "label": "CNDP contact droits",
    "type": "text",
    "options": null,
    "aide": "$CNDP_CONTACT_DROITS — Point de contact auprès duquel s'exercent les droits d'accès, de rectification et d'opposition.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": null
  },
  {
    "variable": "CNDP_MOTIF_MODIFICATION",
    "cle": "cndpMotifModification",
    "label": "CNDP motif modification",
    "type": "text",
    "options": null,
    "aide": "$CNDP_RECEPISSE_NUMERO, $CNDP_MOTIF_MODIFICATION — Conditionnels, si déclaration modificative.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": null
  },
  {
    "variable": "CNDP_NATURE_DECLARATION",
    "cle": "cndpNatureDeclaration",
    "label": "CNDP nature déclaration",
    "type": "select",
    "options": [
      "Déclaration normale",
      "Déclaration simplifiée",
      "Demande d'autorisation préalable",
      "Déclaration modificative"
    ],
    "aide": "$CNDP_NATURE_DECLARATION — Case à cocher : « Déclaration normale » / « Déclaration simplifiée » / « Demande d'autorisation préalable » / « Déclaration modificative ».",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": null
  },
  {
    "variable": "CNDP_RESPONSABLE_NOM",
    "cle": "cndpResponsableNom",
    "label": "CNDP responsable nom",
    "type": "text",
    "options": null,
    "aide": "$CNDP_RESPONSABLE_NOM, $CNDP_RESPONSABLE_QUALITE — Responsable du traitement, signataire de la déclaration.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": null
  },
  {
    "variable": "CNDP_RESPONSABLE_QUALITE",
    "cle": "cndpResponsableQualite",
    "label": "CNDP responsable qualité",
    "type": "text",
    "options": null,
    "aide": "$CNDP_RESPONSABLE_NOM, $CNDP_RESPONSABLE_QUALITE — Responsable du traitement, signataire de la déclaration.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_ACTIVITE_CARACTERE",
    "cle": "cnssActiviteCaractere",
    "label": "CNSS activité caractère",
    "type": "select",
    "options": [
      "Permanente",
      "Saisonnière",
      "Occasionnelle"
    ],
    "aide": "$CNSS_ACTIVITE_CARACTERE — Case à cocher : « Permanente » / « Saisonnière » / « Occasionnelle ».",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_CONTACT_EMAIL",
    "cle": "cnssContactEmail",
    "label": "CNSS contact courriel",
    "type": "text",
    "options": null,
    "aide": "$CNSS_CONTACT_NOM, $CNSS_CONTACT_TELEPHONE, $CNSS_CONTACT_EMAIL — Personne habilitée pour la télédéclaration.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_CONTACT_NOM",
    "cle": "cnssContactNom",
    "label": "CNSS contact nom",
    "type": "text",
    "options": null,
    "aide": "$CNSS_CONTACT_NOM, $CNSS_CONTACT_TELEPHONE, $CNSS_CONTACT_EMAIL — Personne habilitée pour la télédéclaration.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_CONTACT_TELEPHONE",
    "cle": "cnssContactTelephone",
    "label": "CNSS contact téléphone",
    "type": "text",
    "options": null,
    "aide": "$CNSS_CONTACT_NOM, $CNSS_CONTACT_TELEPHONE, $CNSS_CONTACT_EMAIL — Personne habilitée pour la télédéclaration.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_DATE_PREMIER_SALARIE",
    "cle": "cnssDatePremierSalarie",
    "label": "CNSS date premier salarié",
    "type": "date",
    "options": null,
    "aide": "$CNSS_DATE_PREMIER_SALARIE — Date d'embauche du premier salarié.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_EFFECTIF",
    "cle": "cnssEffectif",
    "label": "CNSS effectif",
    "type": "number",
    "options": null,
    "aide": "$CNSS_EFFECTIF — Effectif salarié à la date de la demande.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "CNSS_MODE_DECLARATION",
    "cle": "cnssModeDeclaration",
    "label": "CNSS mode déclaration",
    "type": "select",
    "options": [
      "Télédéclaration et télépaiement (DAMANCOM)",
      "Déclaration sur support papier"
    ],
    "aide": "$CNSS_MODE_DECLARATION — Case à cocher : « Télédéclaration et télépaiement (DAMANCOM) » / « Déclaration sur support papier ».",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": null
  },
  {
    "variable": "COMMISSAIRE_APPORTS_DESIGNATION",
    "cle": "commissaireApportsDesignation",
    "label": "Commissaire apports désignation",
    "type": "text",
    "options": null,
    "aide": "$COMMISSAIRE_APPORTS_DESIGNATION — **[2026-09-03]** « requis » / « dispense » / « sans objet ». Le rapport est requis si un apport en nature excède cent mille dirhams ou si l'ensemble des apports en nature excède la moitié du capital. Cette variable commande le contrôle effectué au lancement de la génération des statuts : lorsqu'elle vaut « requis », le rapport doit être au dossier.",
    "section": "6. Apports — boucle `APPORTS_PAR_ASSOCIE`",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "COMPTE_DEFINITIF_NUMERO",
    "cle": "compteDefinitifNumero",
    "label": "Compte définitif numéro",
    "type": "text",
    "options": null,
    "aide": "$COMPTE_DEFINITIF_NUMERO — **[2026-09-03]** Numéro du compte courant définitif, ouvert après immatriculation.",
    "section": "4. Capital et parts",
    "documents": [
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "DATE_ATTESTATION_BLOCAGE",
    "cle": "dateAttestationBlocage",
    "label": "Date attestation blocage",
    "type": "date",
    "options": null,
    "aide": "$DATE_ATTESTATION_BLOCAGE — **[2026-09-03]** Date de l'attestation de blocage délivrée par la banque.",
    "section": "4. Capital et parts",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "DATE_DEBUT_ACTIVITE",
    "cle": "dateDebutActivite",
    "label": "Date début activité",
    "type": "date",
    "options": null,
    "aide": "$DATE_DEBUT_ACTIVITE, $DATE_COMMENCEMENT_EXPLOITATION — Dates de démarrage.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DATE_IMMATRICULATION",
    "cle": "dateImmatriculation",
    "label": "Date immatriculation",
    "type": "date",
    "options": null,
    "aide": "$DATE_IMMATRICULATION — **[2026-09-03]** Date d'immatriculation au registre du commerce. Distincte de $DATE_DEPOT_LEGAL : le dépôt précède l'immatriculation.",
    "section": "2. Identifiants",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS",
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "DATE_SOUSCRIPTION",
    "cle": "dateSouscription",
    "label": "Date souscription",
    "type": "date",
    "options": null,
    "aide": "$DATE_SOUSCRIPTION — **[2026-09-03]** Date de la déclaration de souscription et de versement.",
    "section": "4. Capital et parts",
    "documents": [
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "DE_ACTIVITE_NATURE",
    "cle": "activiteNature",
    "label": "Déclaration d'existence activité nature",
    "type": "select",
    "options": [
      "Commerciale",
      "Industrielle",
      "Artisanale",
      "Prestation de services",
      "Immobilière",
      "Agricole",
      "Profession libérale"
    ],
    "aide": "$DE_ACTIVITE_NATURE — « Commerciale » / « Industrielle » / « Artisanale » / « Prestation de services » / « Immobilière » / « Agricole » / « Profession libérale ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DE_REGIME_RESULTAT",
    "cle": "regimeResultat",
    "label": "Déclaration d'existence régime résultat",
    "type": "select",
    "options": [
      "Impôt sur les sociétés — régime du résultat net réel",
      "Impôt sur les sociétés — sur option",
      "Impôt sur le revenu — régime du résultat net réel",
      "Impôt sur le revenu — régime du résultat net simplifié",
      "Impôt sur le revenu — régime de la contribution professionnelle unique"
    ],
    "aide": "$DE_REGIME_RESULTAT — « Impôt sur les sociétés — régime du résultat net réel » / « Impôt sur les sociétés — sur option » / « Impôt sur le revenu — régime du résultat net réel » / « Impôt sur le revenu — régime du résultat net simplifié » / « Impôt sur le revenu — régime de la contribution professionnelle unique ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DE_TVA_ASSUJETTISSEMENT",
    "cle": "tvaAssujettissement",
    "label": "Déclaration d'existence TVA assujettissement",
    "type": "select",
    "options": [
      "Assujetti à titre obligatoire",
      "Assujetti par option",
      "Exonéré",
      "Hors champ d'application"
    ],
    "aide": "$DE_TVA_ASSUJETTISSEMENT — « Assujetti à titre obligatoire » / « Assujetti par option » / « Exonéré » / « Hors champ d'application ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DE_TVA_DATE_OPTION",
    "cle": "deTvaDateOption",
    "label": "Déclaration d'existence TVA date option",
    "type": "date",
    "options": null,
    "aide": "$DE_TVA_DATE_OPTION — **[2026-09-09]** Date d'effet de l'option pour l'assujettissement à la TVA. Conditionnelle : servie lorsque $DE_TVA_ASSUJETTISSEMENT = « Assujetti par option ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null
  },
  {
    "variable": "DE_TVA_FAIT_GENERATEUR",
    "cle": "tvaFaitGenerateur",
    "label": "Déclaration d'existence TVA fait générateur",
    "type": "select",
    "options": [
      "Régime de l'encaissement",
      "Régime du débit"
    ],
    "aide": "$DE_TVA_FAIT_GENERATEUR — « Régime de l'encaissement » / « Régime du débit ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DE_TVA_PERIODICITE",
    "cle": "tvaPeriodicite",
    "label": "Déclaration d'existence TVA périodicité",
    "type": "select",
    "options": [
      "Déclaration mensuelle",
      "Déclaration trimestrielle"
    ],
    "aide": "$DE_TVA_PERIODICITE — « Déclaration mensuelle » / « Déclaration trimestrielle ».",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DEBLOCAGE_DESTINATION",
    "cle": "deblocageDestination",
    "label": "Déblocage destination",
    "type": "text",
    "options": null,
    "aide": "$DEBLOCAGE_DESTINATION — **[2026-09-03]** « compte définitif » / « mise à disposition ». Pilote la destination des fonds débloqués.",
    "section": "4. Capital et parts",
    "documents": [
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "DECLARANT_PIECE_NUMERO",
    "cle": "declarantPieceNumero",
    "label": "Déclarant pièce numéro",
    "type": "text",
    "options": null,
    "aide": "$DECLARANT_PIECE_TYPE, $DECLARANT_PIECE_NUMERO — **[2026-09-09]** Pièce d'identité du déclarant signataire d'un formulaire administratif, et son numéro.",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "DECLARANT_PIECE_TYPE",
    "cle": "declarantPieceType",
    "label": "Déclarant pièce type",
    "type": "text",
    "options": null,
    "aide": "$DECLARANT_PIECE_TYPE, $DECLARANT_PIECE_NUMERO — **[2026-09-09]** Pièce d'identité du déclarant signataire d'un formulaire administratif, et son numéro.",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "DEMARCHE_SUITE",
    "cle": "demarcheSuite",
    "label": "Démarche suite",
    "type": "text",
    "options": null,
    "aide": "Boucle DEMARCHES_INTERROMPUES : $DEMARCHE_DESIGNATION, $DEMARCHE_ADMINISTRATION, $DEMARCHE_DATE_DEPOT, $DEMARCHE_REFERENCE, $DEMARCHE_SUITE.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "NOTE_ANNULATION_DOSSIER"
    ],
    "boucle": "DEMARCHES_INTERROMPUES"
  },
  {
    "variable": "DIRECTION_REGIONALE",
    "cle": "directionRegionale",
    "label": "Direction régionale",
    "type": "text",
    "options": null,
    "aide": "$DIRECTION_REGIONALE, $SUBDIVISION — Services de la direction générale des impôts.",
    "section": "3. Administration compétente",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DEMANDE_ADHESION_SIMPL",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "DOCUMENT_COMMERCIAL_CONFORMITE",
    "cle": "documentCommercialConformite",
    "label": "Document commercial conformité",
    "type": "text",
    "options": null,
    "aide": "Boucle DOCUMENTS_COMMERCIAUX : $DOCUMENT_COMMERCIAL_DESIGNATION (papier à en-tête, facture, devis, bon de commande, site internet…), $DOCUMENT_COMMERCIAL_CONFORMITE (« conforme » / « à corriger » / « non communiqué »), $DOCUMENT_COMMERCIAL_MENTIONS_MANQUANTES.",
    "section": "19. Documents commerciaux et mentions légales — [2026-09-09]",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": "DOCUMENTS_COMMERCIAUX"
  },
  {
    "variable": "DOCUMENT_COMMERCIAL_DESIGNATION",
    "cle": "documentCommercialDesignation",
    "label": "Document commercial désignation",
    "type": "text",
    "options": null,
    "aide": "Boucle DOCUMENTS_COMMERCIAUX : $DOCUMENT_COMMERCIAL_DESIGNATION (papier à en-tête, facture, devis, bon de commande, site internet…), $DOCUMENT_COMMERCIAL_CONFORMITE (« conforme » / « à corriger » / « non communiqué »), $DOCUMENT_COMMERCIAL_MENTIONS_MANQUANTES.",
    "section": "19. Documents commerciaux et mentions légales — [2026-09-09]",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": "DOCUMENTS_COMMERCIAUX"
  },
  {
    "variable": "DOCUMENT_COMMERCIAL_MENTIONS_MANQUANTES",
    "cle": "documentCommercialMentionsManquantes",
    "label": "Document commercial mentions manquantes",
    "type": "text",
    "options": null,
    "aide": "Boucle DOCUMENTS_COMMERCIAUX : $DOCUMENT_COMMERCIAL_DESIGNATION (papier à en-tête, facture, devis, bon de commande, site internet…), $DOCUMENT_COMMERCIAL_CONFORMITE (« conforme » / « à corriger » / « non communiqué »), $DOCUMENT_COMMERCIAL_MENTIONS_MANQUANTES.",
    "section": "19. Documents commerciaux et mentions légales — [2026-09-09]",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": "DOCUMENTS_COMMERCIAUX"
  },
  {
    "variable": "DOMICILIATION_ADRESSE",
    "cle": "domiciliationAdresse",
    "label": "Domiciliation adresse",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_DATE_EFFET",
    "cle": "domiciliationDateEffet",
    "label": "Domiciliation date effet",
    "type": "date",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_DEPOT_GARANTIE",
    "cle": "domiciliationDepotGarantie",
    "label": "Domiciliation dépôt garantie",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_DUREE",
    "cle": "domiciliationDuree",
    "label": "Domiciliation durée",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_FRAIS_CHARGE",
    "cle": "domiciliationFraisCharge",
    "label": "Domiciliation frais charge",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_MODALITES_PAIEMENT",
    "cle": "domiciliationModalitesPaiement",
    "label": "Domiciliation modalités paiement",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_PERIODICITE",
    "cle": "domiciliationPeriodicite",
    "label": "Domiciliation périodicité",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_PREAVIS",
    "cle": "domiciliationPreavis",
    "label": "Domiciliation préavis",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_PRESTATIONS_ANNEXES",
    "cle": "domiciliationPrestationsAnnexes",
    "label": "Domiciliation prestations annexes",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_REDEVANCE_CHIFFRES",
    "cle": "domiciliationRedevanceChiffres",
    "label": "Domiciliation redevance chiffres",
    "type": "number",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_RENOUVELLEMENT",
    "cle": "domiciliationRenouvellement",
    "label": "Domiciliation renouvellement",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOMICILIATION_TRIBUNAL",
    "cle": "domiciliationTribunal",
    "label": "Domiciliation tribunal",
    "type": "text",
    "options": null,
    "aide": "Contrat : $DOMICILIATION_ADRESSE, $DOMICILIATION_DUREE, $DOMICILIATION_DATE_EFFET, $DOMICILIATION_RENOUVELLEMENT (« tacite » / « exprès »), $DOMICILIATION_PREAVIS, $DOMICILIATION_REDEVANCE_CHIFFRES, $DOMICILIATION_REDEVANCE_LETTRES, $DOMICILIATION_PERIODICITE, $DOMICILIATION_MODALITES_PAIEMENT, $DOMICILIATION_DEPOT_GARANTIE, $DOMICILIATION_PRESTATIONS_ANNEXES, $DOMICILIATION_TRIBUNAL, $DOMICILIATION_FRAIS_CHARGE.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "CONTRAT_DOMICILIATION"
    ],
    "boucle": null
  },
  {
    "variable": "DOSSIER_MOTIF_ANNULATION",
    "cle": "dossierMotifAnnulation",
    "label": "Dossier motif annulation",
    "type": "text",
    "options": null,
    "aide": "$DOSSIER_MOTIF_ANNULATION — Motif d'annulation du ticket (bordereau de restitution).",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER",
      "LETTRE_RETRAIT_DEPOT",
      "NOTE_ANNULATION_DOSSIER"
    ],
    "boucle": null
  },
  {
    "variable": "EFFECTIF_PREVISIONNEL",
    "cle": "effectifPrevisionnel",
    "label": "Effectif prévisionnel",
    "type": "text",
    "options": null,
    "aide": "$EFFECTIF_PREVISIONNEL — **[2026-09-03]** Effectif salarié envisagé à l'ouverture du dossier.",
    "section": "1. Société",
    "documents": [
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "EMAIL",
    "cle": "email",
    "label": "Courriel",
    "type": "text",
    "options": null,
    "aide": "$TELEPHONE, $FAX, $EMAIL — Coordonnées de la société.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_CNDP",
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_ADHESION_SIMPL",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ENSEIGNE",
    "cle": "enseigne",
    "label": "Enseigne",
    "type": "text",
    "options": null,
    "aide": "$ENSEIGNE — Enseigne commerciale.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION",
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "ETAB_EFFECTIF",
    "cle": "etabEffectif",
    "label": "Établissement effectif",
    "type": "number",
    "options": null,
    "aide": "Boucle ETABLISSEMENTS (existante, complétée) : $ETAB_ADRESSE, $ETAB_VILLE, $ETAB_ACTIVITE, $ETAB_IDENTIFIANT_TP, $ETAB_EFFECTIF — **[2026-09-03]** pour le dernier.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "boucle": "ETABLISSEMENTS"
  },
  {
    "variable": "FAX",
    "cle": "fax",
    "label": "Fax",
    "type": "text",
    "options": null,
    "aide": "$TELEPHONE, $FAX, $EMAIL — Coordonnées de la société.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "LIBERATION_QUOTITE",
    "cle": "liberationQuotite",
    "label": "Libération quotité",
    "type": "number",
    "options": null,
    "aide": "$LIBERATION_QUOTITE — **[2026-09-03]** Quotité libérée lorsque $MODE_LIBERATION = « partielle » (ex. « un quart »). Le moteur ne calculant pas, la valeur est fournie par le dossier.",
    "section": "4. Capital et parts",
    "documents": [
      "ATTESTATION_SOUSCRIPTION_LIBERATION",
      "DECLARATION_IMMATRICULATION_RC",
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": null
  },
  {
    "variable": "MENTIONS_OBSERVATIONS",
    "cle": "mentionsObservations",
    "label": "Mentions observations",
    "type": "textarea",
    "options": null,
    "aide": "$MENTIONS_OBSERVATIONS — Observations propres au dossier.",
    "section": "19. Documents commerciaux et mentions légales — [2026-09-09]",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": null
  },
  {
    "variable": "PIECE_FORME",
    "cle": "pieceForme",
    "label": "Pièce forme",
    "type": "text",
    "options": null,
    "aide": "Boucle PIECES_REMISES : $PIECE_NUMERO, $PIECE_DESIGNATION, $PIECE_FORME (original, copie, copie légalisée), $PIECE_NOMBRE, $PIECE_OBSERVATION.",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER"
    ],
    "boucle": "PIECES_REMISES"
  },
  {
    "variable": "PIECE_NOMBRE",
    "cle": "pieceNombre",
    "label": "Pièce nombre",
    "type": "number",
    "options": null,
    "aide": "Boucle PIECES_REMISES : $PIECE_NUMERO, $PIECE_DESIGNATION, $PIECE_FORME (original, copie, copie légalisée), $PIECE_NOMBRE, $PIECE_OBSERVATION.",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER"
    ],
    "boucle": "PIECES_REMISES"
  },
  {
    "variable": "PIECE_OBSERVATION",
    "cle": "pieceObservation",
    "label": "Pièce observation",
    "type": "textarea",
    "options": null,
    "aide": "Boucle PIECES_REMISES : $PIECE_NUMERO, $PIECE_DESIGNATION, $PIECE_FORME (original, copie, copie légalisée), $PIECE_NOMBRE, $PIECE_OBSERVATION.",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER"
    ],
    "boucle": "PIECES_REMISES"
  },
  {
    "variable": "PIECES_PRODUITES",
    "cle": "piecesProduites",
    "label": "Pièces produites",
    "type": "text",
    "options": null,
    "aide": "$PIECES_PRODUITES — Pièces jointes aux formulaires administratifs (existante).",
    "section": "12. Dossier, pièces et suivi — [2026-09-03]",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "POUVOIR_MANDANT",
    "cle": "pouvoirMandant",
    "label": "Pouvoir mandant",
    "type": "text",
    "options": null,
    "aide": "$POUVOIR_MANDANT — « associés » / « gérant ». Détermine qui donne le pouvoir.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_ADRESSE",
    "cle": "pouvoirMandataireAdresse",
    "label": "Pouvoir mandataire adresse",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_CIVILITE",
    "cle": "pouvoirMandataireCivilite",
    "label": "Pouvoir mandataire civilité",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_DENOMINATION",
    "cle": "pouvoirMandataireDenomination",
    "label": "Pouvoir mandataire dénomination",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_FORME",
    "cle": "pouvoirMandataireForme",
    "label": "Pouvoir mandataire forme",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_NATIONALITE",
    "cle": "pouvoirMandataireNationalite",
    "label": "Pouvoir mandataire nationalité",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_NOM",
    "cle": "pouvoirMandataireNom",
    "label": "Pouvoir mandataire nom",
    "type": "text",
    "options": null,
    "aide": "$MANDATAIRE_NOM — **[NOUVEAU]** Mandataire habilité à **engager la société** (article 15 des statuts). À ne pas confondre avec $POUVOIR_MANDATAIRE_NOM (§ 10).",
    "section": "7. Gérance et signature sociale",
    "documents": [
      "LETTRE_RETRAIT_DEPOT",
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_PIECE_NUMERO",
    "cle": "pouvoirMandatairePieceNumero",
    "label": "Pouvoir mandataire pièce numéro",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_PIECE_TYPE",
    "cle": "pouvoirMandatairePieceType",
    "label": "Pouvoir mandataire pièce type",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_PRENOM",
    "cle": "pouvoirMandatairePrenom",
    "label": "Pouvoir mandataire prénom",
    "type": "text",
    "options": null,
    "aide": "Personne physique : $POUVOIR_MANDATAIRE_CIVILITE, $POUVOIR_MANDATAIRE_PRENOM, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_NATIONALITE, $POUVOIR_MANDATAIRE_ADRESSE, $POUVOIR_MANDATAIRE_PIECE_TYPE, $POUVOIR_MANDATAIRE_PIECE_NUMERO.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_RC_NUMERO",
    "cle": "pouvoirMandataireRcNumero",
    "label": "Pouvoir mandataire registre du commerce numéro",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_RC_VILLE",
    "cle": "pouvoirMandataireRcVille",
    "label": "Pouvoir mandataire registre du commerce ville",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_REPRESENTANT_NOM",
    "cle": "pouvoirMandataireRepresentantNom",
    "label": "Pouvoir mandataire représentant nom",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_SIEGE",
    "cle": "pouvoirMandataireSiege",
    "label": "Pouvoir mandataire siège",
    "type": "text",
    "options": null,
    "aide": "Personne morale : $POUVOIR_MANDATAIRE_DENOMINATION, $POUVOIR_MANDATAIRE_FORME, $POUVOIR_MANDATAIRE_SIEGE, $POUVOIR_MANDATAIRE_RC_VILLE, $POUVOIR_MANDATAIRE_RC_NUMERO, $POUVOIR_MANDATAIRE_REPRESENTANT_NOM.",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "POUVOIR_MANDATAIRE_TYPE",
    "cle": "pouvoirMandataireType",
    "label": "Pouvoir mandataire type",
    "type": "text",
    "options": null,
    "aide": "$POUVOIR_MANDATAIRE_TYPE — « personne physique » / « personne morale ».",
    "section": "10. Pouvoir pour les formalités — [2026-09-03]",
    "documents": [
      "POUVOIR_FORMALITES_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DATE_EVENEMENT",
    "cle": "rbeDateEvenement",
    "label": "Registre des bénéficiaires effectifs date événement",
    "type": "date",
    "options": null,
    "aide": "$RBE_MOTIF_MODIFICATION, $RBE_DATE_EVENEMENT — Conditionnels, si déclaration modificative.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_EMAIL",
    "cle": "rbeDeclarantEmail",
    "label": "Registre des bénéficiaires effectifs déclarant courriel",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_NOM",
    "cle": "rbeDeclarantNom",
    "label": "Registre des bénéficiaires effectifs déclarant nom",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_PIECE_NUMERO",
    "cle": "rbeDeclarantPieceNumero",
    "label": "Registre des bénéficiaires effectifs déclarant pièce numéro",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_PIECE_TYPE",
    "cle": "rbeDeclarantPieceType",
    "label": "Registre des bénéficiaires effectifs déclarant pièce type",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_QUALITE",
    "cle": "rbeDeclarantQualite",
    "label": "Registre des bénéficiaires effectifs déclarant qualité",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_DECLARANT_TELEPHONE",
    "cle": "rbeDeclarantTelephone",
    "label": "Registre des bénéficiaires effectifs déclarant téléphone",
    "type": "text",
    "options": null,
    "aide": "Déclarant : $RBE_DECLARANT_NOM, $RBE_DECLARANT_QUALITE, $RBE_DECLARANT_PIECE_TYPE, $RBE_DECLARANT_PIECE_NUMERO, $RBE_DECLARANT_TELEPHONE, $RBE_DECLARANT_EMAIL.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_MOTIF_MODIFICATION",
    "cle": "rbeMotifModification",
    "label": "Registre des bénéficiaires effectifs motif modification",
    "type": "text",
    "options": null,
    "aide": "$RBE_MOTIF_MODIFICATION, $RBE_DATE_EVENEMENT — Conditionnels, si déclaration modificative.",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RBE_NATURE_DECLARATION",
    "cle": "rbeNatureDeclaration",
    "label": "Registre des bénéficiaires effectifs nature déclaration",
    "type": "select",
    "options": [
      "Déclaration initiale",
      "Déclaration modificative",
      "Confirmation annuelle"
    ],
    "aide": "$RBE_NATURE_DECLARATION — Case à cocher : « Déclaration initiale » / « Déclaration modificative » / « Confirmation annuelle ».",
    "section": "11. Formalités postérieures à l'immatriculation — [2026-09-03]",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_DEMANDE_OBJET",
    "cle": "retraitDemandeObjet",
    "label": "Retrait demande objet",
    "type": "text",
    "options": null,
    "aide": "$RETRAIT_DEMANDE_OBJET, $RETRAIT_REGULARISATION_OBJET, $RETRAIT_PIECES_DEMANDEES — Conditionnels selon $RETRAIT_OBJET.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_OBJET",
    "cle": "retraitObjet",
    "label": "Retrait objet",
    "type": "select",
    "options": [
      "Retrait d'une demande en cours",
      "Régularisation d'un dossier déposé",
      "Restitution de pièces déposées"
    ],
    "aide": "$RETRAIT_OBJET — Case à cocher : « Retrait d'une demande en cours » / « Régularisation d'un dossier déposé » / « Restitution de pièces déposées ».",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_OBSERVATIONS",
    "cle": "retraitObservations",
    "label": "Retrait observations",
    "type": "textarea",
    "options": null,
    "aide": "$RETRAIT_OBSERVATIONS, $RETRAIT_PIECES_JOINTES — Compléments propres au dossier.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_PIECES_DEMANDEES",
    "cle": "retraitPiecesDemandees",
    "label": "Retrait pièces demandées",
    "type": "text",
    "options": null,
    "aide": "$RETRAIT_DEMANDE_OBJET, $RETRAIT_REGULARISATION_OBJET, $RETRAIT_PIECES_DEMANDEES — Conditionnels selon $RETRAIT_OBJET.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_PIECES_JOINTES",
    "cle": "retraitPiecesJointes",
    "label": "Retrait pièces jointes",
    "type": "text",
    "options": null,
    "aide": "$RETRAIT_OBSERVATIONS, $RETRAIT_PIECES_JOINTES — Compléments propres au dossier.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RETRAIT_REGULARISATION_OBJET",
    "cle": "retraitRegularisationObjet",
    "label": "Retrait régularisation objet",
    "type": "text",
    "options": null,
    "aide": "$RETRAIT_DEMANDE_OBJET, $RETRAIT_REGULARISATION_OBJET, $RETRAIT_PIECES_DEMANDEES — Conditionnels selon $RETRAIT_OBJET.",
    "section": "22. Annulation du dossier — [2026-09-09]",
    "documents": [
      "LETTRE_RETRAIT_DEPOT"
    ],
    "boucle": null
  },
  {
    "variable": "RIB",
    "cle": "rib",
    "label": "RIB",
    "type": "text",
    "options": null,
    "aide": "$RIB — **[2026-09-03]** Relevé d'identité bancaire de la société.",
    "section": "4. Capital et parts",
    "documents": [
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_DEBLOCAGE_CAPITAL"
    ],
    "boucle": null
  },
  {
    "variable": "SIEGE_TITRE_OCCUPATION",
    "cle": "siegeTitreOccupation",
    "label": "Siège titre occupation",
    "type": "select",
    "options": [
      "domiciliation",
      "bail",
      "propriété"
    ],
    "aide": "$SIEGE_TITRE_OCCUPATION — « domiciliation » / « bail » / « propriété ». Pilote le choix du contrat et les pièces exigées.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "SIEGE_TITRE_PROPRIETE",
    "cle": "siegeTitrePropriete",
    "label": "Siège titre propriété",
    "type": "text",
    "options": null,
    "aide": "$SIEGE_TITRE_PROPRIETE — Référence du titre de propriété lorsque le local appartient à la société ou à un associé.",
    "section": "8. Siège — occupation des locaux — [2026-09-03]",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_ADHESION_OBJET",
    "cle": "simplAdhesionObjet",
    "label": "SIMPL adhésion objet",
    "type": "select",
    "options": [
      "Adhésion initiale",
      "Modification de l'adhésion",
      "Résiliation de l'adhésion"
    ],
    "aide": "$SIMPL_ADHESION_OBJET — Case à cocher : « Adhésion initiale » / « Modification de l'adhésion » / « Résiliation de l'adhésion ».",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_EMAIL",
    "cle": "simplContactEmail",
    "label": "SIMPL contact courriel",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_NOM",
    "cle": "simplContactNom",
    "label": "SIMPL contact nom",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_PIECE_NUMERO",
    "cle": "simplContactPieceNumero",
    "label": "SIMPL contact pièce numéro",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_PIECE_TYPE",
    "cle": "simplContactPieceType",
    "label": "SIMPL contact pièce type",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_QUALITE",
    "cle": "simplContactQualite",
    "label": "SIMPL contact qualité",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_CONTACT_TELEPHONE",
    "cle": "simplContactTelephone",
    "label": "SIMPL contact téléphone",
    "type": "text",
    "options": null,
    "aide": "Personne habilitée : $SIMPL_CONTACT_NOM, $SIMPL_CONTACT_QUALITE, $SIMPL_CONTACT_PIECE_TYPE, $SIMPL_CONTACT_PIECE_NUMERO, $SIMPL_CONTACT_TELEPHONE, $SIMPL_CONTACT_EMAIL.",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SIMPL_TELESERVICES",
    "cle": "simplTeleservices",
    "label": "SIMPL téléservices",
    "type": "text",
    "options": null,
    "aide": "$SIMPL_TELESERVICES — Téléservices sollicités (télédéclaration, télépaiement, consultation du compte fiscal).",
    "section": "20. Téléservices fiscaux — adhésion SIMPL — [2026-09-09]",
    "documents": [
      "DEMANDE_ADHESION_SIMPL"
    ],
    "boucle": null
  },
  {
    "variable": "SITE_WEB",
    "cle": "siteWeb",
    "label": "Site web",
    "type": "text",
    "options": null,
    "aide": "$SITE_WEB — **[2026-09-09]** Adresse du site internet de la société, le cas échéant. Le site porte les mêmes mentions légales que les documents commerciaux.",
    "section": "1. Société",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "boucle": null
  },
  {
    "variable": "SUBDIVISION",
    "cle": "subdivision",
    "label": "Subdivision",
    "type": "text",
    "options": null,
    "aide": "$DIRECTION_REGIONALE, $SUBDIVISION — Services de la direction générale des impôts.",
    "section": "3. Administration compétente",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DEMANDE_ADHESION_SIMPL",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "TELEPHONE",
    "cle": "telephone",
    "label": "Téléphone",
    "type": "text",
    "options": null,
    "aide": "$TELEPHONE, $FAX, $EMAIL — Coordonnées de la société.",
    "section": "1. Société",
    "documents": [
      "DECLARATION_CNDP",
      "DECLARATION_EXISTENCE",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_ADHESION_SIMPL",
      "DEMANDE_AFFILIATION_CNSS",
      "DEMANDE_TAXE_PROFESSIONNELLE",
      "FICHE_RENSEIGNEMENTS_CREATION"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "TP_COMMUNE",
    "cle": "tpCommune",
    "label": "Taxe professionnelle commune",
    "type": "text",
    "options": null,
    "aide": "$TP_COMMUNE — **[2026-09-09]** Commune ou arrondissement de rattachement du local imposable à la taxe professionnelle.",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null
  },
  {
    "variable": "TP_OBJET",
    "cle": "tpObjet",
    "label": "Taxe professionnelle objet",
    "type": "select",
    "options": [
      "Personne morale",
      "Personne physique",
      "Autre"
    ],
    "aide": "$TP_OBJET — Case à cocher : « Personne morale » / « Personne physique » / « Autre ». $TP_OBJET_AUTRE_PRECISION complète le troisième cas.",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "TP_OBJET_AUTRE_PRECISION",
    "cle": "tpObjetAutrePrecision",
    "label": "Taxe professionnelle objet autre précision",
    "type": "textarea",
    "options": null,
    "aide": "$TP_OBJET — Case à cocher : « Personne morale » / « Personne physique » / « Autre ». $TP_OBJET_AUTRE_PRECISION complète le troisième cas.",
    "section": "14. Régime fiscal — déclaration d'existence et taxe professionnelle",
    "documents": [
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "boucle": null,
    "heritee": true
  },
  {
    "variable": "TRAITEMENT_CATEGORIES_DONNEES",
    "cle": "traitementCategoriesDonnees",
    "label": "Traitement catégories données",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_DENOMINATION",
    "cle": "traitementDenomination",
    "label": "Traitement dénomination",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_DESTINATAIRES",
    "cle": "traitementDestinataires",
    "label": "Traitement destinataires",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_DONNEES_SENSIBLES",
    "cle": "traitementDonneesSensibles",
    "label": "Traitement données sensibles",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_DONNEES_SENSIBLES_PRECISION",
    "cle": "traitementDonneesSensiblesPrecision",
    "label": "Traitement données sensibles précision",
    "type": "textarea",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_DUREE_CONSERVATION",
    "cle": "traitementDureeConservation",
    "label": "Traitement durée conservation",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_FINALITE",
    "cle": "traitementFinalite",
    "label": "Traitement finalité",
    "type": "textarea",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_MESURES_SECURITE",
    "cle": "traitementMesuresSecurite",
    "label": "Traitement mesures sécurité",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_PAYS_DESTINATAIRE",
    "cle": "traitementPaysDestinataire",
    "label": "Traitement pays destinataire",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_PERSONNES_CONCERNEES",
    "cle": "traitementPersonnesConcernees",
    "label": "Traitement personnes concernées",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_TRANSFERT_ETRANGER",
    "cle": "traitementTransfertEtranger",
    "label": "Traitement transfert étranger",
    "type": "text",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  },
  {
    "variable": "TRAITEMENT_TRANSFERT_MOTIF",
    "cle": "traitementTransfertMotif",
    "label": "Traitement transfert motif",
    "type": "textarea",
    "options": null,
    "aide": "Boucle TRAITEMENTS_DONNEES : $TRAITEMENT_NUMERO, $TRAITEMENT_DENOMINATION, $TRAITEMENT_FINALITE, $TRAITEMENT_PERSONNES_CONCERNEES, $TRAITEMENT_CATEGORIES_DONNEES, $TRAITEMENT_DONNEES_SENSIBLES (« oui » / « non »), $TRAITEMENT_DONNEES_SENSIBLES_PRECISION, $TRAITEMENT_DESTINATAIRES, $TRAITEMENT_DUREE_CONSERVATION, $TRAITEMENT_MESURES_SECURITE, $TRAITEMENT_TRANSFERT_ETRANGER (« oui » / « non »), $TRAITEMENT_PAYS_DESTINATAIRE, $TRAITEMENT_TRANSFERT_MOTIF.",
    "section": "21. Protection des données personnelles — déclaration CNDP — [2026-09-09]",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "boucle": "TRAITEMENTS_DONNEES"
  }
];

export const BOUCLES_CREATION: BoucleCreation[] = [
  {
    "nom": "ACTES_EN_FORMATION",
    "label": "Actes en formation",
    "documents": [
      "ETAT_ACTES_SOCIETE_EN_FORMATION"
    ],
    "champs": [
      "acteFormationAuteur",
      "acteFormationCocontractant",
      "acteFormationDate",
      "acteFormationEcheance",
      "acteFormationMontant",
      "acteFormationNature",
      "acteFormationObjet"
    ]
  },
  {
    "nom": "ASSOCIES",
    "label": "Associes",
    "documents": [
      "DECLARATION_IMMATRICULATION_RC"
    ],
    "champs": [
      "associeResidence"
    ]
  },
  {
    "nom": "BAIL_LOCAUX",
    "label": "Bail locaux",
    "documents": [
      "CONTRAT_BAIL",
      "DECLARATION_IMMATRICULATION_RC",
      "DEMANDE_TAXE_PROFESSIONNELLE"
    ],
    "champs": [
      "bailLocalAdresse",
      "bailLocalDesignation",
      "bailLocalSuperficie"
    ]
  },
  {
    "nom": "BENEFICIAIRES_EFFECTIFS",
    "label": "Beneficiaires effectifs",
    "documents": [
      "DECLARATION_BENEFICIAIRES_EFFECTIFS"
    ],
    "champs": [
      "beAdresse",
      "beCivilite",
      "beCritere",
      "beDateAcquisition",
      "beDateNaissance",
      "beEmail",
      "beFonction",
      "beGenre",
      "beIdentifiantFiscal",
      "beLieuNaissance",
      "beModeDetention",
      "beNationalite",
      "beNationaliteAutre",
      "beNom",
      "beObservations",
      "bePaysResidence",
      "bePieceDateDelivrance",
      "bePieceNumero",
      "bePieceType",
      "bePourcentageVote",
      "bePrenom",
      "beTelephone"
    ]
  },
  {
    "nom": "DEMARCHES_INTERROMPUES",
    "label": "Demarches interrompues",
    "documents": [
      "NOTE_ANNULATION_DOSSIER"
    ],
    "champs": [
      "demarcheSuite"
    ]
  },
  {
    "nom": "DOCUMENTS_COMMERCIAUX",
    "label": "Documents commerciaux",
    "documents": [
      "NOTE_CONFORMITE_MENTIONS_LEGALES"
    ],
    "champs": [
      "documentCommercialConformite",
      "documentCommercialDesignation",
      "documentCommercialMentionsManquantes"
    ]
  },
  {
    "nom": "ETABLISSEMENTS",
    "label": "Etablissements",
    "documents": [
      "DECLARATION_EXISTENCE",
      "DEMANDE_AFFILIATION_CNSS"
    ],
    "champs": [
      "etabEffectif"
    ]
  },
  {
    "nom": "PIECES_REMISES",
    "label": "Pièces remises",
    "documents": [
      "BORDEREAU_REMISE_DOSSIER"
    ],
    "champs": [
      "pieceForme",
      "pieceNombre",
      "pieceObservation"
    ]
  },
  {
    "nom": "TRAITEMENTS_DONNEES",
    "label": "Traitements données",
    "documents": [
      "DECLARATION_CNDP"
    ],
    "champs": [
      "traitementCategoriesDonnees",
      "traitementDenomination",
      "traitementDestinataires",
      "traitementDonneesSensibles",
      "traitementDonneesSensiblesPrecision",
      "traitementDureeConservation",
      "traitementFinalite",
      "traitementMesuresSecurite",
      "traitementPaysDestinataire",
      "traitementPersonnesConcernees",
      "traitementTransfertEtranger",
      "traitementTransfertMotif"
    ]
  }
];

/**
 * Les variables du corpus qu AUCUNE donnee du dossier ne peut alimenter :
 * donnee d'un tiers (bailleur, domiciliataire, commissaire aux apports) ou
 * delivree par une administration apres depot. Aucun champ n est ouvert pour
 * elles — c est au cabinet de dire qui les releve, et quand. Elles remontent
 * au controle de completude, document par document.
 */
export const VARIABLES_SANS_SOURCE: string[] = [
  "ANNULATION_ADMINISTRATION",
  "ANNULATION_DECISION_REFERENCE",
  "APPORT_NATURE_CHARGES",
  "APPORT_NATURE_METHODE",
  "APPORT_NATURE_ORIGINE_PROPRIETE",
  "BAILLEUR_ADRESSE",
  "BAILLEUR_CAPITAL",
  "BAILLEUR_CIVILITE",
  "BAILLEUR_DENOMINATION",
  "BAILLEUR_FORME",
  "BAILLEUR_NATIONALITE",
  "BAILLEUR_NOM",
  "BAILLEUR_PIECE_NUMERO",
  "BAILLEUR_PIECE_TYPE",
  "BAILLEUR_PRENOM",
  "BAILLEUR_RC_NUMERO",
  "BAILLEUR_RC_VILLE",
  "BAILLEUR_REPRESENTANT_NOM",
  "BAILLEUR_REPRESENTANT_QUALITE",
  "BAILLEUR_SIEGE",
  "BAILLEUR_TYPE",
  "BAIL_TITRE_FONCIER",
  "BAIL_TITRE_FONCIER_NOM",
  "BE_CHAINE_DETENTION",
  "BE_INTERMEDIAIRE_DENOMINATION",
  "BE_INTERMEDIAIRE_RC_NUMERO",
  "BE_INTERMEDIAIRE_RC_VILLE",
  "CNDP_RECEPISSE_NUMERO",
  "CNSS_PIECES_COMPLEMENT",
  "COMMISSAIRE_APPORTS_ADRESSE",
  "COMMISSAIRE_APPORTS_DATE_DESIGNATION",
  "COMMISSAIRE_APPORTS_DATE_RAPPORT",
  "COMMISSAIRE_APPORTS_DILIGENCES_COMPLEMENTAIRES",
  "COMMISSAIRE_APPORTS_LIEU",
  "COMMISSAIRE_APPORTS_MODE_DESIGNATION",
  "COMMISSAIRE_APPORTS_OBSERVATIONS",
  "COMMISSAIRE_APPORTS_QUALITE",
  "COMMISSAIRE_COMPTES_ADRESSE",
  "DEBLOCAGE_PIECES_COMPLEMENT",
  "DOMICILIATAIRE_CAPITAL",
  "DOMICILIATAIRE_DENOMINATION",
  "DOMICILIATAIRE_FORME",
  "DOMICILIATAIRE_ICE",
  "DOMICILIATAIRE_RC_NUMERO",
  "DOMICILIATAIRE_RC_VILLE",
  "DOMICILIATAIRE_REPRESENTANT_NOM",
  "DOMICILIATAIRE_REPRESENTANT_QUALITE",
  "DOMICILIATAIRE_SIEGE",
  "POUVOIR_ETENDUE_COMPLEMENT",
  "RBE_PIECES_COMPLEMENT",
  "RETRAIT_ADMINISTRATION_ADRESSE",
  "RETRAIT_ADMINISTRATION_DESIGNATION",
  "RETRAIT_DEPOT_DATE",
  "RETRAIT_DEPOT_REFERENCE",
  "SIMPL_PIECES_COMPLEMENT"
];

/**
 * Champs reellement a afficher, compte tenu des documents retenus.
 * Un champ servant plusieurs documents n apparait qu une fois, sous le premier
 * document retenu qui le reclame. Les champs de boucle en sont exclus : ils
 * s affichent par la boucle, pas un par un.
 */
export function champsParDocument(
  codesRetenus: string[],
): { code: string; champs: ChampCreation[] }[] {
  const deja = new Set<string>();
  const out: { code: string; champs: ChampCreation[] }[] = [];
  for (const code of codesRetenus) {
    const champs = CHAMPS_CREATION.filter(
      (c) => !c.boucle && c.documents.includes(code) && !deja.has(c.cle),
    );
    champs.forEach((c) => deja.add(c.cle));
    if (champs.length) out.push({ code, champs });
  }
  return out;
}

/** Boucles a afficher, compte tenu des documents retenus. */
export function bouclesParDocument(codesRetenus: string[]): BoucleCreation[] {
  const retenus = new Set(codesRetenus);
  return BOUCLES_CREATION.filter((b) => b.documents.some((d) => retenus.has(d)));
}

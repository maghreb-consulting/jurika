/**
 * Lot L6b (E10) : textes de la vitrine, hors tarifs (config/pricing.ts).
 *
 * Chaque affirmation vient du cahier des charges (CDC) ou du contrat d'abonnement V1
 * (pieces B, C, D, valide le 14 octobre 2026) ; la reference est notee a cote. Aucun
 * temoignage, aucun chiffre ni statistique, aucun logo de client, aucune promesse
 * inverifiable, aucune mention de conformite a la CNDP (la declaration est en cours).
 */

/** Sections reprises par la navigation de l'en-tete (ancres de la page d'accueil). */
export const SECTIONS_VITRINE = [
  { id: 'fonctionnalites', libelle: 'Fonctionnalités' },
  { id: 'parcours', libelle: 'Parcours' },
  { id: 'securite', libelle: 'Sécurité' },
  { id: 'tarifs', libelle: 'Tarifs' },
  { id: 'faq', libelle: 'Questions' },
] as const;

/** Section "probleme et solution" : les six roles de la plateforme (CDC 1.1). */
export const PROBLEME = {
  titre: 'Un dossier de société, aujourd’hui, est éparpillé',
  points: [
    'Les pièces, les actes et les échanges d’une même société sont répartis entre dossiers papier, courriels et fichiers.',
    'Les mêmes données sont ressaisies d’un acte à l’autre, avec le risque d’une erreur de recopie.',
    'L’avancement des démarches se reconstitue en interrogeant chacun.',
  ],
};

export const ROLES = [
  { titre: 'Digitaliser', texte: 'Saisie, production des actes, suivi des démarches, échanges avec le client et état des débours se font dans la plateforme.' },
  { titre: 'Centraliser', texte: 'Pour chaque société : ses identifiants, toutes ses opérations juridiques, ses actes et ses pièces, en un seul endroit.' },
  { titre: 'Guider', texte: 'Chaque parcours suit les référentiels de démarches du cabinet, étape par étape.' },
  { titre: 'Produire', texte: 'Les actes sont générés à partir des modèles Word validés du cabinet : à données identiques, acte identique.' },
  { titre: 'Conserver', texte: 'L’historique et les documents de chaque dossier sont rangés dans une Data Room structurée.' },
  { titre: 'Superviser', texte: 'Le responsable du cabinet voit l’avancement exact des dossiers et le travail de chaque employé.' },
];

export type IconeFonction =
  | 'parcours'
  | 'saisie'
  | 'actes'
  | 'dataroom'
  | 'client'
  | 'chatbot'
  | 'suivi'
  | 'delais'
  | 'messagerie'
  | 'edition';

export interface Fonctionnalite {
  icone: IconeFonction;
  titre: string;
  texte: string;
  /** Libelle exact de la fonction au paragraphe 4.5 (config/pricing.ts), pour le forfait requis. */
  fonction4_5?: string;
}

/** Fonctionnalites (CDC 4.5, 5 a 12 ; P2, P3, P4, P7). */
export const FONCTIONNALITES: Fonctionnalite[] = [
  {
    icone: 'parcours',
    titre: 'Parcours guidés',
    texte: 'Chaque opération est un ticket qui suit son parcours, de la collecte des informations à la clôture du dossier ; les démarches se cochent au fil du déroulement.',
    fonction4_5: 'Workflows complets et génération des actes',
  },
  {
    icone: 'saisie',
    titre: 'Saisie unique et lecture des pièces',
    texte: 'Une donnée n’est saisie qu’une fois. La CIN et le certificat négatif sont lus automatiquement : les valeurs lues vous sont présentées pour confirmation.',
    fonction4_5: 'Extraction automatique des pièces (CIN, certificat négatif)',
  },
  {
    icone: 'actes',
    titre: 'Actes générés sans intelligence artificielle',
    texte: 'Statuts, procès-verbaux et formulaires sont produits au format Word à partir des modèles du cabinet. Si une donnée manque, la plateforme la nomme au lieu de produire un acte incomplet.',
  },
  {
    icone: 'dataroom',
    titre: 'Data Room par dossier',
    texte: 'Quatre sections : dossier juridique, dépôts du client, demandes du client et requêtes du cabinet. Une seule version en vigueur à chaque emplacement ; les versions remplacées restent consultables.',
    fonction4_5: 'Data Room et accès client',
  },
  {
    icone: 'client',
    titre: 'Accès du client à son seul dossier',
    texte: 'Le client ne voit que les documents que vous lui rendez visibles, dans la limite des permissions accordées : consultation, téléchargement, dépôt, demandes.',
  },
  {
    icone: 'chatbot',
    titre: 'Chatbot juridique aux réponses sourcées',
    texte: 'Une aide juridique générale qui répond à partir de sources fiables et les cite. Il n’a accès à aucun dossier ; une question qui contient une donnée personnelle est bloquée avant l’envoi.',
    fonction4_5: 'Chatbot juridique aux réponses sourcées',
  },
  {
    icone: 'suivi',
    titre: 'Suivi et traçabilité',
    texte: 'Chaque action porte son auteur, sa date et son heure. Fiche client, fiche dossier et état des débours sont établis à partir des données du dossier.',
  },
  {
    icone: 'delais',
    titre: 'Alertes de délais légaux',
    texte: 'Un délai n’est suivi que si son point de départ est connu : aucune date n’est inventée, et la plateforme indique la donnée qui manque.',
    fonction4_5: 'Alertes de délais légaux',
  },
  {
    icone: 'messagerie',
    titre: 'Messagerie interne',
    texte: 'Le superviseur et les employés du cabinet échangent dans la plateforme ; un message est enregistré dès son envoi.',
    fonction4_5: 'Messagerie interne',
  },
  {
    icone: 'edition',
    titre: 'Édition intégrée et tableaux de bord',
    texte: 'Les actes se modifient directement dans la plateforme ; les tableaux de bord présentent la charge, l’avancement et la productivité de l’équipe.',
    fonction4_5: 'Édition des actes intégrée à la plateforme',
  },
];

/**
 * Parcours couverts : CDC 16.1, et types de tickets de l'application (types/ticket.ts :
 * CREATION, MODIFICATION, PV_AGO, SUCCURSALE_MA, FERMETURE_SUCCURSALE, SUCCURSALE_ETR,
 * DISSOLUTION, LIQUIDATION, IMPORT ; incidents d'assemblee : IncidentSeancePanel).
 */
export const PARCOURS = [
  { titre: 'Création de SARL et de SARL à associé unique', texte: 'De la dénomination au dépôt : statuts, formulaires et démarches de constitution.' },
  { titre: 'Modifications et vie sociale', texte: 'Décisions modifiant la société, y compris les opérations diverses.' },
  { titre: 'Comptes, résultat et distributions', texte: 'Approbation des comptes, affectation du résultat et assemblée générale ordinaire.' },
  { titre: 'Succursale d’une société mère marocaine', texte: 'Ouverture, vie et fermeture de la succursale.' },
  { titre: 'Succursale au Maroc d’une société mère étrangère', texte: 'Ouverture de la succursale au Maroc.' },
  { titre: 'Dissolution et liquidation', texte: 'De la décision de dissolution à la clôture de la liquidation.' },
  { titre: 'Incidents d’assemblée', texte: 'Constat de défaut de quorum et irrégularité de convocation, dans tout parcours qui tient une assemblée.' },
  { titre: 'Import de dossier', texte: 'Reprise d’une société déjà suivie par le cabinet, avec ses données, pour y ouvrir les opérations suivantes.' },
];

export const FORMES_COUVERTES =
  'Formes couvertes : SARL et SARL à associé unique, en droit marocain (loi n° 5-96, Code de commerce, Code général des impôts).';

export type IconeSecurite = 'authentification' | 'cloisonnement' | 'droits' | 'journal' | 'sauvegarde' | 'chiffrement' | 'proprietaire' | 'ia';

/** Securite et confidentialite : contrat V1 (articles 3.2, 8.1, 8.2, 8.4, 10 ; piece C). */
export const SECURITE: { icone: IconeSecurite; titre: string; texte: string }[] = [
  { icone: 'authentification', titre: 'Double authentification', texte: 'Obligatoire pour tous les comptes du cabinet ; les identifiants sont personnels.' },
  { icone: 'cloisonnement', titre: 'Cloisonnement des cabinets', texte: 'Les données d’un cabinet ne sont jamais accessibles à un autre cabinet.' },
  { icone: 'droits', titre: 'Droits vérifiés par le serveur', texte: 'Chaque rôle (superviseur, employé, client) n’agit que dans ses droits, contrôlés côté serveur et pas seulement à l’écran.' },
  { icone: 'journal', titre: 'Journalisation des actions', texte: 'Toute action porte son auteur, sa date et son heure, et reste consultable.' },
  { icone: 'sauvegarde', titre: 'Sauvegardes quotidiennes', texte: 'Données et documents sauvegardés chaque jour ; les sauvegardes sont testées par restauration.' },
  { icone: 'chiffrement', titre: 'Échanges chiffrés', texte: 'Les échanges avec la plateforme sont chiffrés (HTTPS).' },
  { icone: 'proprietaire', titre: 'Vos données vous appartiennent', texte: 'Le cabinet reste seul propriétaire de ses données et peut les exporter en totalité, y compris après l’arrêt de son forfait.' },
  { icone: 'ia', titre: 'Aucun entraînement d’IA', texte: 'Les données confiées par le cabinet ne servent jamais à entraîner un modèle d’intelligence artificielle ; le chatbot ne reçoit aucune donnée personnelle.' },
];

export const HEBERGEMENT =
  'La plateforme est hébergée chez Amazon Web Services, à Paris (Union européenne). L’hébergement hors du Maroc est réalisé sous réserve de l’autorisation de transfert de la CNDP, obtenue par l’éditeur avant la mise en production.';

export const CNDP_STATUT = 'Déclaration CNDP en cours';

export const DISPONIBILITE =
  'Objectif de 99,5 % de disponibilité en heures ouvrées. Un engagement de service contractuel n’est prévu que pour le forfait Sur Devis.';

/** Questions frequentes : reponses tirees du CDC et du contrat V1, rien d'ajoute. */
export const FAQ: { q: string; a: string }[] = [
  {
    q: 'Les actes sont-ils rédigés par une intelligence artificielle ?',
    a: 'Non. Les actes sont générés à partir des modèles validés du cabinet : à données identiques, acte identique. Aucune intelligence artificielle ne rédige un acte. Le cabinet relit chaque acte et en reste seul responsable avant sa signature, son dépôt ou sa remise à un client.',
  },
  {
    q: 'Quelles sociétés et quelles opérations sont couvertes ?',
    a: 'Les SARL et les SARL à associé unique : création, modifications et vie sociale, comptes et assemblée générale ordinaire, succursales, dissolution et liquidation, incidents d’assemblée et import de dossier.',
  },
  {
    q: 'Où sont hébergées les données ?',
    a: 'Chez Amazon Web Services, à Paris (Union européenne). L’hébergement hors du Maroc est soumis à l’autorisation de transfert de la CNDP, que l’éditeur obtient avant la mise en production ; la déclaration auprès de la CNDP est en cours.',
  },
  {
    q: 'Le chatbot a-t-il accès à mes dossiers ?',
    a: 'Non. Le chatbot donne des informations juridiques générales à partir de sources sélectionnées, qu’il cite. Il n’a accès à aucun dossier, et une question qui contient une donnée personnelle (CIN, ICE, RC, identifiant fiscal, téléphone, adresse électronique, RIB, date de naissance) est bloquée avant l’envoi. Ses réponses ne constituent pas une consultation juridique.',
  },
  {
    q: 'Comment fonctionne l’essai gratuit ?',
    a: 'L’essai dure 7 jours, sans moyen de paiement, un essai par cabinet. Il donne accès aux fonctions du forfait Professionnel, dans la limite de 3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage et 20 questions au chatbot. Aucun téléchargement n’est permis et les actes portent la mention « VERSION D’ÉVALUATION — SANS VALEUR JURIDIQUE ». L’essai ne doit pas servir à traiter de vrais dossiers de clients. Si vous souscrivez, vous conservez tout votre travail.',
  },
  {
    q: 'Comment se règle un forfait ?',
    a: 'Le forfait est souscrit pour un an, prix hors taxes. Paiement annuel : un seul règlement pour l’année. Paiement mensuel : douze mensualités égales au dixième du prix annuel. Les modes de paiement prévus sont la carte bancaire en ligne, le virement, le chèque et les espèces ; chaque paiement donne lieu à une facture électronique, consultable sur la plateforme.',
  },
  {
    q: 'Puis-je changer de forfait ?',
    a: 'Oui. Un forfait supérieur s’applique dès la confirmation du paiement ; vous réglez la différence au prorata de la période restante. Un forfait inférieur s’applique à la prochaine échéance, sans remboursement de la période en cours, si votre usage tient dans le forfait choisi.',
  },
  {
    q: 'Comment les dossiers sont-ils comptés ?',
    a: 'Un dossier juridique est une société suivie par le cabinet. Il est compté une seule fois dans le mois où un ticket y est ouvert, quel que soit le nombre de tickets. À l’atteinte du quota, la création de nouveaux dossiers ou l’ajout de documents est bloqué jusqu’à l’adaptation du forfait ; les dossiers et documents existants restent accessibles.',
  },
  {
    q: 'Que deviennent mes données si j’arrête mon forfait ?',
    a: 'Tout votre travail est archivé, jamais supprimé sans votre demande expresse. Les accès sont fermés, mais le superviseur peut toujours se connecter pour exporter l’intégralité des données ou en demander la suppression définitive. Si vous souscrivez de nouveau, votre espace est réactivé dans l’état exact où vous l’avez laissé.',
  },
  {
    q: 'Que se passe-t-il en cas de violation de données ?',
    a: 'L’éditeur informe le cabinet de toute violation de données personnelles dans les meilleurs délais, et au plus tard 72 heures après en avoir eu connaissance, en précisant sa nature, ses conséquences probables et les mesures prises.',
  },
  {
    q: 'Quelle disponibilité est prévue ?',
    a: 'L’objectif est de 99,5 % de disponibilité en heures ouvrées. Il s’agit d’un objectif ; un engagement de service contractuel n’est prévu que pour le forfait Sur Devis, dans les conditions de son offre.',
  },
];

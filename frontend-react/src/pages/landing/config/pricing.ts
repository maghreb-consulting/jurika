/**
 * Offre commerciale affichee par la vitrine : SEULE source des tarifs de la vitrine
 * (lot L7 E3, reprise au lot L6b E10).
 *
 * Source : cahier des charges, paragraphe 4 (il fait foi), et contrat d'abonnement V1.
 *  - 4.3 grille tarifaire : prix HT, paiement annuel en une fois ou mensuel en douze
 *    mensualites (dixieme du prix annuel, RG-TAR-02), quotas ;
 *  - 4.5 differenciation fonctionnelle par forfait ;
 *  - 4.4 socle commun a tous les forfaits ;
 *  - 4.2 essai gratuit de 7 jours (piece D du contrat V1).
 * Aucun chiffre ni fonction absent du cahier des charges ne figure ici.
 *
 * L6b E10 : la "connexion par l'annuaire du cabinet (authentification unique)" du 4.4 n'est
 * plus annoncee : elle n'existe ni dans le code ni dans le contrat V1 (article 8.4), elle
 * releve du lot Securite de la feuille de route (CDC 17, n. 8). Elle reviendra a sa livraison.
 */

export type IdForfait = 'essentiel' | 'professionnel' | 'max' | 'sur-devis';

export interface Forfait {
  id: IdForfait;
  label: string;
  prixAnnuelHt: number | null;
  prixMensuelHt: number | null;
  dossiers: string;
  utilisateurs: string;
  stockage: string;
}

export const FORFAITS: Forfait[] = [
  {
    id: 'essentiel',
    label: 'Essentiel',
    prixAnnuelHt: 3750,
    prixMensuelHt: 375,
    dossiers: "Jusqu'à 25 dossiers juridiques par mois",
    utilisateurs: '1 superviseur et 1 accès employé',
    stockage: '5 Go de stockage',
  },
  {
    id: 'professionnel',
    label: 'Professionnel',
    prixAnnuelHt: 7500,
    prixMensuelHt: 750,
    dossiers: "Jusqu'à 50 dossiers juridiques par mois",
    utilisateurs: '1 superviseur et 2 accès employés',
    stockage: '10 Go de stockage',
  },
  {
    id: 'max',
    label: 'Max',
    prixAnnuelHt: 15000,
    prixMensuelHt: 1500,
    dossiers: "Jusqu'à 100 dossiers juridiques par mois",
    utilisateurs: '1 superviseur et 4 accès employés',
    stockage: '25 Go de stockage',
  },
  {
    id: 'sur-devis',
    label: 'Sur Devis',
    prixAnnuelHt: null,
    prixMensuelHt: null,
    dossiers: 'Plus de 100 dossiers juridiques par mois',
    utilisateurs: 'Utilisateurs selon devis',
    stockage: 'Stockage selon devis',
  },
];

export interface Fonction {
  libelle: string;
  forfaits: IdForfait[];
}

/** Paragraphe 4.5 : chaque fonction et les forfaits qui la comprennent. */
export const FONCTIONS: Fonction[] = [
  { libelle: 'Workflows complets et génération des actes', forfaits: ['essentiel', 'professionnel', 'max', 'sur-devis'] },
  { libelle: 'Data Room et accès client', forfaits: ['essentiel', 'professionnel', 'max', 'sur-devis'] },
  { libelle: 'Chatbot juridique aux réponses sourcées', forfaits: ['essentiel', 'professionnel', 'max', 'sur-devis'] },
  { libelle: 'Extraction automatique des pièces (CIN, certificat négatif)', forfaits: ['essentiel', 'professionnel', 'max', 'sur-devis'] },
  { libelle: 'Alertes de délais légaux', forfaits: ['professionnel', 'max', 'sur-devis'] },
  { libelle: 'Messagerie interne', forfaits: ['professionnel', 'max', 'sur-devis'] },
  { libelle: 'Tableaux de bord de productivité', forfaits: ['max', 'sur-devis'] },
  { libelle: 'Édition des actes intégrée à la plateforme', forfaits: ['max', 'sur-devis'] },
  { libelle: "Rapports d'activité", forfaits: ['max', 'sur-devis'] },
  { libelle: 'Engagement de service contractuel et support prioritaire', forfaits: ['sur-devis'] },
];

export function fonctionsDuForfait(id: IdForfait): string[] {
  return FONCTIONS.filter((f) => f.forfaits.includes(id)).map((f) => f.libelle);
}

/**
 * Premier forfait (dans l'ordre de la grille) qui comprend une fonction, ou null si tous la
 * comprennent. Sert aux mentions "a partir du forfait ..." de la section des fonctionnalites :
 * elles derivent de cette table, jamais d'un texte recopie.
 */
export function forfaitMinimal(libelle: string): Forfait | null {
  const f = FONCTIONS.find((x) => x.libelle === libelle);
  if (!f) throw new Error(`Fonction absente du paragraphe 4.5 : ${libelle}`);
  if (f.forfaits.length === FORFAITS.length) return null;
  return FORFAITS.find((x) => f.forfaits.includes(x.id)) ?? null;
}

/**
 * Paragraphe 4.4 et contrat V1 (articles 8.1, 8.4, 10.2 ; piece C, article 3) : garanties
 * communes a tous les forfaits. La protection des donnees est annoncee avec l'etat de la
 * declaration aupres de la CNDP (en cours chez la direction) : aucune mention de conformite
 * tant que la declaration n'est pas faite.
 */
export const SOCLE_COMMUN: string[] = [
  'Chiffrement des échanges (HTTPS)',
  'Double authentification',
  'Cloisonnement strict des cabinets',
  'Droits par rôle vérifiés côté serveur',
  'Traçabilité des actions',
  'Sauvegardes quotidiennes, testées par restauration',
  'Export complet des données du cabinet (dossiers et documents)',
  'Protection des données personnelles selon la loi 09-08 (déclaration auprès de la CNDP en cours)',
];

/** Paragraphe 4.2 (RG-SOU-02, RG-ESS-04 a RG-ESS-10) et piece D du contrat V1. */
export const ESSAI = {
  dureeJours: 7,
  conditions: [
    'Sans moyen de paiement, un essai par cabinet',
    'Fonctions du forfait Professionnel',
    '3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage, 20 questions au chatbot',
    'Aucun téléchargement : les documents se consultent en aperçu, les actes portent la mention « VERSION D’ÉVALUATION — SANS VALEUR JURIDIQUE »',
    'En souscrivant, vous conservez tout votre travail',
  ],
  /** Piece D, points 4 et 6 ; RG-ESS-08 (rappels). */
  reserves: [
    "L'essai sert à découvrir la plateforme : il ne doit pas être utilisé pour traiter de vrais dossiers de clients.",
    'Sans souscription au terme des 7 jours, l’espace passe en lecture seule pendant 30 jours, puis ses données sont supprimées définitivement, après avertissement.',
    'Des rappels s’affichent dans l’application trois jours, puis un jour avant la fin de l’essai.',
  ],
};

/** Montant en dirhams avec separateur de milliers (espace insecable). */
export function formatPrice(value: number): string {
  return value.toLocaleString('fr-FR').replace(/[\u202f\u00a0 ]/g, '\u00a0');
}

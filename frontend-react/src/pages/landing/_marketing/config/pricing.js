/**
 * Offre commerciale affichee par la vitrine (lot L7, E3).
 *
 * Source : cahier des charges, paragraphe 4 (il fait foi).
 *  - 4.3 grille tarifaire : prix HT, paiement annuel en une fois ou mensuel en douze
 *    mensualites (dixieme du prix annuel, RG-TAR-02), quotas ;
 *  - 4.5 differenciation fonctionnelle par forfait ;
 *  - 4.4 socle commun a tous les forfaits ;
 *  - 4.2 essai gratuit de 7 jours.
 * Aucun chiffre ni fonction absent du cahier des charges ne figure ici.
 */

export const FORFAITS = [
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

/** Paragraphe 4.5 : chaque fonction et les forfaits qui la comprennent. */
export const FONCTIONS = [
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

export function fonctionsDuForfait(id) {
  return FONCTIONS.filter((f) => f.forfaits.includes(id)).map((f) => f.libelle);
}

/**
 * Paragraphe 4.4 : garanties communes a tous les forfaits. La conformite a la loi 09-08
 * est annoncee avec l'etat de la declaration aupres de la CNDP (en cours chez la direction) :
 * aucune mention "conforme CNDP" tant que la declaration n'est pas faite.
 */
export const SOCLE_COMMUN = [
  'Chiffrement des échanges (HTTPS)',
  'Double authentification',
  "Connexion par l'annuaire du cabinet (authentification unique), pour tout cabinet qui en dispose",
  'Cloisonnement strict des cabinets',
  'Droits par rôle vérifiés côté serveur',
  'Traçabilité des actions',
  'Sauvegardes quotidiennes, testées par restauration',
  'Export complet des données du cabinet (dossiers et documents)',
  'Protection des données personnelles selon la loi 09-08 (déclaration auprès de la CNDP en cours)',
];

/** Paragraphe 4.2 (RG-SOU-02, RG-ESS-04 a RG-ESS-07, RG-ESS-10). */
export const ESSAI = {
  dureeJours: 7,
  conditions: [
    'Sans moyen de paiement, un essai par cabinet',
    'Fonctions du forfait Professionnel',
    '3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage, 20 questions au chatbot',
    'Aucun téléchargement : les documents se consultent en aperçu, les actes portent la mention « VERSION D’ÉVALUATION — SANS VALEUR JURIDIQUE »',
    'En souscrivant, vous conservez tout votre travail',
  ],
};

/** Montant en dirhams avec separateur de milliers (espace insecable fine). */
export function formatPrice(value) {
  return value.toLocaleString('fr-FR').replace(/[\u202f\u00a0 ]/g, '\u00a0');
}

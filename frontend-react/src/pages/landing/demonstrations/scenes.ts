/**
 * Lot L6c (E1, demande du 2026-10-17) : demonstrations animees de la vitrine.
 *
 * Elles remplacent les cinq videos du lot L6b : chaque scene est construite en HTML avec les
 * VRAIS composants de l'application (components/ui, frise des demarches, frise des etapes,
 * indicateurs du tableau de bord, JURI), alimentes par des donnees de demonstration FIXES et
 * fictives (donnees.ts). Aucune video, aucune image animee, aucune requete reseau.
 *
 * Chaque scene joue en boucle une courte histoire (curseur simule, saisie, changement d'etat),
 * en etapes de durees fixes (`durees`, en millisecondes). Elle ne joue qu'a l'ecran, onglet
 * visible ; sous "reduire les animations", elle montre son etat final (`etapeFinale`), fixe.
 * Elle n'est pas interactive : role="img" et sa description (`description`) pour les
 * lecteurs d'ecran, contenu aria-hidden et inert.
 */

export const NOMS_SCENES = ['creation-ticket', 'frise-demarches', 'statuts-apercu', 'juri-aide', 'theme-clair-sombre'] as const;

export type NomScene = (typeof NOMS_SCENES)[number];

/** Taille de dessin des scenes (ratio 16:10, comme les captures) ; mise a l'echelle du cadre. */
export const DESSIN_SCENE = { largeur: 800, hauteur: 500 } as const;

export interface Scene {
  /** Nom de l'ecran, affiche dans la barre du cadre. */
  ecran: string;
  /** Texte equivalent : ce que montre la scene, en francais (role="img", aria-label). */
  description: string;
  /** Legende courte, affichee sous la scene. */
  legende: string;
  /** Duree de chaque etape, en millisecondes (la derniere ramene au debut). */
  durees: readonly number[];
  /** Etape montree, fixe, quand les animations sont reduites. */
  etapeFinale: number;
}

export const SCENES: Record<NomScene, Scene> = {
  'creation-ticket': {
    ecran: 'Nouveau ticket et parcours de création',
    description:
      'Démonstration animée, données fictives : un employé ouvre un ticket de création de SARL pour la société fictive OASIS EXEMPLE SARL, puis arrive sur la première étape du parcours, « Dénomination », avec l’aide de l’étape affichée.',
    legende: 'Ouvrir un ticket de création de SARL, puis suivre l’aide de la première étape.',
    durees: [900, 1000, 900, 1700, 900, 1600, 1100, 4200, 700],
    etapeFinale: 7,
  },
  'frise-demarches': {
    ecran: 'Ticket et frise des démarches',
    description:
      'Démonstration animée, données fictives : sur la page d’un ticket, l’employé coche la démarche en retard « Enregistrement des statuts au service de l’enregistrement (DGI), dépôt » ; dans la frise des démarches, elle passe de « Dépassée » à « Accomplie ».',
    legende: 'Cocher une démarche : la frise du ticket se met à jour aussitôt.',
    durees: [700, 1200, 900, 4200, 700],
    etapeFinale: 3,
  },
  'statuts-apercu': {
    ecran: 'Génération des actes',
    description:
      'Démonstration animée, données fictives : à l’étape « Génération des actes », l’employé génère les statuts de la société fictive OASIS EXEMPLE SARL à partir du modèle du cabinet, puis ouvre l’aperçu de l’acte.',
    legende: 'Générer les statuts à partir du modèle du cabinet, puis ouvrir l’aperçu de l’acte.',
    durees: [700, 1000, 1300, 1200, 400, 4400, 700],
    etapeFinale: 5,
  },
  'juri-aide': {
    ecran: 'JURI, l’assistant de l’écran',
    description:
      'Démonstration animée, données fictives : au survol, JURI sort du bord droit du tableau de bord, puis ouvre son panneau avec l’aide de l’écran, la visite guidée et les raccourcis clavier.',
    legende: 'JURI sort du bord de l’écran et ouvre l’aide de l’écran en cours.',
    durees: [1200, 1300, 4600, 700],
    etapeFinale: 2,
  },
  'theme-clair-sombre': {
    ecran: 'Tableau de bord, thème clair et sombre',
    description:
      'Démonstration animée, données fictives : depuis le choix du thème de l’en-tête, le tableau de bord d’un employé passe du thème clair au thème sombre, puis revient au thème clair. Animations réduites : les deux thèmes côte à côte.',
    legende: 'Le même tableau de bord, en thème clair et en thème sombre : chacun choisit le sien.',
    durees: [1000, 900, 800, 700, 2400, 800, 700, 700, 1500],
    etapeFinale: 0,
  },
};

/** Duree d'un tour complet de la scene, en millisecondes. */
export function dureeCycle(nom: NomScene): number {
  return SCENES[nom].durees.reduce((a, b) => a + b, 0);
}

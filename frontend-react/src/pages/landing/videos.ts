/**
 * Lot L6b (E10, V4, demande du 2026-10-16) : courtes videos en boucle de la vitrine.
 *
 * Enregistrees avec Playwright (1280 x 800) sur des donnees FICTIVES : la pile de
 * demonstration (cabinet JUR-DEMO2, compte d'employe de demonstration) ou le harnais visuel
 * du lot (e2e/visuel : API simulee, cabinet "Atlas Conseil" invente ; l'acte montre est celui
 * que le moteur documentaire produit sur les donnees fictives de ses tests). Encodees par
 * Chromium (MediaRecorder) en MP4 (H.264) et WebM (VP9), sans son, de 6 a 12 secondes,
 * moins de 1,5 Mo chacune ; image fixe (poster) en WebP : la premiere image de la video.
 *
 * Fichiers : public/vitrine/videos/<nom>.mp4, .webm, .webp. Le test videosFournies.test.ts
 * verifie leur presence, leur poids, leur duree et l'absence de piste son.
 */

export const NOMS_VIDEOS = ['creation-ticket', 'statuts-apercu', 'frise-demarches', 'juri-aide', 'theme-clair-sombre'] as const;

export type NomVideo = (typeof NOMS_VIDEOS)[number];

/** Dimensions intrinseques (ratio 16:10, comme les captures) : la place est reservee. */
export const DIMENSIONS_VIDEO = { largeur: 1280, hauteur: 800 } as const;

/** Limites de la demande du 2026-10-16. */
export const LIMITES_VIDEO = { dureeMinS: 6, dureeMaxS: 12, poidsMaxOctets: 1_500_000 } as const;

export interface Video {
  /** Nom de l'ecran, affiche dans la barre du cadre. */
  ecran: string;
  /** Texte equivalent : ce que montre la video, en francais (aria-label). */
  alt: string;
  /** Legende courte, affichee sous la video. */
  legende: string;
  /** Provenance de l'enregistrement (pour le rapport et la relecture). */
  source: 'pile de demonstration' | 'harnais visuel';
}

export const VIDEOS: Record<NomVideo, Video> = {
  'creation-ticket': {
    ecran: 'Nouveau ticket et parcours de création',
    alt: 'Vidéo : un employé ouvre un ticket de création de SARL pour une société fictive, puis arrive sur la première étape du parcours, avec l’aide de l’étape affichée.',
    legende: 'Ouvrir un ticket de création de SARL, puis suivre l’aide de la première étape.',
    source: 'pile de demonstration',
  },
  'statuts-apercu': {
    ecran: 'Génération des actes',
    alt: 'Vidéo : à l’étape de génération des actes, l’employé génère les statuts d’une SARL fictive, puis ouvre l’aperçu de l’acte en plein écran.',
    legende: 'Générer les statuts à partir du modèle du cabinet, puis ouvrir l’aperçu de l’acte.',
    source: 'harnais visuel',
  },
  'frise-demarches': {
    ecran: 'Ticket et frise des démarches',
    alt: 'Vidéo : sur la page d’un ticket, l’employé coche une démarche en retard ; dans la frise des démarches, elle passe de « Dépassée » à « Accomplie ».',
    legende: 'Cocher une démarche : la frise du ticket se met à jour aussitôt.',
    source: 'harnais visuel',
  },
  'juri-aide': {
    ecran: 'JURI, l’assistant de l’écran',
    alt: 'Vidéo : au survol, JURI sort du bord droit de l’écran, puis ouvre son panneau avec l’aide du tableau de bord.',
    legende: 'JURI sort du bord de l’écran et ouvre l’aide de l’écran en cours.',
    source: 'pile de demonstration',
  },
  'theme-clair-sombre': {
    ecran: 'Tableau de bord, thème clair et sombre',
    alt: 'Vidéo : depuis le menu du thème, le tableau de bord de l’employé passe du thème clair au thème sombre, puis revient au thème clair.',
    legende: 'Le même tableau de bord, en thème clair et en thème sombre : chacun choisit le sien.',
    source: 'pile de demonstration',
  },
};

export const FORMATS_VIDEO = ['mp4', 'webm', 'webp'] as const;

export function cheminVideo(nom: NomVideo, format: (typeof FORMATS_VIDEO)[number]): string {
  return `/vitrine/videos/${nom}.${format}`;
}

/** Les quinze fichiers attendus dans public/vitrine/videos. */
export function fichiersVideos(): string[] {
  return NOMS_VIDEOS.flatMap((n) => FORMATS_VIDEO.map((f) => `${n}.${f}`));
}

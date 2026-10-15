/**
 * Lot L6b (E10, V2) : captures de la plateforme montrees par la vitrine.
 *
 * Ce sont de VRAIES captures d'ecran de JURIKA (aucune maquette, aucune donnee inventee
 * dessinee a la main), deposees en WebP dans public/vitrine, chacune en deux largeurs :
 * <nom>-800.webp et <nom>-1600.webp. Le test capturesFournies.test.ts verifie leur presence.
 *
 * Ratio d'affichage fixe (16:10) : la boite de l'image est reservee avant son chargement
 * (aspect-ratio, width et height), aucun decalage de la mise en page.
 */

export const NOMS_CAPTURES = [
  'tableau-de-bord-employe-clair',
  'tableau-de-bord-employe-sombre',
  'ticket-frise-clair',
  'data-room-clair',
  'apercu-acte-clair',
  'juri-chatbot-clair',
] as const;

export type NomCapture = (typeof NOMS_CAPTURES)[number];

export const LARGEURS_CAPTURE = [800, 1600] as const;

/** Dimensions intrinseques declarees (largeur 1600, ratio 16:10). */
export const DIMENSIONS_CAPTURE = { largeur: 1600, hauteur: 1000 } as const;

export interface Capture {
  /** Texte de remplacement : ce que montre l'ecran, en francais. */
  alt: string;
  /** Nom de l'ecran, affiche dans la barre du cadre. */
  ecran: string;
}

export const CAPTURES: Record<NomCapture, Capture> = {
  'tableau-de-bord-employe-clair': {
    ecran: 'Tableau de bord de l’employé',
    alt: 'Capture du tableau de bord de l’employé dans JURIKA, en thème clair.',
  },
  'tableau-de-bord-employe-sombre': {
    ecran: 'Tableau de bord de l’employé',
    alt: 'Capture du même tableau de bord de l’employé dans JURIKA, en thème sombre.',
  },
  'ticket-frise-clair': {
    ecran: 'Ticket et frise des démarches',
    alt: 'Capture de la page d’un ticket dans JURIKA, avec la frise des démarches de l’opération.',
  },
  'data-room-clair': {
    ecran: 'Data Room du dossier',
    alt: 'Capture de la Data Room d’un dossier dans JURIKA, avec ses sections et ses documents.',
  },
  'apercu-acte-clair': {
    ecran: 'Aperçu d’un acte',
    alt: 'Capture de l’aperçu d’un acte généré par JURIKA à partir d’un modèle du cabinet.',
  },
  'juri-chatbot-clair': {
    ecran: 'Chatbot juridique',
    alt: 'Capture du chatbot juridique de JURIKA, ouvert depuis l’assistant JURI.',
  },
};

export function cheminCapture(nom: NomCapture, largeur: (typeof LARGEURS_CAPTURE)[number]): string {
  return `/vitrine/${nom}-${largeur}.webp`;
}

/** Les douze fichiers attendus dans public/vitrine. */
export function fichiersCaptures(): string[] {
  return NOMS_CAPTURES.flatMap((n) => LARGEURS_CAPTURE.map((l) => `${n}-${l}.webp`));
}

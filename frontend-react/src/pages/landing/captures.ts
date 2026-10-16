/**
 * Lot L6b (E10, V2) : captures de la plateforme montrees par la vitrine.
 *
 * Ce sont de VRAIES captures d'ecran de JURIKA (aucune maquette, aucune donnee inventee
 * dessinee a la main), deposees en WebP dans public/vitrine, chacune en deux largeurs :
 * <nom>-800.webp et <nom>-1600.webp. Le test capturesFournies.test.ts verifie leur presence.
 *
 * V4 (demande du 2026-10-16) : les autres ecrans (tableau de bord clair et sombre, ticket,
 * apercu d'un acte, JURI) sont montres par des videos en boucle (videos.ts) ; leurs captures
 * fixes sont retirees. Seule la Data Room, sans video, garde sa capture.
 *
 * Ratio d'affichage fixe (16:10) : la boite de l'image est reservee avant son chargement
 * (aspect-ratio, width et height), aucun decalage de la mise en page.
 */

export const NOMS_CAPTURES = ['data-room-clair'] as const;

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
  'data-room-clair': {
    ecran: 'Data Room du dossier',
    alt: 'Capture de la Data Room d’un dossier dans JURIKA, avec ses sections et ses documents.',
  },
};

export function cheminCapture(nom: NomCapture, largeur: (typeof LARGEURS_CAPTURE)[number]): string {
  return `/vitrine/${nom}-${largeur}.webp`;
}

/** Les fichiers attendus dans public/vitrine (deux largeurs par capture). */
export function fichiersCaptures(): string[] {
  return NOMS_CAPTURES.flatMap((n) => LARGEURS_CAPTURE.map((l) => `${n}-${l}.webp`));
}

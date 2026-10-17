/**
 * Lot L6b (E10, V2) : captures de la plateforme montrees par la vitrine.
 *
 * Ce sont de VRAIES captures d'ecran de JURIKA (aucune maquette, aucune donnee inventee
 * dessinee a la main), deposees en WebP dans public/vitrine, chacune en deux largeurs :
 * <nom>-800.webp et <nom>-1600.webp. Le test capturesFournies.test.ts verifie leur presence.
 *
 * V4 (demande du 2026-10-16) : les autres ecrans (tableau de bord clair et sombre, ticket,
 * apercu d'un acte, JURI) sont montres par des videos en boucle (L6c : demonstrations animees,
 * demonstrations/scenes.ts) ; leurs captures
 * fixes sont retirees. Seule la Data Room, sans video, garde sa capture.
 *
 * Ratio d'affichage fixe (16:10) : la boite de l'image est reservee avant son chargement
 * (aspect-ratio, width et height), aucun decalage de la mise en page.
 *
 * L6c (demande du 2026-10-17) : captures prises en haute definition (deviceScaleFactor 2),
 * servies en AVIF puis en WebP (element <picture>), chacune en deux largeurs (800 et 1600) ;
 * le navigateur prend le premier format qu'il sait lire, a la largeur utile. Script de
 * regeneration : frontend-react/scripts/vitrine-captures.mjs puis vitrine-avif.py.
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

/** Formats servis, dans l'ordre de preference du <picture> (le dernier est celui de l'<img>). */
export const FORMATS_CAPTURE = ['avif', 'webp'] as const;

export type FormatCapture = (typeof FORMATS_CAPTURE)[number];

export function cheminCapture(nom: NomCapture, largeur: (typeof LARGEURS_CAPTURE)[number], format: FormatCapture = 'webp'): string {
  return `/vitrine/${nom}-${largeur}.${format}`;
}

/** Jeu de sources d'un format (descripteurs de largeur). */
export function sourcesCapture(nom: NomCapture, format: FormatCapture): string {
  return LARGEURS_CAPTURE.map((l) => `${cheminCapture(nom, l, format)} ${l}w`).join(', ');
}

/** Les fichiers attendus dans public/vitrine (deux largeurs et deux formats par capture). */
export function fichiersCaptures(): string[] {
  return NOMS_CAPTURES.flatMap((n) => FORMATS_CAPTURE.flatMap((f) => LARGEURS_CAPTURE.map((l) => `${n}-${l}.${f}`)));
}

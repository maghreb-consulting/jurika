/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : apercu d'un document en AFFICHAGE seulement.
 * Les pages sont des images PNG produites par le serveur ; pendant l'essai, la mention
 * d'evaluation y est incrustee. Aucun fichier du document n'est transmis au navigateur.
 */
export interface PageApercu {
  numero: number;
  largeur: number;
  hauteur: number;
  /** Image PNG en URL de donnees. */
  image: string;
}

export interface ApercuPages {
  filigrane: boolean;
  mention: string | null;
  nombrePages: number;
  tronque: boolean;
  pages: PageApercu[];
}

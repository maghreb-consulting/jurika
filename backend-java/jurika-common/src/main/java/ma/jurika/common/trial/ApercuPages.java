package ma.jurika.common.trial;

import java.util.List;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : apercu d'un document en AFFICHAGE seulement.
 * Les pages sont des images PNG produites par le serveur ; pendant l'essai, la mention
 * {@link EssaiRegles#MENTION_FILIGRANE} y est incrustee (dans les pixels : le navigateur ne peut
 * pas la retirer). Aucun octet du document d'origine n'est transmis.
 *
 * @param filigrane   vrai si la mention de l'essai est incrustee sur chaque page
 * @param mention     texte de la mention incrustee, ou null sans filigrane
 * @param nombrePages nombre total de pages du document rendu
 * @param tronque     vrai si seules les {@link RenduApercu#MAX_PAGES} premieres pages sont rendues
 * @param pages       pages rendues, dans l'ordre
 */
public record ApercuPages(boolean filigrane, String mention, int nombrePages, boolean tronque, List<Page> pages) {

    /**
     * @param numero  numero de la page (a partir de 1)
     * @param largeur largeur de l'image, en pixels
     * @param hauteur hauteur de l'image, en pixels
     * @param image   image PNG en URL de donnees ({@code data:image/png;base64,...})
     */
    public record Page(int numero, int largeur, int hauteur, String image) {}
}

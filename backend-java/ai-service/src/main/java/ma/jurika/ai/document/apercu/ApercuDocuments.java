package ma.jurika.ai.document.apercu;

import ma.jurika.common.trial.ApercuPages;
import ma.jurika.common.trial.RenduApercu;

import java.util.Locale;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : apercu d'un document en pages PNG produites
 * par le serveur, la mention de l'essai incrustee sur chaque page quand {@code filigrane}.
 *
 * <ul>
 *   <li>.docx : mis en page par {@link MiseEnPageDocx} (sans LibreOffice), puis rendu ;</li>
 *   <li>PDF : rendu par PDFBox ;</li>
 *   <li>image (PNG, JPEG, GIF, BMP) : redessinee par le serveur ;</li>
 *   <li>tout autre format (.doc, .xlsx, .odt...) : refus nomme ({@link FormatSansApercuException}) ;
 *       jamais de repli sur le fichier d'origine.</li>
 * </ul>
 */
public final class ApercuDocuments {

    private ApercuDocuments() {}

    public static ApercuPages acte(byte[] docx, boolean filigrane) {
        return RenduApercu.depuisPdf(MiseEnPageDocx.enPdf(docx), filigrane);
    }

    public static ApercuPages fichier(byte[] octets, String nom, String type, boolean filigrane) {
        if (octets == null || octets.length == 0) {
            throw new RenduApercu.ApercuImpossibleException("Le document est vide : aucun aper\u00e7u.", null);
        }
        String n = nom == null ? "" : nom.toLowerCase(Locale.ROOT);
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (n.endsWith(".docx") || t.contains("wordprocessingml")) return acte(octets, filigrane);
        if (n.endsWith(".pdf") || t.startsWith("application/pdf") || commencePar(octets, "%PDF")) {
            return RenduApercu.depuisPdf(octets, filigrane);
        }
        if (t.startsWith("image/") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp")) {
            return RenduApercu.depuisImage(octets, filigrane);
        }
        throw new FormatSansApercuException();
    }

    private static boolean commencePar(byte[] octets, String prefixe) {
        if (octets.length < prefixe.length()) return false;
        for (int i = 0; i < prefixe.length(); i++) if (octets[i] != prefixe.charAt(i)) return false;
        return true;
    }

    /** Format sans apercu par le serveur (ni .docx, ni PDF, ni image). */
    public static class FormatSansApercuException extends RenduApercu.ApercuImpossibleException {
        public FormatSansApercuException() {
            super("Aper\u00e7u indisponible pour ce format : seuls les documents Word (.docx), les PDF et les "
                    + "images s\u2019affichent dans la plateforme.", null);
        }
    }
}

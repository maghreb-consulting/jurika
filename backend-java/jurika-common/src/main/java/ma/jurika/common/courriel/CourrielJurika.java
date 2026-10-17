package ma.jurika.common.courriel;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recette R3 : composition commune de tous les courriels de JURIKA (auth-service, billing-service).
 *
 * <p>Defaut corrige : le motif de zellige (branding/jurika-zellij.png) etait joint en partie
 * "en ligne" (Content-ID jurika-zellij) mais AUCUN gabarit ne le referencait (le commentaire
 * parlait d'un background-image:url('cid:...'), que Gmail ignore de toute facon) : une partie en
 * ligne orpheline, que Gmail et Outlook affichent comme piece jointe PNG. En outre la racine etait
 * multipart/mixed et il n'y avait pas de version texte brut.
 *
 * <p>Structure produite :
 * <ul>
 *   <li>sans piece jointe : multipart/related [ multipart/alternative [ text/plain, text/html ],
 *       image/png du logo (Content-Disposition inline, Content-ID {@value #LOGO_CID}) ] ;</li>
 *   <li>avec piece jointe (PDF d'une facture, d'un contrat) : multipart/mixed [ la structure
 *       ci-dessus, la piece en attachment ].</li>
 * </ul>
 * Le logo n'est joint que si le HTML le reference ({@code src="cid:logo-jurika"}) ; toute autre
 * image en ligne et toute image de fond CSS ({@code url(...)}) sont refusees : le decor (bandeaux,
 * frise de zellige) est fait en HTML et couleurs, sans image.
 */
public final class CourrielJurika {

    /** Content-ID du logo, reference par la mise en page commune (templates/email/_mise-en-page.html). */
    public static final String LOGO_CID = "logo-jurika";
    /** Logo Moucharabieh (variante ivoire) en PNG 3x, regenere par scripts/courriels/generer-logo-courriel.mjs. */
    public static final String LOGO_RESSOURCE = "branding/courriel/logo-jurika-courriel.png";
    /** Cote d'affichage du logo dans le courriel (pixels CSS) ; le fichier fait 3 fois plus. */
    public static final int LOGO_COTE = 56;

    public record PieceJointe(String nom, byte[] contenu, String type) {}

    private static final Pattern CID = Pattern.compile("cid:([^\"'()\\s>]+)", Pattern.CASE_INSENSITIVE);

    private CourrielJurika() {}

    /**
     * Remplit {@code message} : expediteur (avec nom affiche), destinataire, objet, texte brut
     * derive du HTML, HTML, logo en ligne s'il est reference, pieces jointes.
     *
     * @throws IllegalArgumentException si le HTML reference une image en ligne autre que le logo,
     *                                  ou une image de fond CSS
     * @throws IllegalStateException    si le logo est reference mais absent du classpath
     */
    public static void composer(MimeMessage message, String expediteur, String nomExpediteur, String destinataire,
                                String objet, String html, List<PieceJointe> pieces)
            throws MessagingException, UnsupportedEncodingException {
        boolean logo = false;
        Matcher m = CID.matcher(html);
        while (m.find()) {
            if (!LOGO_CID.equals(m.group(1))) {
                throw new IllegalArgumentException("Image en ligne inconnue dans le courriel : cid:" + m.group(1));
            }
            logo = true;
        }
        String minuscules = html.toLowerCase(Locale.ROOT);
        if (minuscules.contains("url(") || minuscules.contains(" background=\"")) {
            throw new IllegalArgumentException("Image de fond interdite dans un courriel (url( ou background=) : "
                    + "le decor se fait en HTML et couleurs");
        }
        boolean avecPieces = pieces != null && !pieces.isEmpty();
        MimeMessageHelper helper = new MimeMessageHelper(message,
                avecPieces ? MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED : MimeMessageHelper.MULTIPART_MODE_RELATED,
                StandardCharsets.UTF_8.name());
        if (nomExpediteur == null || nomExpediteur.isBlank()) {
            helper.setFrom(expediteur);
        } else {
            helper.setFrom(expediteur, nomExpediteur);
        }
        helper.setTo(destinataire);
        helper.setSubject(objet);
        helper.setText(TexteBrut.depuisHtml(html), html);
        if (logo) {
            ClassPathResource res = new ClassPathResource(LOGO_RESSOURCE);
            if (!res.exists()) {
                throw new IllegalStateException("Logo des courriels absent du classpath : " + LOGO_RESSOURCE);
            }
            helper.addInline(LOGO_CID, res, "image/png");
        }
        if (avecPieces) {
            for (PieceJointe p : pieces) {
                helper.addAttachment(p.nom(), new ByteArrayResource(p.contenu()), p.type());
            }
        }
    }
}

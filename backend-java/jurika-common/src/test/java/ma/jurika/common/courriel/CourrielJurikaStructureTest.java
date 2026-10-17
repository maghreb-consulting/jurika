package ma.jurika.common.courriel;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recette R3 : "le decor de zellige arrive en piece jointe PNG". Structure MIME attendue de tout
 * courriel de JURIKA :
 * <ul>
 *   <li>sans piece jointe : racine multipart/related ; une partie multipart/alternative (texte
 *       brut + HTML) ; une seule image, le logo, en ligne (Content-Disposition inline) avec un
 *       Content-ID que le HTML reference ; aucune image de decor ;</li>
 *   <li>avec piece jointe (facture, contrat) : racine multipart/mixed ; seule la piece (PDF) est
 *       en attachment ;</li>
 *   <li>aucune partie en ligne orpheline : une image jointe sans reference dans le HTML est
 *       affichee en piece jointe par Gmail et Outlook (la cause du defaut).</li>
 * </ul>
 */
class CourrielJurikaStructureTest {

    private static final String HTML = "<html><body><img src=\"cid:" + CourrielJurika.LOGO_CID
            + "\" alt=\"JURIKA\" width=\"56\" height=\"56\"><p>Bonjour Karim,</p>"
            + "<p>Votre cabinet vous &eacute;crit.</p><a href=\"https://exemple.test/x\">Ouvrir</a></body></html>";

    private static MimeMessage composer(String html, List<CourrielJurika.PieceJointe> pieces) throws Exception {
        MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
        CourrielJurika.composer(m, "expediteur@exemple.test", "JURIKA", "dest@exemple.test", "Objet", html, pieces);
        m.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        m.writeTo(out);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(out.toByteArray()));
    }

    /** Parties feuilles (non multipart) du message, dans l'ordre. */
    static List<Part> feuilles(Part p) throws Exception {
        List<Part> l = new ArrayList<>();
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) l.addAll(feuilles(mp.getBodyPart(i)));
        } else {
            l.add(p);
        }
        return l;
    }

    static List<String> typesMultipart(Part p) throws Exception {
        List<String> l = new ArrayList<>();
        if (p.isMimeType("multipart/*")) {
            l.add(p.getContentType().split(";")[0].trim().toLowerCase());
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) l.addAll(typesMultipart(mp.getBodyPart(i)));
        }
        return l;
    }

    @Test
    void sans_piece_jointe_racine_related_logo_en_ligne_reference_et_texte_brut() throws Exception {
        MimeMessage m = composer(HTML, List.of());
        assertThat(m.getContentType()).startsWith("multipart/related");
        assertThat(typesMultipart(m)).containsExactly("multipart/related", "multipart/alternative");

        List<Part> f = feuilles(m);
        List<Part> images = f.stream().filter(p -> { try { return p.isMimeType("image/*"); } catch (Exception e) { return false; } }).toList();
        assertThat(images).as("une seule image : le logo").hasSize(1);
        Part logo = images.get(0);
        assertThat(logo.isMimeType("image/png")).isTrue();
        assertThat(logo.getDisposition()).isEqualToIgnoringCase(Part.INLINE);
        assertThat(((BodyPart) logo).getHeader("Content-ID")[0]).isEqualTo("<" + CourrielJurika.LOGO_CID + ">");

        String html = null;
        String texte = null;
        for (Part p : f) {
            if (p.isMimeType("text/html")) html = (String) p.getContent();
            if (p.isMimeType("text/plain")) texte = (String) p.getContent();
            assertThat(p.getDisposition()).as("aucune partie en attachment").isNotEqualToIgnoringCase(Part.ATTACHMENT);
        }
        assertThat(html).contains("src=\"cid:" + CourrielJurika.LOGO_CID + "\"");
        assertThat(texte).as("version texte brut").isNotBlank()
                .contains("Bonjour Karim,").contains("Votre cabinet vous \u00e9crit.")
                .contains("https://exemple.test/x")
                .doesNotContain("<").doesNotContain("&eacute;");
    }

    @Test
    void avec_un_pdf_seul_le_pdf_est_en_piece_jointe() throws Exception {
        byte[] pdf = "%PDF-1.4 exemple".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MimeMessage m = composer(HTML, List.of(new CourrielJurika.PieceJointe("Facture-F-2026-0001.pdf", pdf, "application/pdf")));
        assertThat(m.getContentType()).startsWith("multipart/mixed");
        assertThat(typesMultipart(m)).containsExactly("multipart/mixed", "multipart/related", "multipart/alternative");
        List<Part> jointes = new ArrayList<>();
        for (Part p : feuilles(m)) {
            if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) jointes.add(p);
        }
        assertThat(jointes).hasSize(1);
        assertThat(jointes.get(0).isMimeType("application/pdf")).isTrue();
        assertThat(jointes.get(0).getFileName()).isEqualTo("Facture-F-2026-0001.pdf");
    }

    @Test
    void sans_reference_au_logo_aucune_image_n_est_jointe() throws Exception {
        MimeMessage m = composer("<html><body><p>Texte seul</p></body></html>", List.of());
        for (Part p : feuilles(m)) {
            assertThat(p.isMimeType("image/*")).as("aucune image orpheline").isFalse();
        }
    }

    @Test
    void une_image_en_ligne_inconnue_est_refusee() {
        String html = HTML + "<img src=\"cid:jurika-zellij\" alt=\"\">";
        assertThatThrownBy(() -> composer(html, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jurika-zellij");
    }

    @Test
    void une_image_de_fond_css_est_refusee() {
        String html = HTML.replace("<p>", "<p style=\"background-image:url('x.png')\">");
        assertThatThrownBy(() -> composer(html, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url(");
    }

    @Test
    void expediteur_avec_le_nom_affiche() throws Exception {
        MimeMessage m = composer(HTML, List.of());
        assertThat(m.getFrom()[0].toString()).isEqualTo("JURIKA <expediteur@exemple.test>");
    }
}

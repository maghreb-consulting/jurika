package ma.jurika.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5b (E3, RG-FIC-08) : charte des documents de synthese -- logo Moucharabieh valide,
 * couleurs lues dans le SVG du logo, decor, rendu deterministe, poids raisonnable.
 */
class JurikaPdfCharteTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 10, 13);

    private static byte[] exemple(int lignes) {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument().dateEdition(JOUR);
        doc.header(CabinetIdentity.resolve(null, "Cabinet Alaoui & Associ\u00e9s", null, null,
                        "12, rue des Consultants, Casablanca", "+212 5 22 00 00 00", "contact@cabinet.ma",
                        null, "002345678000089", "RC-45219", "12345678"),
                "Document de synth\u00e8se", "Soci\u00e9t\u00e9 \u00c9toile d'Or SARL", "\u00c9tat au 13/10/2026");
        doc.sectionTitle("1", "Identifiants de la soci\u00e9t\u00e9");
        doc.keyValues(List.of(
                new String[]{"D\u00e9nomination", "\u00c9toile d'Or SARL", "Parcours de cr\u00e9ation"},
                new String[]{"Si\u00e8ge social", "45, boulevard Zerktouni, Casablanca"}));
        PdfTable t = new PdfTable()
                .column("N\u00b0", 0.6f, PdfTable.Align.LEFT)
                .column("Libell\u00e9", 3f, PdfTable.Align.LEFT)
                .column("Montant (DH)", 1.4f, PdfTable.Align.RIGHT);
        for (int i = 1; i <= lignes; i++) {
            t.row(PdfTable.Cell.of(String.valueOf(i)), PdfTable.Cell.of("Frais de greffe \u00e0 pr\u00e9voir " + i),
                    PdfTable.Cell.of("1\u00a0250,00"));
        }
        doc.table(t);
        doc.paragraph("Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat \u00e0 la somme de mille deux cent cinquante dirhams.");
        doc.paragraph("Reprise \u00e0 l\u2019import du dossier ; \u0153uvre, \u00e7a, \u00ee, \u00fb, \u00eb.");
        return doc.finish();
    }

    @Test
    void memes_donnees_memes_octets() {
        assertThat(exemple(3)).isEqualTo(exemple(3));
        assertThat(exemple(60)).isEqualTo(exemple(60));
    }

    @Test
    void la_date_d_edition_est_obligatoire_le_pdf_ne_lit_pas_l_horloge() {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument();
        doc.header(CabinetIdentity.ofName(null, "Cabinet"), "Titre", null, null);
        assertThatThrownBy(doc::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proprietes_figees_sur_la_date_d_edition() throws Exception {
        try (PDDocument d = Loader.loadPDF(exemple(1))) {
            assertThat(d.getDocumentInformation().getCreationDate().toInstant().toString())
                    .isEqualTo("2026-10-13T00:00:00Z");
            assertThat(d.getDocumentInformation().getModificationDate().toInstant().toString())
                    .isEqualTo("2026-10-13T00:00:00Z");
            assertThat(d.getDocumentInformation().getProducer()).isEqualTo("JURIKA");
            assertThat(d.getDocument().getDocumentID()).isNotNull();
        }
    }

    @Test
    void texte_accentue_exact_et_pied_de_page() throws Exception {
        String t;
        try (PDDocument d = Loader.loadPDF(exemple(3))) {
            t = new PDFTextStripper().getText(d);
        }
        assertThat(t).contains("Document de synth\u00e8se", "Soci\u00e9t\u00e9 \u00c9toile d'Or SARL",
                "D\u00c9NOMINATION", "Parcours de cr\u00e9ation", "Frais de greffe \u00e0 pr\u00e9voir 1",
                "Arr\u00eat\u00e9 le pr\u00e9sent \u00e9tat", "\u00c9dit\u00e9 par JURIKA le 13/10/2026",
                "Page 1 / 1", "ICE : 002345678000089",
                "Reprise \u00e0 l'import du dossier ; \u0153uvre, \u00e7a, \u00ee, \u00fb, \u00eb.");
        // L'apostrophe typographique ne ressort pas en U+02BC (copier-coller faux).
        assertThat(t).doesNotContain("\u02bc");
    }

    @Test
    void logo_moucharabieh_est_le_fichier_valide() throws Exception {
        try (InputStream in = LogoMoucharabieh.class.getResourceAsStream(LogoMoucharabieh.RESSOURCE)) {
            assertThat(in).isNotNull();
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            // ~/docs/logo/3-moucharabieh/logo-horizontal-clair.png
            assertThat(sha).isEqualTo("d6748db13fe93549323afd02d45e71da00a2ae0bcbc8da516f3bfe6a96b43b10");
        }
        assertThat(LogoMoucharabieh.image().getWidth()).isEqualTo(1180);
        assertThat(LogoMoucharabieh.image().getHeight()).isEqualTo(300);
    }

    @Test
    void couleurs_lues_dans_le_svg_du_logo() {
        assertThat(JurikaPdfTheme.NAVY).isEqualTo(new Color(0x0c1d3f));
        assertThat(JurikaPdfTheme.GOLD).isEqualTo(new Color(0xa8853d));
        assertThat(JurikaPdfTheme.GOLD_LINE).isEqualTo(new Color(0xc9a24a));
        assertThat(JurikaPdfTheme.GOLD_SOFT).isEqualTo(new Color(0xd4b06a));
        assertThat(JurikaPdfTheme.NAVY_SOFT).isEqualTo(new Color(0x16325f));
    }

    @Test
    void pdf_leger_et_pagination_avec_decor_sur_chaque_page() throws Exception {
        byte[] court = exemple(3);
        byte[] long_ = exemple(60);
        assertThat(court.length).isLessThan(400_000);
        try (PDDocument d = Loader.loadPDF(long_)) {
            assertThat(d.getNumberOfPages()).isGreaterThan(1);
            String t = new PDFTextStripper().getText(d);
            assertThat(t).contains("Page 2 / " + d.getNumberOfPages());
            // Decor : de l'or dans le bandeau haut et dans le bandeau bas de chaque page,
            // aucun pixel de decor dans la zone de texte (marges respectees).
            PDFRenderer r = new PDFRenderer(d);
            for (int p = 0; p < d.getNumberOfPages(); p++) {
                var img = r.renderImageWithDPI(p, 72, ImageType.RGB);
                assertThat(contientDeLOr(img, 812)).as("frise de zellige page %d", p + 1).isTrue();
                assertThat(contientDeLOr(img, 842 - 25)).as("treillis page %d", p + 1).isTrue();
            }
        }
        sortie("exemple-charte-1-page", court);
        sortie("exemple-charte-plusieurs-pages", long_);
    }

    /** Une ligne horizontale (en points depuis le haut) porte-t-elle un pixel dore ? */
    private static boolean contientDeLOr(java.awt.image.BufferedImage img, int ligne) {
        for (int x = 60; x < img.getWidth() - 60; x++) {
            Color c = new Color(img.getRGB(x, ligne));
            if (c.getRed() > c.getBlue() + 25 && c.getRed() < 250) return true;
        }
        return false;
    }

    /** Range le PDF et ses pages en PNG pour relecture (target/output/l5b-pdf). */
    static List<File> sortie(String nom, byte[] pdf) throws Exception {
        File dir = new File("target/output/l5b-pdf");
        dir.mkdirs();
        List<File> out = new ArrayList<>();
        File f = new File(dir, nom + ".pdf");
        Files.write(f.toPath(), pdf);
        out.add(f);
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFRenderer r = new PDFRenderer(d);
            for (int p = 0; p < d.getNumberOfPages(); p++) {
                File png = new File(dir, nom + "-p" + (p + 1) + ".png");
                ImageIO.write(r.renderImageWithDPI(p, 110, ImageType.RGB), "png", png);
                out.add(png);
                // Impression en niveaux de gris, et detail de l'en-tete a 300 dpi.
                File gris = new File(dir, nom + "-p" + (p + 1) + "-gris.png");
                ImageIO.write(r.renderImageWithDPI(p, 110, ImageType.GRAY), "png", gris);
                out.add(gris);
                if (p == 0) {
                    File detail = new File(dir, nom + "-p1-entete-300dpi.png");
                    ImageIO.write(r.renderImageWithDPI(0, 300, ImageType.RGB).getSubimage(150, 100, 1300, 550),
                            "png", detail);
                    out.add(detail);
                }
            }
        }
        return out;
    }
}

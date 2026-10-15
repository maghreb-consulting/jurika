package ma.jurika.ai.document.apercu;

import ma.jurika.ai.document.FiligraneEssaiDocx;
import ma.jurika.common.trial.ApercuPages;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.RenduApercu;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : l'apercu d'un acte .docx est compose par le
 * serveur SANS LibreOffice, rendu en pages PNG lisibles, la mention de l'essai incrustee sur
 * chaque page ; le rendu est deterministe ; hors essai, aucune mention.
 */
class ApercuDocumentsTest {

    static byte[] acte() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Cabinet Essai \u2014 en-t\u00eate");
            XWPFParagraph titre = doc.createParagraph();
            titre.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = titre.createRun();
            r.setBold(true);
            r.setFontSize(16);
            r.setText("PROC\u00c8S-VERBAL DE L\u2019ASSEMBL\u00c9E G\u00c9N\u00c9RALE EXTRAORDINAIRE");
            for (int i = 1; i <= 40; i++) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.BOTH);
                p.setSpacingAfter(120);
                XWPFRun a = p.createRun();
                a.setBold(true);
                a.setText("Article " + i + " \u2014 ");
                XWPFRun b = p.createRun();
                b.setText("L\u2019associ\u00e9 unique d\u00e9cide de transf\u00e9rer le si\u00e8ge social de la soci\u00e9t\u00e9 "
                        + "et de modifier corr\u00e9lativement les statuts ; la pr\u00e9sente r\u00e9solution est adopt\u00e9e "
                        + "\u00e0 l\u2019unanimit\u00e9 des voix pr\u00e9sentes ou repr\u00e9sent\u00e9es.");
            }
            XWPFTable t = doc.createTable(3, 2);
            t.getRow(0).getCell(0).setText("Associ\u00e9");
            t.getRow(0).getCell(1).setText("Parts sociales");
            t.getRow(1).getCell(0).setText("M. Ahmed BENANI");
            t.getRow(1).getCell(1).setText("1 000");
            t.getRow(2).getCell(0).setText("Mme Salma ALAOUI");
            t.getRow(2).getCell(1).setText("500");
            XWPFParagraph saut = doc.createParagraph();
            saut.setPageBreak(true);
            saut.createRun().setText("Fait \u00e0 Casablanca, le 14 octobre 2026.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    static BufferedImage image(ApercuPages.Page p) throws Exception {
        byte[] png = Base64.getDecoder().decode(p.image().substring("data:image/png;base64,".length()));
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    static long pixels(BufferedImage img, boolean rouge) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                boolean estRouge = c.getRed() - c.getGreen() > 25 && c.getRed() - c.getBlue() > 25;
                boolean estNoir = c.getRed() < 80 && c.getGreen() < 80 && c.getBlue() < 80;
                if (rouge ? estRouge : estNoir) n++;
            }
        }
        return n;
    }

    @Test
    void la_mise_en_page_garde_le_texte_de_l_acte_sans_libreoffice() throws Exception {
        byte[] pdf = MiseEnPageDocx.enPdf(acte());
        try (PDDocument d = Loader.loadPDF(pdf)) {
            String texte = new PDFTextStripper().getText(d).replaceAll("\\s+", " ");
            assertThat(texte).contains("PROC\u00c8S-VERBAL DE L").contains("ASSEMBL\u00c9E G\u00c9N\u00c9RALE")
                    .contains("Article 40")
                    .contains("M. Ahmed BENANI")
                    .contains("Fait \u00e0 Casablanca")
                    .contains("Cabinet Essai");
            // 40 articles et un saut de page explicite : plusieurs pages.
            assertThat(d.getNumberOfPages()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void essai_chaque_page_de_l_acte_porte_la_mention_et_reste_lisible() throws Exception {
        ApercuPages a = ApercuDocuments.acte(acte(), true);
        assertThat(a.filigrane()).isTrue();
        assertThat(a.mention()).isEqualTo(EssaiRegles.MENTION_FILIGRANE);
        assertThat(a.pages()).hasSize(a.nombrePages());
        for (ApercuPages.Page p : a.pages()) {
            BufferedImage img = image(p);
            assertThat(pixels(img, true)).as("mention, page " + p.numero()).isGreaterThan(5_000);
            assertThat(pixels(img, false)).as("texte, page " + p.numero()).isGreaterThan(200);
        }
        Path sortie = Path.of("target", "apercu-essai");
        Files.createDirectories(sortie);
        ImageIO.write(image(a.pages().get(0)), "png", sortie.resolve("acte-page1-essai.png").toFile());
    }

    @Test
    void hors_essai_aucune_mention() throws Exception {
        ApercuPages a = ApercuDocuments.acte(acte(), false);
        assertThat(a.filigrane()).isFalse();
        assertThat(pixels(image(a.pages().get(0)), true)).isZero();
    }

    @Test
    void memes_donnees_meme_rendu() throws Exception {
        byte[] docx = acte();
        assertThat(ApercuDocuments.acte(docx, true)).isEqualTo(ApercuDocuments.acte(docx, true));
    }

    @Test
    void acte_reel_du_classpath_marque_pour_l_essai_rendu_en_pages() throws Exception {
        byte[] gabarit;
        try (InputStream in = getClass().getResourceAsStream("/templates/docx/PV_APPROBATION_COMPTES_SARL.docx")) {
            assertThat(in).as("gabarit du classpath").isNotNull();
            gabarit = in.readAllBytes();
        }
        ApercuPages a = ApercuDocuments.acte(FiligraneEssaiDocx.appliquer(gabarit), true);
        assertThat(a.pages()).isNotEmpty();
        assertThat(pixels(image(a.pages().get(0)), false)).isGreaterThan(500);
        Path sortie = Path.of("target", "apercu-essai");
        Files.createDirectories(sortie);
        for (ApercuPages.Page p : a.pages()) {
            ImageIO.write(image(p), "png", sortie.resolve("pv-approbation-page" + p.numero() + ".png").toFile());
        }
    }

    @Test
    void format_sans_apercu_refuse_et_jamais_rendu_tel_quel() {
        assertThatThrownBy(() -> ApercuDocuments.fichier("PK\u0003\u0004".getBytes(), "budget.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true))
                .isInstanceOf(ApercuDocuments.FormatSansApercuException.class)
                .hasMessageContaining("Aper\u00e7u indisponible");
        assertThatThrownBy(() -> ApercuDocuments.acte("pas un docx".getBytes(), true))
                .isInstanceOf(RenduApercu.ApercuImpossibleException.class);
    }
}

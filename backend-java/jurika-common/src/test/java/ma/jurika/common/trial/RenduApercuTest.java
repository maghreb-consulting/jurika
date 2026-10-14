package ma.jurika.common.trial;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : l'apercu est un affichage (pages PNG produites
 * par le serveur), la mention de l'essai est incrustee dans les pixels de CHAQUE page, et le rendu
 * est deterministe ; hors essai, aucune mention.
 */
class RenduApercuTest {

    static byte[] pdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Statuts de la societe, page " + i);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    static BufferedImage image(ApercuPages.Page p) throws Exception {
        assertThat(p.image()).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(p.image().substring("data:image/png;base64,".length()));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(p.largeur());
        assertThat(img.getHeight()).isEqualTo(p.hauteur());
        return img;
    }

    /** Pixels nettement rouges (la mention) ; le texte du document est noir. */
    static long pixelsRouges(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() - c.getGreen() > 25 && c.getRed() - c.getBlue() > 25) n++;
            }
        }
        return n;
    }

    @Test
    void essai_chaque_page_porte_la_mention_incrustee() throws Exception {
        ApercuPages a = RenduApercu.depuisPdf(pdf(3), true);
        assertThat(a.filigrane()).isTrue();
        assertThat(a.mention()).isEqualTo(EssaiRegles.MENTION_FILIGRANE);
        assertThat(a.nombrePages()).isEqualTo(3);
        assertThat(a.tronque()).isFalse();
        assertThat(a.pages()).hasSize(3);
        for (ApercuPages.Page p : a.pages()) {
            BufferedImage img = image(p);
            // A4 a 110 ppp : 909 x 1286 pixels.
            assertThat(img.getWidth()).isBetween(900, 920);
            assertThat(pixelsRouges(img)).as("page " + p.numero()).isGreaterThan(5_000);
        }
        // Page rendue conservee pour la verification a l'ecran.
        Path sortie = Path.of("target", "apercu-essai");
        Files.createDirectories(sortie);
        ImageIO.write(image(a.pages().get(0)), "png", sortie.resolve("pdf-page1-essai.png").toFile());
    }

    @Test
    void hors_essai_aucune_mention() throws Exception {
        ApercuPages a = RenduApercu.depuisPdf(pdf(1), false);
        assertThat(a.filigrane()).isFalse();
        assertThat(a.mention()).isNull();
        assertThat(pixelsRouges(image(a.pages().get(0)))).isZero();
    }

    @Test
    void rendu_deterministe() throws Exception {
        byte[] source = pdf(2);
        assertThat(RenduApercu.depuisPdf(source, true)).isEqualTo(RenduApercu.depuisPdf(source, true));
    }

    @Test
    void image_deposee_rendue_par_le_serveur_avec_la_mention() throws Exception {
        BufferedImage photo = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 2000, 1000);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(photo, "jpeg", jpeg);
        ApercuPages a = RenduApercu.depuisImage(jpeg.toByteArray(), true);
        BufferedImage page = image(a.pages().get(0));
        assertThat(page.getWidth()).isEqualTo(RenduApercu.LARGEUR_MAX_IMAGE);
        assertThat(pixelsRouges(page)).isGreaterThan(1_000);
        // Les octets du fichier depose ne sont pas rendus tels quels.
        assertThat(a.pages().get(0).image()).doesNotContain(Base64.getEncoder().encodeToString(jpeg.toByteArray()));
    }

    @Test
    void document_illisible_refuse_sans_repli() {
        assertThatThrownBy(() -> RenduApercu.depuisPdf("pas un pdf".getBytes(), true))
                .isInstanceOf(RenduApercu.ApercuImpossibleException.class);
        assertThatThrownBy(() -> RenduApercu.depuisImage("pas une image".getBytes(), true))
                .isInstanceOf(RenduApercu.ApercuImpossibleException.class);
    }
}

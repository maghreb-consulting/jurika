package ma.jurika.common.trial;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L7 (E4), RG-ESS-06 : filigrane sur chaque page, sortie deterministe. */
class FiligraneEssaiPdfTest {

    private static byte[] pdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(i % 2 == 0 ? new PDRectangle(842, 595) : PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 500);
                    cs.showText("Statuts de la societe, page " + i);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void chaque_page_porte_la_mention() throws Exception {
        byte[] marque = FiligraneEssaiPdf.appliquer(pdf(3));
        try (PDDocument doc = Loader.loadPDF(marque)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
            for (int p = 1; p <= 3; p++) {
                PDFTextStripper t = new PDFTextStripper();
                t.setStartPage(p);
                t.setEndPage(p);
                // Le texte incline est extrait avec des espaces parasites : comparaison sans blancs.
                String texte = t.getText(doc).replaceAll("\\s+", "");
                assertThat(texte).as("page %d", p)
                        .contains(EssaiRegles.MENTION_FILIGRANE.replace(" ", ""))
                        .contains("page" + p);
            }
        }
    }

    @Test
    void memes_octets_en_entree_memes_octets_en_sortie() throws Exception {
        byte[] source = pdf(2);
        assertThat(FiligraneEssaiPdf.appliquer(source)).isEqualTo(FiligraneEssaiPdf.appliquer(source));
    }
}

package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de non-régression « gabarit-first » (audit 2026-06-19) — verrouillent la
 * règle universelle : <b>la typographie vient du gabarit .docx, jamais d'une passe
 * de post-traitement</b>. Le moteur ne doit pas toucher police / taille / gras /
 * alignement des runs et paragraphes existants ; il se contente de substituer les
 * placeholders {@code ${...}} / {@code {{...}}}.
 *
 * <p>Branche {@code fix/typographie-gabarit-first} — remplace
 * {@code fix/statuts-mise-en-page} et {@code fix/document-typography} (régressions
 * TNR). Voir {@code output/AUDIT_MISE_EN_PAGE_STATUTS_2026-06-19.md}.
 */
class DocxTemplateGabaritFirstTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    @Test
    void page_de_titre_centree_16pt_gras_preservee_apres_substitution() throws Exception {
        byte[] input = buildDocx(doc -> {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = p.createRun();
            r.setBold(true);
            r.setFontSize(16);
            r.setText("${DENOMINATION}");
        });

        byte[] output = engine.render(input, "titre.docx",
                Map.of("DENOMINATION", "JURIKA DIRECTEUR SARL")).bytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(output))) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            assertEquals(ParagraphAlignment.CENTER, p.getAlignment(),
                    "alignement CENTER de la page de titre doit etre preserve");
            XWPFRun r = p.getRuns().get(0);
            assertTrue(r.isBold(), "gras de la page de titre doit etre preserve");
            assertEquals(16, r.getFontSize(), "taille 16 pt de la page de titre doit etre preservee");
            assertEquals("JURIKA DIRECTEUR SARL", r.text(),
                    "le placeholder doit avoir ete substitue");
        }
    }

    @Test
    void corps_justifie_10pt_calibri_preserve_apres_substitution() throws Exception {
        byte[] input = buildDocx(doc -> {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.BOTH);
            XWPFRun r = p.createRun();
            r.setFontFamily("Calibri");
            r.setFontSize(10);
            r.setText("Capital : ${CAPITAL} MAD.");
        });

        byte[] output = engine.render(input, "corps.docx",
                Map.of("CAPITAL", "300000")).bytes();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(output))) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            assertEquals(ParagraphAlignment.BOTH, p.getAlignment(),
                    "justification BOTH du corps doit etre preservee");
            XWPFRun r = p.getRuns().get(0);
            assertEquals("Calibri", r.getFontFamily(),
                    "police Calibri du corps doit etre preservee");
            assertEquals(10, r.getFontSize(), "taille 10 pt du corps doit etre preservee");
            assertEquals("Capital : 300000 MAD.", r.text(),
                    "le placeholder doit avoir ete substitue");
        }
    }

    // -------- Helper --------

    @FunctionalInterface
    private interface DocxBuilder {
        void build(XWPFDocument doc) throws Exception;
    }

    private byte[] buildDocx(DocxBuilder builder) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.build(doc);
            doc.write(out);
            return out.toByteArray();
        }
    }
}

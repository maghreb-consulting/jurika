package ma.jurika.ai.document;

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
 * 2026-08 — OBJET_SOCIAL multi-activités : le moteur convertit les sauts de ligne
 * {@code '\n'} d'une valeur injectée en vrais retours à la ligne Word ({@code <w:br/>})
 * au sein du paragraphe (sans modifier le texte du modèle directeur).
 */
class DocxTemplateEngineMultilineTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    private static byte[] docx(String... lines) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : lines) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void multiActivitesRenduEnRetoursLigneWord() {
        byte[] t = docx("$OBJET_SOCIAL");
        String objet = "- Le conseil et l’ingénierie\n- La formation professionnelle\n- L’import-export";
        DocxTemplateEngine.DocumentResult res =
                engine.render(t, "TEST.docx", Map.of("OBJET_SOCIAL", objet));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            XWPFParagraph p = doc.getParagraphs().get(0);
            XWPFRun run = p.getRuns().get(0);
            // 3 activités -> 2 sauts de ligne Word (<w:br/>).
            assertEquals(2, run.getCTR().sizeOfBrArray(),
                    "3 activités doivent produire 2 <w:br/>");
            // Le texte complet (run.text() rend <w:br/> en \n) contient les 3 lignes.
            String text = run.text();
            assertTrue(text.contains("Le conseil"), text);
            assertTrue(text.contains("La formation professionnelle"), text);
            assertTrue(text.contains("import-export"), text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void activiteUniqueRenduSansSautDeLigne() {
        byte[] t = docx("$OBJET_SOCIAL");
        DocxTemplateEngine.DocumentResult res =
                engine.render(t, "TEST.docx", Map.of("OBJET_SOCIAL", "Le conseil en gestion"));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            XWPFRun run = doc.getParagraphs().get(0).getRuns().get(0);
            assertEquals(0, run.getCTR().sizeOfBrArray(),
                    "Une seule activité : aucun <w:br/>");
            assertEquals("Le conseil en gestion", run.text());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

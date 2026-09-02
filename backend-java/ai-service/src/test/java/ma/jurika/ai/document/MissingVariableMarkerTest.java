package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests PARTIE A — marquage rouge des variables manquantes.
 *
 * <p>Les fixtures .docx sont construites programmatiquement via Apache POI pour
 * eviter de dependre des modeles directeur.
 */
class MissingVariableMarkerTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    private byte[] buildDocxWithParagraph(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithParagraphs(String... texts) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String t : texts) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(t);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private String fullText(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            return sb.toString();
        }
    }

    private XWPFRun findRunContaining(byte[] docxBytes, String needle) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null && t.contains(needle)) return r;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // 1. Variable absente -> run rouge "‹ VALEUR MANQUANTE : x ›"
    // ---------------------------------------------------------------------

    @Test
    void variable_absente_genere_run_rouge() throws Exception {
        byte[] docx = buildDocxWithParagraph(
                "Apres :${ABSENTE} suite");
        var result = engine.render(docx, "test.docx", Map.of());

        String text = fullText(result.bytes());
        assertTrue(text.contains("‹ VALEUR MANQUANTE : ABSENTE ›"),
                "marqueur attendu : " + text);
        assertFalse(text.contains(""), "sentinel residuel ouverture : " + text);
        assertFalse(text.contains(""), "sentinel residuel fermeture : " + text);
    }

    @Test
    void variable_absente_run_est_gras_rouge_C00000() throws Exception {
        byte[] docx = buildDocxWithParagraph("Avant ${ABSENTE} apres");
        var result = engine.render(docx, "test.docx", Map.of());

        XWPFRun r = findRunContaining(result.bytes(), "VALEUR MANQUANTE");
        assertNotNull(r, "run marqueur introuvable");
        assertEquals(Boolean.TRUE, r.isBold(),
                "le run du marqueur doit etre en gras");
        assertEquals("C00000", r.getColor(),
                "le run du marqueur doit etre rouge C00000, vu : " + r.getColor());
    }

    // ---------------------------------------------------------------------
    // 2. Variable presente -> aucun marqueur
    // ---------------------------------------------------------------------

    @Test
    void variable_presente_pas_de_marqueur() throws Exception {
        byte[] docx = buildDocxWithParagraph("Bonjour ${DENOMINATION}");
        var result = engine.render(docx, "test.docx", Map.of("DENOMINATION", "ACME SARL"));

        String text = fullText(result.bytes());
        assertTrue(text.contains("Bonjour ACME SARL"), text);
        assertFalse(text.contains("VALEUR MANQUANTE"),
                "ne doit contenir aucun marqueur : " + text);
        assertEquals(List.of(), result.missingVariables(),
                "missingVariables doit etre vide");
    }

    // ---------------------------------------------------------------------
    // 3. Header X-Missing-Variables correct (liste ordonnee + dedup)
    // ---------------------------------------------------------------------

    @Test
    void missingVariables_ordonnee_et_dedupliquee() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Premier ${VAR_A}",
                "Deuxieme ${VAR_B}",
                "Repete ${VAR_A}",
                "Troisieme ${VAR_C}");
        var result = engine.render(docx, "test.docx", Map.of("VAR_B", "ok"));

        // VAR_B est present donc absent de la liste ; VAR_A et VAR_C manquent.
        // L'ordre suit la premiere apparition dans le document.
        assertEquals(List.of("VAR_A", "VAR_C"), result.missingVariables(),
                "ordre/dedup attendu");
    }

    @Test
    void plusieurs_occurrences_meme_variable_dedupliquees() throws Exception {
        byte[] docx = buildDocxWithParagraph(
                "X ${VAR_X} et encore ${VAR_X} et ${VAR_X}");
        var result = engine.render(docx, "test.docx", Map.of());

        assertEquals(List.of("VAR_X"), result.missingVariables(),
                "doublons doivent etre dedupliques");
    }

    // ---------------------------------------------------------------------
    // 4. Determinisme : memes octets sur 2 generations identiques
    // ---------------------------------------------------------------------

    @Test
    void determinisme_bit_pour_bit_meme_payload() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Avant ${X} milieu ${Y}",
                "Fin ${X}");
        var r1 = engine.render(docx, "test.docx", Map.of("Y", "valeurY"));
        var r2 = engine.render(docx, "test.docx", Map.of("Y", "valeurY"));

        assertArrayEquals(r1.bytes(), r2.bytes(),
                "deux rendus du meme payload doivent etre bit-pour-bit identiques");
        assertEquals(r1.missingVariables(), r2.missingVariables());
    }

    // ---------------------------------------------------------------------
    // 5. Idempotence de la passe (sentinels deja consommes)
    // ---------------------------------------------------------------------

    @Test
    void passe_idempotente_si_pas_de_sentinel() throws Exception {
        byte[] docx = buildDocxWithParagraph("Texte sans sentinel");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<String> first = MissingVariableMarker.apply(doc);
            List<String> second = MissingVariableMarker.apply(doc);
            assertEquals(List.of(), first);
            assertEquals(List.of(), second);
        }
    }
}

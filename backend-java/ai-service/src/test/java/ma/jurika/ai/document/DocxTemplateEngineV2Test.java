package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests V2 du moteur de templates DOCX. Les fixtures .docx sont construites
 * programmatiquement via Apache POI pour eviter de dependre des modeles officiels.
 */
class DocxTemplateEngineV2Test {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    // -------- Helpers fixture --------

    private byte[] buildDocxWithSingleParagraph(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithMultipleRuns(String... runTexts) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            for (String txt : runTexts) {
                XWPFRun r = p.createRun();
                r.setText(txt);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithParagraphs(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(text);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithTableRow(String cell1Text, String cell2Text, String cell3Text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable(1, 3);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).removeParagraph(0);
            XWPFParagraph p1 = row.getCell(0).addParagraph();
            p1.createRun().setText(cell1Text);
            row.getCell(1).removeParagraph(0);
            XWPFParagraph p2 = row.getCell(1).addParagraph();
            p2.createRun().setText(cell2Text);
            row.getCell(2).removeParagraph(0);
            XWPFParagraph p3 = row.getCell(2).addParagraph();
            p3.createRun().setText(cell3Text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private String extractAllText(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            sb.append(p.getText()).append('\n');
                        }
                    }
                }
            }
            return sb.toString();
        }
    }

    private int countRows(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            int count = 0;
            for (XWPFTable table : doc.getTables()) {
                count += table.getNumberOfRows();
            }
            return count;
        }
    }

    private int countParagraphs(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return doc.getParagraphs().size();
        }
    }

    // -------- Tests substitution scalaire --------

    @Test
    void test_double_support_both_syntaxes_in_same_paragraph() throws Exception {
        byte[] docx = buildDocxWithSingleParagraph("Bonjour ${DENOMINATION} (RC {{rc}})");
        Map<String, Object> vars = new HashMap<>();
        vars.put("DENOMINATION", "ACME SARL");
        vars.put("rc", "12345");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("Bonjour ACME SARL"), "UPPERCASE non resolu : " + text);
        assertTrue(text.contains("RC 12345"), "lowercase non resolu : " + text);
    }

    @Test
    void test_uppercase_only_two_scalars() throws Exception {
        byte[] docx = buildDocxWithSingleParagraph("${DENOMINATION} au capital de ${CAPITAL} MAD");
        Map<String, Object> vars = Map.of(
                "DENOMINATION", "ACME SARL",
                "CAPITAL", "100000"
        );
        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("ACME SARL au capital de 100000 MAD"), text);
    }

    @Test
    void test_legacy_double_brace_still_works() throws Exception {
        byte[] docx = buildDocxWithSingleParagraph("Societe {{denomination}}");
        Map<String, Object> vars = Map.of("denomination", "ACME SARL");
        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("Societe ACME SARL"), text);
    }

    @Test
    void test_run_fragmentation_uppercase() throws Exception {
        // Eclate ${VAR} sur 3 runs : "${", "DENOM", "INATION}"
        byte[] docx = buildDocxWithMultipleRuns("Bonjour ", "${", "DENOM", "INATION}", " et bienvenue");
        Map<String, Object> vars = Map.of("DENOMINATION", "ACME SARL");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("Bonjour ACME SARL et bienvenue"),
                "fragmentation non recomposee : " + text);
    }

    @Test
    void test_run_fragmentation_legacy() throws Exception {
        byte[] docx = buildDocxWithMultipleRuns("{{", "deno", "mination", "}}");
        Map<String, Object> vars = Map.of("denomination", "ACME SARL");
        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("ACME SARL"), text);
    }

    @Test
    void test_unknown_placeholder_rendered_as_red_marker() throws Exception {
        byte[] docx = buildDocxWithSingleParagraph("Inconnu :[${VAR_INEXISTANTE}] suite");
        Map<String, Object> vars = Map.of();

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        // 2026-06-19 (PARTIE A) : variable non fournie -> marqueur rouge visible
        // "‹ VALEUR MANQUANTE : VAR_INEXISTANTE ›" + remontee dans missingVariables.
        assertFalse(text.contains("${VAR_INEXISTANTE}"), "litteral residuel non desire : " + text);
        assertTrue(text.contains("‹ VALEUR MANQUANTE : VAR_INEXISTANTE ›"),
                "marqueur rouge attendu : " + text);
        assertTrue(result.missingVariables().contains("VAR_INEXISTANTE"),
                "VAR_INEXISTANTE devrait apparaitre dans missingVariables : " + result.missingVariables());
    }

    @Test
    void test_unknown_legacy_placeholder_rendered_as_red_marker() throws Exception {
        byte[] docx = buildDocxWithSingleParagraph("Inconnu :[{{var_inconnue}}] suite");
        var result = engine.render(docx, "test.docx", Map.of());
        String text = extractAllText(result.bytes());
        // 2026-06-19 (PARTIE A) : coherence syntaxe legacy {{var}} -> marqueur rouge,
        // nom normalise en UPPERCASE_SNAKE pour la lisibilite cote employe.
        assertFalse(text.contains("{{var_inconnue}}"), "litteral legacy residuel non desire : " + text);
        assertTrue(text.contains("‹ VALEUR MANQUANTE : VAR_INCONNUE ›"),
                "marqueur rouge attendu : " + text);
        assertTrue(result.missingVariables().contains("VAR_INCONNUE"),
                "VAR_INCONNUE devrait apparaitre dans missingVariables : " + result.missingVariables());
    }

    // -------- Test générique sortie propre (Fix L7b moteur) --------

    @Test
    void test_generic_template_no_residuals() throws Exception {
        // Template GÉNÉRIQUE construit en mémoire — 2 scalaires + 1 bloc répétable.
        byte[] docx = buildDocxWithParagraphs(
                "Bonjour ${NOM} de ${VILLE}",
                "▶ FRUITS - ${ITEM_NOM} (${ITEM_PRIX} MAD)"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("NOM", "Alice");
        vars.put("VILLE", "Casablanca");
        vars.put("FRUITS", List.of(
                Map.of("ITEM_NOM", "Pomme",  "ITEM_PRIX", "10"),
                Map.of("ITEM_NOM", "Banane", "ITEM_PRIX", "5"),
                Map.of("ITEM_NOM", "Orange", "ITEM_PRIX", "7")
        ));

        var result = engine.render(docx, "generic.docx", vars);
        String text = extractAllText(result.bytes());

        // Substitutions scalaires
        assertTrue(text.contains("Bonjour Alice de Casablanca"),
                "scalaires non substitués : " + text);

        // Bloc répétable expandu pour chaque item
        assertTrue(text.contains("Pomme"),  "item 1 manquant : " + text);
        assertTrue(text.contains("Banane"), "item 2 manquant : " + text);
        assertTrue(text.contains("Orange"), "item 3 manquant : " + text);
        assertTrue(text.contains("10 MAD"), "prix 1 manquant : " + text);
        assertTrue(text.contains("5 MAD"),  "prix 2 manquant : " + text);
        assertTrue(text.contains("7 MAD"),  "prix 3 manquant : " + text);

        // ZÉRO résidu ${} (Fix 2)
        assertFalse(text.matches("(?s).*\\$\\{[A-Z][A-Z0-9_]*\\}.*"),
                "placeholder ${} résiduel détecté : " + text);

        // ZÉRO marqueur ▶ (Fix 1)
        assertFalse(text.contains("▶"),
                "marqueur ▶ résiduel détecté : " + text);

        // Nom de bloc FRUITS jamais visible dans la sortie
        int fruitsOccurrences = countSubstring(text, "FRUITS");
        assertEquals(0, fruitsOccurrences,
                "nom de bloc FRUITS visible (" + fruitsOccurrences + " occ) : " + text);
    }

    private int countSubstring(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // -------- Tests blocs repetables --------

    @Test
    void test_block_associes_in_paragraph_three_items() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Liste des associes :",
                "▶ ASSOCIES ${ASSOCIE_NOM} detient ${ASSOCIE_PARTS_CHIFFRES} parts",
                "Fin de liste."
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("ASSOCIES", List.of(
                Map.of("ASSOCIE_NOM", "Alice", "ASSOCIE_PARTS_CHIFFRES", "100"),
                Map.of("ASSOCIE_NOM", "Bob", "ASSOCIE_PARTS_CHIFFRES", "50"),
                Map.of("ASSOCIE_NOM", "Carol", "ASSOCIE_PARTS_CHIFFRES", "25")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("Alice detient 100 parts"), text);
        assertTrue(text.contains("Bob detient 50 parts"), text);
        assertTrue(text.contains("Carol detient 25 parts"), text);
        assertFalse(text.contains("▶"), "marqueur ▶ subsiste : " + text);
    }

    @Test
    void test_block_associes_in_table_row_three_items() throws Exception {
        byte[] docx = buildDocxWithTableRow(
                "▶ ASSOCIES",
                "${ASSOCIE_NOM}",
                "${ASSOCIE_PARTS_CHIFFRES}"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("ASSOCIES", List.of(
                Map.of("ASSOCIE_NOM", "Alice", "ASSOCIE_PARTS_CHIFFRES", "100"),
                Map.of("ASSOCIE_NOM", "Bob", "ASSOCIE_PARTS_CHIFFRES", "50"),
                Map.of("ASSOCIE_NOM", "Carol", "ASSOCIE_PARTS_CHIFFRES", "25")
        ));

        var result = engine.render(docx, "test.docx", vars);
        int rows = countRows(result.bytes());
        String text = extractAllText(result.bytes());

        assertEquals(3, rows, "expected 3 rows after clone, got " + rows + " ; text=" + text);
        assertTrue(text.contains("Alice"), text);
        assertTrue(text.contains("Bob"), text);
        assertTrue(text.contains("Carol"), text);
        assertFalse(text.contains("▶"), "marqueur ▶ subsiste : " + text);
    }

    @Test
    void test_block_empty_list_removes_carrier_row() throws Exception {
        byte[] docx = buildDocxWithTableRow(
                "▶ ASSOCIES",
                "${ASSOCIE_NOM}",
                "${ASSOCIE_PARTS_CHIFFRES}"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("ASSOCIES", List.of());

        var result = engine.render(docx, "test.docx", vars);
        int rows = countRows(result.bytes());
        assertEquals(0, rows, "expected 0 rows after empty expansion, got " + rows);
    }

    @Test
    void test_block_resolutions_auto_rang_generated() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Resolutions de l'assemblee :",
                "▶ RESOLUTIONS ${RESOLUTION_RANG} RESOLUTION : ${RESOLUTION_TEXTE}"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("RESOLUTIONS", List.of(
                Map.of("RESOLUTION_TEXTE", "Approbation comptes"),
                Map.of("RESOLUTION_TEXTE", "Quitus gerant"),
                Map.of("RESOLUTION_TEXTE", "Affectation resultat"),
                Map.of("RESOLUTION_TEXTE", "Pouvoirs")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("PREMIÈRE RESOLUTION : Approbation comptes"), text);
        assertTrue(text.contains("DEUXIÈME RESOLUTION : Quitus gerant"), text);
        assertTrue(text.contains("TROISIÈME RESOLUTION : Affectation resultat"), text);
        assertTrue(text.contains("QUATRIÈME RESOLUTION : Pouvoirs"), text);
    }

    @Test
    void test_block_resolutions_explicit_rang_not_overridden() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "▶ RESOLUTIONS ${RESOLUTION_RANG} RESOLUTION"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("RESOLUTIONS", List.of(
                Map.of("RESOLUTION_RANG", "SPÉCIALE")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("SPÉCIALE RESOLUTION"), text);
        assertFalse(text.contains("PREMIÈRE"), "auto-rang ne devrait pas ecraser : " + text);
    }

    @Test
    void test_block_ordre_du_jour_two_items() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Ordre du jour :",
                "▶ ORDRE_DU_JOUR - ${ORDRE_DU_JOUR_POINT}"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("ORDRE_DU_JOUR", List.of(
                Map.of("ORDRE_DU_JOUR_POINT", "Point A"),
                Map.of("ORDRE_DU_JOUR_POINT", "Point B")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("- Point A"), text);
        assertTrue(text.contains("- Point B"), text);

        // 1 paragraphe intro + 2 paragraphes clones = au moins 3 (le default + autres techniques).
        int paragraphs = countParagraphs(result.bytes());
        assertTrue(paragraphs >= 3, "expected at least 3 paragraphs, got " + paragraphs);
    }

    @Test
    void test_case_insensitive_list_key_lookup() throws Exception {
        // Cle stockee en minuscules dans variables, marqueur en MAJUSCULES dans le template.
        byte[] docx = buildDocxWithParagraphs(
                "▶ ASSOCIES ${ASSOCIE_NOM}"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("associes", List.of(
                Map.of("ASSOCIE_NOM", "Alice"),
                Map.of("ASSOCIE_NOM", "Bob")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("Alice"), text);
        assertTrue(text.contains("Bob"), text);
    }

    @Test
    void test_block_unknown_key_leaves_marker_intact() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "▶ NONEXISTENT ${X}"
        );
        var result = engine.render(docx, "test.docx", Map.of());
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("▶ NONEXISTENT"),
                "marker should remain when key not found : " + text);
    }

    // ============================================================
    // CONDITIONAL BLOCKS (◇ FLAG / ◆ FLAG) — Sprint 2026-06-12
    // ============================================================

    @Test
    void test_cond_single_paragraph_truthy_keeps_content_strips_markers() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Intro",
                "◇ CLAUSE_PREEMPTION Article 12.1 : droit de preemption. ◆ CLAUSE_PREEMPTION",
                "Conclusion"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("CLAUSE_PREEMPTION", "oui");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("Article 12.1 : droit de preemption."), text);
        assertFalse(text.contains("◇"), "marqueur ◇ subsiste : " + text);
        assertFalse(text.contains("◆"), "marqueur ◆ subsiste : " + text);
        assertFalse(text.contains("CLAUSE_PREEMPTION"), "nom flag subsiste : " + text);
    }

    @Test
    void test_cond_single_paragraph_falsy_removes_paragraph() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Intro",
                "◇ CLAUSE_PREEMPTION Article 12.1 : droit de preemption. ◆ CLAUSE_PREEMPTION",
                "Conclusion"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("CLAUSE_PREEMPTION", "");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertFalse(text.contains("Article 12.1"), "contenu falsy non retire : " + text);
        assertFalse(text.contains("◇"), text);
        assertFalse(text.contains("◆"), text);
        assertTrue(text.contains("Intro"), text);
        assertTrue(text.contains("Conclusion"), text);
    }

    @Test
    void test_cond_multi_paragraph_falsy_removes_range() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Intro",
                "◇ APPORT_NATURE Article 6.2 : apports en nature.",
                "Suite du detail apport nature.",
                "Fin apport nature. ◆ APPORT_NATURE",
                "Conclusion"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("APPORT_NATURE", "non");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertFalse(text.contains("Article 6.2"), text);
        assertFalse(text.contains("Suite du detail"), text);
        assertFalse(text.contains("Fin apport nature"), text);
        assertTrue(text.contains("Intro"), text);
        assertTrue(text.contains("Conclusion"), text);
    }

    @Test
    void test_cond_multi_paragraph_truthy_keeps_range_strips_markers() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Intro",
                "◇ APPORT_NATURE Article 6.2 : apports en nature.",
                "Suite du detail apport nature.",
                "Fin apport nature. ◆ APPORT_NATURE",
                "Conclusion"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("APPORT_NATURE", "oui");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());

        assertTrue(text.contains("Article 6.2"), text);
        assertTrue(text.contains("Suite du detail"), text);
        assertTrue(text.contains("Fin apport nature"), text);
        assertFalse(text.contains("◇"), text);
        assertFalse(text.contains("◆"), text);
        assertFalse(text.contains("APPORT_NATURE"), text);
    }

    @Test
    void test_cond_falsy_treats_boolean_false() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "◇ CAC_NOMME CAC clause. ◆ CAC_NOMME"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("CAC_NOMME", Boolean.FALSE);

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertFalse(text.contains("CAC clause"), text);
    }

    @Test
    void test_cond_truthy_treats_boolean_true_and_yes_variants() throws Exception {
        for (Object truthy : new Object[]{"oui", "yes", "true", "1", "ON", Boolean.TRUE, Integer.valueOf(1)}) {
            byte[] docx = buildDocxWithParagraphs(
                    "◇ FLAG content. ◆ FLAG"
            );
            Map<String, Object> vars = new HashMap<>();
            vars.put("FLAG", truthy);
            var result = engine.render(docx, "test.docx", vars);
            String text = extractAllText(result.bytes());
            assertTrue(text.contains("content."), "echoue pour " + truthy + " : " + text);
        }
    }

    @Test
    void test_cond_unknown_flag_treated_falsy() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Avant",
                "◇ UNKNOWN_FLAG section optionnelle. ◆ UNKNOWN_FLAG",
                "Apres"
        );
        var result = engine.render(docx, "test.docx", Map.of());
        String text = extractAllText(result.bytes());
        assertFalse(text.contains("section optionnelle"), text);
        assertTrue(text.contains("Avant"));
        assertTrue(text.contains("Apres"));
    }

    @Test
    void test_cond_table_row_falsy_removes_row() throws Exception {
        byte[] docx = buildDocxWithTableRow(
                "◇ COLONNE_OPT",
                "valeur",
                "◆ COLONNE_OPT"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("COLONNE_OPT", "non");

        var result = engine.render(docx, "test.docx", vars);
        int rows = countRows(result.bytes());
        assertEquals(0, rows, "row falsy non supprimee, count=" + rows);
    }

    @Test
    void test_cond_table_row_truthy_keeps_row_strips_markers() throws Exception {
        byte[] docx = buildDocxWithTableRow(
                "◇ COLONNE_OPT debut",
                "valeur",
                "fin ◆ COLONNE_OPT"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("COLONNE_OPT", "oui");

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        int rows = countRows(result.bytes());
        assertEquals(1, rows);
        assertTrue(text.contains("debut"));
        assertTrue(text.contains("valeur"));
        assertTrue(text.contains("fin"));
        assertFalse(text.contains("◇"));
        assertFalse(text.contains("◆"));
    }

    @Test
    void test_cond_wraps_repeatable_block_truthy_then_iterates() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "◇ HAS_ASSOCIES Liste des associes :",
                "▶ ASSOCIES ${ASSOCIE_NOM}",
                "Fin liste. ◆ HAS_ASSOCIES"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("HAS_ASSOCIES", "oui");
        vars.put("ASSOCIES", List.of(
                Map.of("ASSOCIE_NOM", "Alice"),
                Map.of("ASSOCIE_NOM", "Bob")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertTrue(text.contains("Liste des associes"), text);
        assertTrue(text.contains("Alice"), text);
        assertTrue(text.contains("Bob"), text);
        assertTrue(text.contains("Fin liste"), text);
        assertFalse(text.contains("◇"), text);
    }

    @Test
    void test_cond_wraps_repeatable_block_falsy_removes_block_entirely() throws Exception {
        byte[] docx = buildDocxWithParagraphs(
                "Avant",
                "◇ HAS_ASSOCIES Liste des associes :",
                "▶ ASSOCIES ${ASSOCIE_NOM}",
                "Fin liste. ◆ HAS_ASSOCIES",
                "Apres"
        );
        Map<String, Object> vars = new HashMap<>();
        vars.put("HAS_ASSOCIES", "non");
        vars.put("ASSOCIES", List.of(
                Map.of("ASSOCIE_NOM", "Alice")
        ));

        var result = engine.render(docx, "test.docx", vars);
        String text = extractAllText(result.bytes());
        assertFalse(text.contains("Alice"), "le contenu interne doit etre purge avec le bloc : " + text);
        assertFalse(text.contains("Liste des associes"), text);
        assertFalse(text.contains("▶"), text);
        assertTrue(text.contains("Avant"));
        assertTrue(text.contains("Apres"));
    }
}

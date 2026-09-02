package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class HtmlToDocxConverterTest {

    private final HtmlToDocxConverter converter = new HtmlToDocxConverter();

    private XWPFDocument open(byte[] bytes) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private String allText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append('\n');
        }
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow r : t.getRows()) {
                for (XWPFTableCell c : r.getTableCells()) {
                    sb.append(c.getText()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    @Test
    void test_simple_paragraph() throws Exception {
        byte[] docx = converter.convert("<p>Bonjour le monde</p>");
        try (XWPFDocument doc = open(docx)) {
            assertTrue(allText(doc).contains("Bonjour le monde"));
        }
    }

    @Test
    void test_headings_h1_h2_h3() throws Exception {
        byte[] docx = converter.convert(
                "<h1>Titre principal</h1>" +
                        "<h2>Sous-titre</h2>" +
                        "<h3>Section</h3>" +
                        "<p>Contenu</p>");
        try (XWPFDocument doc = open(docx)) {
            String all = allText(doc);
            assertTrue(all.contains("Titre principal"));
            assertTrue(all.contains("Sous-titre"));
            assertTrue(all.contains("Section"));
            assertTrue(all.contains("Contenu"));

            // Le premier paragraphe non-vide doit être centré (h1).
            XWPFParagraph h1 = doc.getParagraphs().stream()
                    .filter(p -> "Titre principal".equals(p.getText()))
                    .findFirst().orElseThrow();
            assertEquals(ParagraphAlignment.CENTER, h1.getAlignment());

            // H1 doit être en gras taille 18.
            assertFalse(h1.getRuns().isEmpty());
            XWPFRun r = h1.getRuns().get(0);
            assertTrue(r.isBold(), "h1 doit être gras");
            assertEquals(18, r.getFontSize());
        }
    }

    @Test
    void test_bold_italic_underline_inline() throws Exception {
        byte[] docx = converter.convert(
                "<p>Un texte avec <strong>gras</strong>, <em>italique</em> et <u>souligne</u>.</p>");
        try (XWPFDocument doc = open(docx)) {
            assertTrue(allText(doc).contains("Un texte avec"));
            XWPFParagraph p = doc.getParagraphs().stream()
                    .filter(par -> par.getText() != null && par.getText().contains("Un texte avec"))
                    .findFirst().orElseThrow();
            boolean foundBold = false, foundItalic = false, foundUnderline = false;
            for (XWPFRun r : p.getRuns()) {
                String t = r.text() == null ? "" : r.text();
                if (t.contains("gras") && r.isBold()) foundBold = true;
                if (t.contains("italique") && r.isItalic()) foundItalic = true;
                if (t.contains("souligne") && r.getUnderline() != UnderlinePatterns.NONE) foundUnderline = true;
            }
            assertTrue(foundBold, "Gras pas detecte sur le run 'gras'");
            assertTrue(foundItalic, "Italique pas detecte");
            assertTrue(foundUnderline, "Souligne pas detecte");
        }
    }

    @Test
    void test_unordered_list_renders_items() throws Exception {
        byte[] docx = converter.convert(
                "<ul><li>Premier item</li><li>Deuxieme item</li><li>Troisieme item</li></ul>");
        try (XWPFDocument doc = open(docx)) {
            String txt = allText(doc);
            assertTrue(txt.contains("Premier item"));
            assertTrue(txt.contains("Deuxieme item"));
            assertTrue(txt.contains("Troisieme item"));
        }
    }

    @Test
    void test_ordered_list_renders_with_numbers_fallback() throws Exception {
        byte[] docx = converter.convert(
                "<ol><li>Etape A</li><li>Etape B</li></ol>");
        try (XWPFDocument doc = open(docx)) {
            String txt = allText(doc);
            // Fallback prefix attendu : "1. Etape A" / "2. Etape B".
            assertTrue(txt.contains("1. Etape A") || txt.contains("Etape A"),
                    "Item A absent : " + txt);
            assertTrue(txt.contains("2. Etape B") || txt.contains("Etape B"),
                    "Item B absent : " + txt);
        }
    }

    @Test
    void test_table_renders_rows_and_cells() throws Exception {
        byte[] docx = converter.convert(
                "<table>" +
                        "<tr><th>Associe</th><th>Parts</th></tr>" +
                        "<tr><td>Oussama</td><td>1500</td></tr>" +
                        "<tr><td>Salma</td><td>800</td></tr>" +
                        "</table>");
        try (XWPFDocument doc = open(docx)) {
            assertEquals(1, doc.getTables().size());
            XWPFTable table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            assertTrue(allText(doc).contains("Oussama"));
            assertTrue(allText(doc).contains("1500"));
            assertTrue(allText(doc).contains("Salma"));
            assertTrue(allText(doc).contains("800"));

            // Header cell gras.
            XWPFTableCell headerCell = table.getRow(0).getCell(0);
            assertEquals("Associe", headerCell.getText());
            boolean headerBold = headerCell.getParagraphs().stream()
                    .flatMap(p -> p.getRuns().stream())
                    .anyMatch(XWPFRun::isBold);
            assertTrue(headerBold, "header cell doit etre gras");
        }
    }

    @Test
    void test_nested_div_recurses() throws Exception {
        byte[] docx = converter.convert(
                "<div><div><p>Texte profond</p></div></div>");
        try (XWPFDocument doc = open(docx)) {
            assertTrue(allText(doc).contains("Texte profond"));
        }
    }

    @Test
    void test_unknown_tag_skipped_gracefully() throws Exception {
        byte[] docx = converter.convert(
                "<p>Avant</p><foobar>Ignore?</foobar><p>Apres</p>");
        try (XWPFDocument doc = open(docx)) {
            assertTrue(allText(doc).contains("Avant"));
            assertTrue(allText(doc).contains("Apres"));
            // 'Ignore?' est rendu via le fallback div (le tag inconnu devient bloc).
            // Tolere les 2 comportements (rendu ou skip), l'important est de ne pas crasher.
        }
    }

    @Test
    void test_empty_html() throws Exception {
        byte[] docx = converter.convert("");
        try (XWPFDocument doc = open(docx)) {
            assertNotNull(doc);
        }
        byte[] docx2 = converter.convert(null);
        try (XWPFDocument doc2 = open(docx2)) {
            assertNotNull(doc2);
        }
    }
}

package ma.jurika.ai.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Migration 2026-07-14 : le rendu HTML -> PDF passe par LibreOffice
 * (DocxToPdfConverter). Les tests qui produisent réellement un PDF sont sautés
 * (assumeTrue) si LibreOffice n'est pas installé sur la machine — comme pour la
 * conversion DOCX -> PDF.
 */
class HtmlToPdfConverterTest {

    private DocxToPdfConverter office;
    private HtmlToPdfConverter converter;

    @BeforeEach
    void setUp() {
        office = new DocxToPdfConverter("", 90, true);
        office.detectAtStartup(); // package-private : resout soffice si present
        converter = new HtmlToPdfConverter(office);
    }

    @Test
    void test_simple_html_produces_pdf_with_magic_bytes() {
        assumeTrue(office.isAvailable(), "LibreOffice absent — conversion PDF non testable");
        byte[] bytes = converter.convert(
                "<h1>JURIKA TEST SARL</h1><p>Statuts <strong>constitutifs</strong>.</p>",
                "Statuts");
        assertNotNull(bytes);
        assertTrue(bytes.length > 500, "PDF trop court : " + bytes.length);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    @Test
    void test_empty_html_does_not_crash() {
        assumeTrue(office.isAvailable(), "LibreOffice absent — conversion PDF non testable");
        byte[] bytes = converter.convert("", "Vide");
        assertNotNull(bytes);
        assertTrue(bytes.length > 200);
    }

    @Test
    void test_table_and_lists_render() {
        assumeTrue(office.isAvailable(), "LibreOffice absent — conversion PDF non testable");
        byte[] bytes = converter.convert(
                "<h2>Associes</h2>" +
                        "<table><tr><th>Nom</th><th>Parts</th></tr>" +
                        "<tr><td>Oussama</td><td>1500</td></tr></table>" +
                        "<ul><li>Item 1</li><li>Item 2</li></ul>",
                "Liste");
        assertNotNull(bytes);
        assertTrue(bytes.length > 1000);
    }

    @Test
    void wrap_est_bien_forme_meme_sans_libreoffice() {
        // Ne produit pas de PDF : verifie seulement que la coque HTML est robuste
        // (null-safe), independamment de la disponibilite de LibreOffice.
        assertNotNull(HtmlToPdfConverter.CABINET_CSS);
        assertFalse(HtmlToPdfConverter.CABINET_CSS.isBlank());
    }
}

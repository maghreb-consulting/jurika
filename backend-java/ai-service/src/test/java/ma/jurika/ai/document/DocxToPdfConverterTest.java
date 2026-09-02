package ma.jurika.ai.document;

import ma.jurika.ai.document.DocxToPdfConverter.LibreOfficeUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link DocxToPdfConverter} sans LibreOffice (fast) puis avec si dispo.
 */
class DocxToPdfConverterTest {

    @Test
    void disabled_property_makes_converter_unavailable() {
        DocxToPdfConverter c = new DocxToPdfConverter("", 60, false);
        c.detectAtStartup();
        assertFalse(c.isAvailable());
        assertNull(c.sofficePath());
    }

    @Test
    void invalid_configured_path_falls_back_to_detection() {
        DocxToPdfConverter c = new DocxToPdfConverter(
                "C:/this/path/does/not/exist/soffice.exe", 60, true);
        c.detectAtStartup();
        // Resultat depend de l'env (PATH/installations par defaut). On verifie
        // au minimum que ca ne throw pas et que isAvailable() est coherent.
        if (c.isAvailable()) {
            assertNotNull(c.sofficePath());
            assertNotEquals("C:/this/path/does/not/exist/soffice.exe", c.sofficePath());
        } else {
            assertNull(c.sofficePath());
        }
    }

    @Test
    void convert_when_unavailable_throws_dedicated_exception() {
        DocxToPdfConverter c = new DocxToPdfConverter("", 60, false);
        c.detectAtStartup();
        assertFalse(c.isAvailable());
        LibreOfficeUnavailableException ex = assertThrows(
                LibreOfficeUnavailableException.class,
                () -> c.convert(new byte[]{1, 2, 3}, "test"));
        assertTrue(ex.getMessage().toLowerCase().contains("libreoffice"));
    }

    @Test
    void convert_rejects_empty_input_when_available_or_throws_unavailable() {
        DocxToPdfConverter c = new DocxToPdfConverter("", 60, true);
        c.detectAtStartup();
        if (c.isAvailable()) {
            assertThrows(IllegalArgumentException.class,
                    () -> c.convert(new byte[0], "test"));
            assertThrows(IllegalArgumentException.class,
                    () -> c.convert(null, "test"));
        } else {
            // Sans soffice, on tombe d'abord sur l'exception unavailable.
            assertThrows(LibreOfficeUnavailableException.class,
                    () -> c.convert(new byte[0], "test"));
        }
    }

    /**
     * Sanity end-to-end : ne tourne que si LibreOffice est detecte (CI dev sans).
     * Utilise un .docx minimal genere via POI a la volee pour rester independant
     * des templates business.
     */
    @Test
    @EnabledIf("hasLibreOffice")
    void real_conversion_produces_valid_pdf_when_libreoffice_installed() throws Exception {
        DocxToPdfConverter c = new DocxToPdfConverter("", 90, true);
        c.detectAtStartup();
        assumeAvailable(c);

        byte[] minimalDocx = MinimalDocxFactory.build("JURIKA test fidele.");
        byte[] pdf = c.convert(minimalDocx, "smoke");

        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "PDF trop court : " + pdf.length);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static void assumeAvailable(DocxToPdfConverter c) {
        if (!c.isAvailable()) {
            // safety net : le @EnabledIf l'a deja exclu mais on garde la verif.
            throw new AssertionError("soffice indisponible mais @EnabledIf l'a laisse passer");
        }
    }

    @SuppressWarnings("unused")
    static boolean hasLibreOffice() {
        DocxToPdfConverter probe = new DocxToPdfConverter("", 60, true);
        ReflectionTestUtils.invokeMethod(probe, "detectAtStartup");
        return probe.isAvailable();
    }
}

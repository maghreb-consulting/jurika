package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot A (2026-09-10) — le déterminisme, prouvé plutôt qu'espéré.
 *
 * <p>« Le même dossier donne le même document » est l'argument du projet devant un
 * cabinet. Il ne tenait pas octet pour octet : POI horodate chaque entrée du ZIP à
 * l'heure d'écriture, et deux générations séparées de deux secondes différaient sur
 * ces octets-là. {@code determinisme_bit_pour_bit_meme_payload} l'a d'ailleurs
 * signalé une fois, en rouge, sur l'octet d'index 10 — le champ heure de l'en-tête
 * local — avant de repasser au vert de lui-même.
 *
 * <p>Le test ci-dessous ATTEND la frontière de deux secondes de l'horodatage MS-DOS
 * entre les deux rendus. Sans {@link ZipHorodatage}, il échoue à tous les coups.
 */
class ZipHorodatageTest {

    /** Frontière de l'horodatage MS-DOS : 2 s. On l'enjambe franchement. */
    private static final long AU_DELA_DE_LA_FRONTIERE_MS = 2_400;

    private Method render() throws Exception {
        Method m = DocxTemplateEngine.class.getDeclaredMethod(
                "renderInternal", InputStream.class, Map.class, boolean.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    private byte[] rendu(Method render, Map<String, Object> vars) throws Exception {
        DocxTemplateEngine engine = new DocxTemplateEngine();
        try (InputStream in = new ClassPathResource(
                "lotA/gabarits/ANNONCE_LEGALE_CONSTITUTION.docx").getInputStream()) {
            Object outcome = render.invoke(engine, in, vars, false, false);
            Method bytes = outcome.getClass().getDeclaredMethod("bytes");
            bytes.setAccessible(true);
            return (byte[]) bytes.invoke(outcome);
        }
    }

    @Test
    @DisplayName("deux rendus séparés par la frontière DOS restent identiques octet pour octet")
    void deux_rendus_a_deux_secondes_dintervalle_sont_identiques() throws Exception {
        Method render = render();
        Map<String, Object> vars = Map.of(
                "DENOMINATION", "ATLAS NEGOCE",
                "CAPITAL_CHIFFRES", "100 000",
                "SIEGE_SOCIAL", "12, rue Ibn Batouta, Casablanca");

        byte[] premier = rendu(render, vars);
        Thread.sleep(AU_DELA_DE_LA_FRONTIERE_MS);
        byte[] second = rendu(render, vars);

        assertArrayEquals(premier, second,
                "le même dossier doit donner le même document, à la seconde près comme au jour près");
    }

    @Test
    @DisplayName("toutes les entrées du .docx portent la date figée du 1er janvier 1980")
    void toutes_les_entrees_portent_la_date_figee() throws Exception {
        byte[] docx = rendu(render(), Map.of("DENOMINATION", "ATLAS NEGOCE"));

        int entrees = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entrees++;
                java.time.LocalDateTime quand = e.getTimeLocal();
                assertTrue(quand.getYear() == 1980 && quand.getMonthValue() == 1
                                && quand.getDayOfMonth() == 1,
                        "entrée « " + e.getName() + " » horodatée " + quand);
            }
        }
        assertTrue(entrees > 0, "le .docx doit contenir des entrées");
    }

    @Test
    @DisplayName("le document reste lisible : figer la date ne touche à rien d'autre")
    void le_document_reste_ouvrable() throws Exception {
        byte[] docx = rendu(render(), Map.of("DENOMINATION", "ATLAS NEGOCE"));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<String> textes = doc.getParagraphs().stream()
                    .map(p -> p.getText() == null ? "" : p.getText())
                    .toList();
            assertTrue(textes.stream().anyMatch(t -> t.contains("ATLAS NEGOCE")),
                    "la dénomination substituée doit se lire dans le document");
        }
    }

    @Test
    @DisplayName("une entrée non-ZIP passe sans dommage plutôt que de faire échouer une génération")
    void entree_illisible_rendue_telle_quelle() {
        byte[] pasUnZip = "ceci n'est pas un zip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(pasUnZip, ZipHorodatage.figer(pasUnZip));
        assertArrayEquals(new byte[0], ZipHorodatage.figer(new byte[0]));
    }

    /** Garde-fou : le ByteArrayOutputStream de POI n'est pas contourné par erreur. */
    @Test
    void figer_rend_le_meme_tableau() throws Exception {
        ByteArrayOutputStream vide = new ByteArrayOutputStream();
        assertArrayEquals(vide.toByteArray(), ZipHorodatage.figer(vide.toByteArray()));
    }
}

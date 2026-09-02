package ma.jurika.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.DictionaryManifest;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.mapper.CreationSarlMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sprint P2.5 2026-06-21 (Cowork) — Test de couverture data-driven.
 *
 * <p>Pour chaque template du manifest, vérifie :
 * <ul>
 *   <li>Le fichier .docx existe sur le classpath.</li>
 *   <li>Pour les templates CREATION_SARL/SARL_AU avec fixture "dossier complet" :
 *       la liste {@code missingVariables} retournée par {@link DocxTemplateEngine}
 *       est un SOUS-ENSEMBLE de {@code dictionary.json#fill_later} — autrement dit,
 *       AUCUNE variable obligatoire ne reste sans valeur ni placeholder.</li>
 * </ul>
 *
 * <p>Régression-fence : si un template référence une variable absente du
 * manifest ET du mapper, le test échoue (= le rouge "VALEUR MANQUANTE"
 * serait visible dans le document).
 *
 * <p>Pour l'instant focus sur les 2 statuts Creation. Extension aux autres
 * workflows (Modification/Dissolution/Liquidation/PvAgo/Succursale*) à
 * ajouter dès qu'une fixture "dossier complet" existe par mapper.
 */
class TemplateCoverageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TemplateManifestLoader loader;
    private static DocxTemplateEngine engine;
    private static CreationSarlMapper creationSarlMapper;
    private static Set<String> fillLater;

    private static synchronized TemplateManifestLoader loader() {
        if (loader == null) {
            loader = new TemplateManifestLoader(MAPPER);
            loader.load();
        }
        return loader;
    }

    private static synchronized DocxTemplateEngine engine() {
        if (engine == null) {
            TemplateDefaultsApplier applier = new TemplateDefaultsApplier(loader());
            engine = new DocxTemplateEngine(loader(), applier);
        }
        return engine;
    }

    private static synchronized CreationSarlMapper sarlMapper() {
        if (creationSarlMapper == null) creationSarlMapper = new CreationSarlMapper();
        return creationSarlMapper;
    }

    private static synchronized Set<String> fillLater() {
        if (fillLater == null) {
            DictionaryManifest dict = loader().dictionary();
            fillLater = (dict == null || dict.fillLater() == null)
                    ? Collections.emptySet()
                    : new HashSet<>(dict.fillLater());
        }
        return fillLater;
    }

    // ---------------------------------------------------------------------
    // Test 1 — Tous les templates du manifest doivent avoir leur .docx
    //          present sur le classpath (sinon mismatch manifest/disque).
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("allDirectEntries")
    void each_manifest_template_has_a_docx_on_classpath(TemplateManifest.TemplateEntry entry) {
        if (entry.file() == null || entry.file().isBlank()) {
            // Certains placeholders (origin=placeholder) peuvent n'avoir aucun
            // fichier : on s'assure juste qu'on n'a pas dimanchee une entree
            // directeur sans fichier.
            if (!"placeholder".equalsIgnoreCase(entry.origin())) {
                fail("Template " + entry.code() + " : aucun fichier declare et origin != placeholder");
            }
            return;
        }
        String path = "templates/docx/" + entry.file();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Template " + entry.code() + " : fichier introuvable a " + path);
        } catch (Exception ex) {
            fail("Template " + entry.code() + " : erreur de chargement " + path + " — " + ex.getMessage());
        }
    }

    static Stream<TemplateManifest.TemplateEntry> allDirectEntries() {
        return loader().allEntries().stream()
                .filter(e -> e.aliasOf() == null);
    }

    // ---------------------------------------------------------------------
    // Test 2 — Couverture statuts SARL/SARL_AU : missingVariables ⊆ fillLater
    //          avec fixture "dossier complet".
    // ---------------------------------------------------------------------

    // Phase E2 (2026-08-09) — la couverture porte désormais sur les Statuts
    // DIRECTEUR (STATUTS_SARL_DIRECTEUR / _AU), le même gabarit que la refonte
    // MODIFICATION rend (STATUTS_REFONDUS_* pointent dessus). La voie LEGACY
    // STATUTS_CONSTITUTIFS_* a été retirée.

    @Test
    void statuts_directeur_sarl_full_payload_no_missing_except_fillLater() throws Exception {
        Map<String, Object> payload = loadFixture("/workflow-fixtures/creation_sarl_full.json");
        Map<String, Object> vars = sarlMapper().map("STATUTS_SARL_DIRECTEUR", payload);
        DocumentResult result = engine().generate("STATUTS_SARL_DIRECTEUR", vars);

        List<String> missing = result.missingVariables();
        Set<String> illegal = missing.stream()
                .filter(name -> !fillLater().contains(name))
                .collect(Collectors.toSet());
        assertTrue(illegal.isEmpty(),
                "STATUTS_SARL_DIRECTEUR : variables manquantes hors fillLater = " + illegal
                        + ". Soit ajouter au builder, soit declarer fill_later dans dictionary.json.");
    }

    @Test
    void statuts_directeur_sarl_au_full_payload_no_missing_except_fillLater() throws Exception {
        Map<String, Object> payload = loadFixture("/workflow-fixtures/creation_sarl_full.json");
        Map<String, Object> vars = sarlMapper().map("STATUTS_SARL_AU_DIRECTEUR", payload);
        DocumentResult result = engine().generate("STATUTS_SARL_AU_DIRECTEUR", vars);

        List<String> missing = result.missingVariables();
        Set<String> illegal = missing.stream()
                .filter(name -> !fillLater().contains(name))
                .collect(Collectors.toSet());
        assertTrue(illegal.isEmpty(),
                "STATUTS_SARL_AU_DIRECTEUR : variables manquantes hors fillLater = " + illegal);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadFixture(String resourcePath) throws Exception {
        try (InputStream in = TemplateCoverageTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Fixture introuvable : " + resourcePath);
            Map<String, Object> raw = MAPPER.readValue(in, Map.class);
            return new LinkedHashMap<>(raw);
        }
    }
}

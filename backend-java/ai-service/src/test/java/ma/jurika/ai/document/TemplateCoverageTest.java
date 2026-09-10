package ma.jurika.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    // Lot A (2026-09-10) — les deux tests de couverture des statuts CRÉATION
    // sont RETIRÉS : leur sujet n'existe plus. Ils vérifiaient que
    // STATUTS_SARL_DIRECTEUR / _AU, rendus depuis la fixture « dossier complet »,
    // ne laissaient aucune variable manquante hors fill_later. Ces deux codes
    // sont sortis du manifeste avec le corpus d'août ; le corpus du 9 septembre
    // les remplace par STATUTS_SARL / STATUTS_SARL_AU, qu'aucun mapper ne résout
    // encore.
    //
    // La garantie doit REVENIR au lot B, sur les nouveaux codes et une fixture
    // couvrant les 404 variables. En attendant :
    //   — le gabarit lui-même reste couvert par StatutsRefondusTest, qui le rend
    //     par la voie MODIFICATION (STATUTS_REFONDUS_*, même fichier) ;
    //   — le rendu des 23 nouveaux gabarits est relevé par
    //     ma.jurika.ai.lotA.CorpusCreation0909RenduTest.
    // ---------------------------------------------------------------------

}

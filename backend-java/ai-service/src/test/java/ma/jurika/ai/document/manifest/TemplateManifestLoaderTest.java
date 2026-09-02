package ma.jurika.ai.document.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du loader manifest L3 (sans Spring context : on appelle
 * {@code @PostConstruct} manuellement via {@link TemplateManifestLoader#load()}).
 */
class TemplateManifestLoaderTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private TemplateManifestLoader newLoaderDefault() {
        TemplateManifestLoader loader = new TemplateManifestLoader(objectMapper);
        loader.load();
        return loader;
    }

    @Test
    void test_load_manifest_ok() {
        TemplateManifestLoader loader = newLoaderDefault();
        // Total >= 27 codes (alias historiques retirés en Phase D ; avenant
        // STATUTS_MODIFIES_* et orphelin PV_OUVERTURE_COMPTE_BANCAIRE retirés — audit directeur).
        assertTrue(loader.allCodes().size() >= 27,
                "Expected >= 27 codes, got " + loader.allCodes().size());
        // Templates d'origine directeur (famille "directeur" + "directeur-2026-08") >= 13.
        // Les migrations successives (retrait CRÉATION LEGACY, voie AGO « affectation »,
        // et Phase C dissolution/liquidation) ont remplacé plusieurs entrées "directeur"
        // par des entrées "directeur-2026-08" ; on compte donc toute la famille directeur.
        long directeurs = loader.allCodes().stream()
                .map(c -> loader.resolve(c).orElseThrow())
                .filter(e -> e.origin() != null && e.origin().startsWith("directeur"))
                .distinct()
                .count();
        assertTrue(directeurs >= 13,
                "Expected >= 13 templates directeurs distincts, got " + directeurs);
    }

    @Test
    void test_legacy_succursale_codes_removed() {
        // Phase D : les modèles succursale remplacés (PV/décision/alias) ont été retirés.
        TemplateManifestLoader loader = newLoaderDefault();
        for (String legacy : new String[]{"PV_OUVERTURE_SUCCURSALE", "PV_AGE_OUVERTURE_SUCCURSALE",
                "PV_AGE_FERMETURE_SUCCURSALE", "DECISION_CONSEIL_ETRANGER"}) {
            assertTrue(loader.resolve(legacy).isEmpty(), legacy + " doit avoir été retiré");
        }
        // Les 6 PV directeur Phase D sont présents et résolus directement.
        for (String code : new String[]{
                "PV_CREATION_SUCCURSALE_MAROC_SARL", "PV_CREATION_SUCCURSALE_MAROC_SARL_AU",
                "PV_CREATION_SUCCURSALE_ETRANGERE_SARL", "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                "PV_FERMETURE_SUCCURSALE_SARL", "PV_FERMETURE_SUCCURSALE_SARL_AU"}) {
            Optional<TemplateManifest.TemplateEntry> r = loader.resolve(code);
            assertTrue(r.isPresent(), code + " doit être présent");
            assertEquals(code + ".docx", r.get().file());
        }
        // Lot DIVERS §B (2026-08-13) : le compagnon LEGACY ANNONCE_JAL_OUVERTURE_SUCCURSALE
        // est RETIRÉ — le directeur a livré son remplaçant, et deux avis d'ouverture
        // concurrents pour le même acte n'auraient aucun sens.
        assertTrue(loader.resolve("ANNONCE_JAL_OUVERTURE_SUCCURSALE").isEmpty(),
                "ANNONCE_JAL_OUVERTURE_SUCCURSALE doit avoir été retiré (doublon)");
        for (String code : new String[]{
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL",
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU"}) {
            Optional<TemplateManifest.TemplateEntry> r = loader.resolve(code);
            assertTrue(r.isPresent(), code + " doit être présent");
            assertEquals(code + ".docx", r.get().file());
        }
    }

    @Test
    void test_resolve_direct() {
        TemplateManifestLoader loader = newLoaderDefault();
        // Phase E2 : PV_AGE_SARL a été retiré. On vérifie un code direct présent —
        // le statut refondu (voie directeur) pointe sur le docx déterministe directeur.
        Optional<TemplateManifest.TemplateEntry> resolved = loader.resolve("STATUTS_REFONDUS_SARL");
        assertTrue(resolved.isPresent());
        TemplateManifest.TemplateEntry entry = resolved.get();
        assertEquals("STATUTS_REFONDUS_SARL", entry.code());
        assertNotNull(entry.file(), "Le code direct doit exposer un file");
        assertEquals("STATUTS_SARL_modele_deterministe.docx", entry.file());
    }

    @Test
    void test_resolve_unknown() {
        TemplateManifestLoader loader = newLoaderDefault();
        assertTrue(loader.resolve("CODE_INEXISTANT_XYZ").isEmpty());
        assertTrue(loader.resolve(null).isEmpty());
    }

    @Test
    void test_dictionary_loaded() {
        TemplateManifestLoader loader = newLoaderDefault();
        DictionaryManifest dict = loader.dictionary();
        assertNotNull(dict);
        assertNotNull(dict.variables());
        assertTrue(dict.variables().size() >= 60,
                "Expected >= 60 variables, got " + dict.variables().size());
        assertNotNull(dict.blocks());
        assertTrue(dict.blocks().size() >= 3,
                "Expected >= 3 blocs, got " + dict.blocks().size());
    }

    @Test
    void test_cycle_protection() {
        TemplateManifestLoader cyclic = new TemplateManifestLoader(
                objectMapper,
                "templates/v2/manifest_test.json",
                "templates/v2/dictionary_test.json");
        cyclic.load();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cyclic.resolve("A"));
        assertTrue(ex.getMessage().toLowerCase().contains("cycle")
                        || ex.getMessage().toLowerCase().contains("trop longue"),
                "Message attendu (cycle/trop longue) : " + ex.getMessage());
    }
}

package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires (sans Spring) du {@link CreationSarlMapper} après Phase E2.
 *
 * <p>Le mapper ne fait plus que router les 4 codes déterministes du directeur vers
 * {@link CreationDirecteurVarsBuilder} — la voie LEGACY {@code STATUTS_CONSTITUTIFS_*}
 * ({{}}) a été retirée. Le rendu détaillé des variables directeur est couvert par
 * {@code CreationDirecteurRenderTest} / {@code CreationDirecteurVarsBuilder} tests.
 */
class CreationSarlMapperTest {

    private CreationSarlMapper mapper;
    private Map<String, Object> payload;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        this.mapper = new CreationSarlMapper();
        try (InputStream in = getClass()
                .getResourceAsStream("/workflow-fixtures/creation_sarl.json")) {
            assertNotNull(in, "Fixture creation_sarl.json introuvable");
            this.payload = new ObjectMapper().readValue(in, Map.class);
        }
    }

    @Test
    void workflowCode_is_creation_sarl() {
        assertEquals("CREATION_SARL", mapper.workflowCode());
    }

    @Test
    void supportedTemplates_contains_only_the_four_directeur_codes() {
        // Phase E2 : retrait des 2 STATUTS_CONSTITUTIFS_* (voie LEGACY {{}}). Restent
        // exclusivement les 4 modèles déterministes du directeur.
        Set<String> supported = mapper.supportedTemplates();
        assertTrue(supported.contains("STATUTS_SARL_DIRECTEUR"));
        assertTrue(supported.contains("STATUTS_SARL_AU_DIRECTEUR"));
        assertTrue(supported.contains("ACTE_NOMINATION_GERANT_DIRECTEUR"));
        assertTrue(supported.contains("ANNONCE_LEGALE_DIRECTEUR"));
        // Les codes LEGACY ne doivent plus être exposés.
        assertFalse(supported.contains("STATUTS_CONSTITUTIFS_SARL"));
        assertFalse(supported.contains("STATUTS_CONSTITUTIFS_SARL_AU"));
        assertEquals(4, supported.size());
    }

    @Test
    void unknown_template_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.map("FOOBAR", payload));
        assertTrue(ex.getMessage().contains("non supporté"));
        // Le code LEGACY retiré n'est plus supporté non plus.
        assertThrows(IllegalArgumentException.class,
                () -> mapper.map("STATUTS_CONSTITUTIFS_SARL", payload));
    }

    @Test
    void map_statuts_directeur_sarl_emits_dictionary_vars() {
        Map<String, Object> vars = mapper.map("STATUTS_SARL_DIRECTEUR", payload);
        // Variables du dictionnaire officiel directeur ($UPPER).
        assertEquals("TEST SARL", vars.get("DENOMINATION"));
        assertNotNull(vars.get("ASSOCIE_UNIQUE"));
        assertTrue(vars.containsKey("CAPITAL_CHIFFRES"));
        assertTrue(vars.containsKey("CAPITAL_LETTRES"));
        assertTrue(vars.containsKey("GERANTS"));
        assertTrue(vars.containsKey("ASSOCIES"));
    }

    @Test
    void map_statuts_directeur_sarl_au_emits_dictionary_vars() {
        Map<String, Object> vars = mapper.map("STATUTS_SARL_AU_DIRECTEUR", payload);
        assertEquals("oui", vars.get("ASSOCIE_UNIQUE"));
        assertTrue(vars.containsKey("DENOMINATION"));
    }

    /**
     * Regression 2026-06-09 — societe.denomination sous forme d'objet imbriqué
     * (draft Step1 rehydrate) : le builder directeur doit dérouler la string.
     */
    @Test
    void map_unwraps_nested_denomination_object() {
        Map<String, Object> nestedSociete = new java.util.HashMap<>();
        Map<String, Object> nestedDen = new java.util.LinkedHashMap<>();
        nestedDen.put("denomination", "PARACOSME TRANSP");
        nestedDen.put("ice", "123456789123123");
        nestedDen.put("formeJuridique", "SARL");
        nestedSociete.put("denomination", nestedDen);
        nestedSociete.put("capitalChiffres", 100000L);
        Map<String, Object> badPayload = new java.util.HashMap<>();
        badPayload.put("societe", nestedSociete);

        Map<String, Object> vars = mapper.map("STATUTS_SARL_DIRECTEUR", badPayload);
        assertEquals("PARACOSME TRANSP", vars.get("DENOMINATION"),
                "L'unwrap doit produire la string et non la Map serialisée");
        assertFalse(String.valueOf(vars.get("DENOMINATION")).startsWith("{"));
    }
}

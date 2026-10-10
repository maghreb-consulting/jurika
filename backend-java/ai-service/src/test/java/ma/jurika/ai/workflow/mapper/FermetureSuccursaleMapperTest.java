package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitaire (no Spring) du mapper {@link FermetureSuccursaleMapper}.
 */
class FermetureSuccursaleMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;

    private final FermetureSuccursaleMapper mapper = new FermetureSuccursaleMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = FermetureSuccursaleMapperTest.class.getResourceAsStream(
                "/workflow-fixtures/fermeture_succursale.json")) {
            if (is == null) {
                throw new IllegalStateException("Fixture fermeture_succursale.json introuvable");
            }
            fixtures = JSON.readValue(is, Map.class);
        }
    }

    @Test
    @DisplayName("Contrat : workflow FERMETURE_SUCCURSALE + 2 PV + 2 annonces de fermeture")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("FERMETURE_SUCCURSALE");
        assertThat(mapper.supportedTemplates()).containsExactlyInAnyOrder(
                "PV_FERMETURE_SUCCURSALE_SARL",
                "PV_FERMETURE_SUCCURSALE_SARL_AU",
                "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL",
                "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU");
    }

    @Test
    @DisplayName("Annonce de fermeture : mêmes faits succursale que le PV, motif PUBLIÉ")
    void mapsAnnonceFermeture() {
        Map<String, Object> payload = fixtures.get("PV_FERMETURE_SUCCURSALE_SARL");
        Map<String, Object> pv = mapper.map("PV_FERMETURE_SUCCURSALE_SARL", payload);
        Map<String, Object> annonce =
                mapper.map("ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL", payload);

        assertThat(annonce.get("DENOMINATION")).isEqualTo("PARACOSME");
        // Le motif est publié dans l'avis de fermeture (contrairement à la dissolution).
        assertThat(annonce.get("SUCCURSALE_MOTIF")).asString().contains("réorganisation");
        for (String key : new String[]{
                "SUCCURSALE_ENSEIGNE", "SUCCURSALE_ADRESSE", "SUCCURSALE_VILLE",
                "SUCCURSALE_VILLE_GREFFE", "SUCCURSALE_RC_NUMERO",
                "SUCCURSALE_DATE_FERMETURE", "SUCCURSALE_MOTIF", "ASSEMBLEE_DATE"}) {
            assertThat(annonce.get(key)).as(key).isEqualTo(pv.get(key));
        }
        // Dépôt légal : inconnu à la génération -> marqueur explicite (2026-08-17).
        // Rendu VIDE, il laissait « … le  sous le numéro  RC N° … » : un trou muet.
        assertThat(annonce.get("DATE_DEPOT_LEGAL")).as("L3 : donnee externe absente, marquee par le moteur").satisfiesAnyOf(x -> assertThat(x).isNull(), x -> assertThat(x.toString()).isBlank());
        assertThat(annonce.get("DEPOT_LEGAL_NUMERO")).as("L3 : donnee externe absente, marquee par le moteur").satisfiesAnyOf(x -> assertThat(x).isNull(), x -> assertThat(x.toString()).isBlank());
        // Avis linéaire : aucune résolution.
        assertThat((List<?>) annonce.get("RESOLUTIONS")).isEmpty();
    }

    @Test
    @DisplayName("Template inconnu → IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("UNKNOWN", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
    }

    @Test
    @DisplayName("SARL : identité BD + fermeture (RC/date/motif) + responsable → 3 résolutions")
    @SuppressWarnings("unchecked")
    void mapsSarl() {
        Map<String, Object> vars = mapper.map("PV_FERMETURE_SUCCURSALE_SARL",
                fixtures.get("PV_FERMETURE_SUCCURSALE_SARL"));

        assertThat(vars.get("DENOMINATION")).isEqualTo("PARACOSME");
        assertThat(vars.get("SUCCURSALE_RC_NUMERO")).isEqualTo("78901");
        assertThat(vars.get("SUCCURSALE_DATE_FERMETURE")).isEqualTo("31/10/2026");
        assertThat(vars.get("SUCCURSALE_MOTIF")).asString().contains("réorganisation");
        assertThat(vars.get("SUCCURSALE_RESPONSABLE_PRESENT")).isEqualTo("oui");

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertThat(res).hasSize(3); // fermeture + cessation + formalités
        assertThat(res.get(0).get("RESOLUTION_TEXTE")).asString().contains("fermer la succursale");
        assertThat(res.get(1).get("RESOLUTION_INTITULE")).asString().contains("Cessation");
    }

    @Test
    @DisplayName("SARL AU : sans responsable → 2 décisions")
    @SuppressWarnings("unchecked")
    void mapsSarlAu() {
        Map<String, Object> vars = mapper.map("PV_FERMETURE_SUCCURSALE_SARL_AU",
                fixtures.get("PV_FERMETURE_SUCCURSALE_SARL_AU"));

        assertThat(vars.get("ASSOCIE_TYPE")).isEqualTo("personne physique");
        assertThat(vars.get("SUCCURSALE_RESPONSABLE_PRESENT")).isEqualTo("non");

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertThat(res).hasSize(2);
        assertThat(res.get(0).get("RESOLUTION_NUMERO")).isEqualTo("PREMIÈRE DÉCISION");
    }
}

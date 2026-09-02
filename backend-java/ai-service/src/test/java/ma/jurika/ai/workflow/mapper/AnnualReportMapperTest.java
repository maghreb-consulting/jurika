package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnualReportMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;

    private final AnnualReportMapper mapper = new AnnualReportMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = AnnualReportMapperTest.class.getResourceAsStream(
                "/workflow-fixtures/annual_report.json")) {
            if (is == null) {
                throw new IllegalStateException("Fixture annual_report.json introuvable");
            }
            fixtures = JSON.readValue(is, Map.class);
        }
    }

    @Test
    @DisplayName("Contrat L4 : workflow ANNUAL_REPORT + 1 template RAPPORT_GESTION")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("ANNUAL_REPORT");
        assertThat(mapper.supportedTemplates()).containsExactly("RAPPORT_GESTION");
    }

    @Test
    @DisplayName("Template inconnu → IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("UNKNOWN", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
    }

    @Test
    @DisplayName("RAPPORT_GESTION : header + exercice + gérant + signature")
    void mapsRapportGestion() {
        Map<String, Object> payload = fixtures.get("RAPPORT_GESTION");
        Map<String, Object> vars = mapper.map("RAPPORT_GESTION", payload);

        // Header société
        assertThat(vars.get("DENOMINATION")).isEqualTo("ACME SARL");
        assertThat(vars.get("ACTIVITE_SOCIETE"))
                .isEqualTo("Conseil juridique et fiscal aux entreprises");
        assertThat(vars.get("CAPITAL_CHIFFRES")).isEqualTo(100_000L);
        assertThat(vars.get("CAPITAL_LETTRES")).asString().contains("DIRHAMS");

        // Exercice
        assertThat(vars.get("DATE_CLOTURE")).isEqualTo("31/12/2025");
        assertThat(vars.get("ANNEE_LETTRES")).isEqualTo("DEUX MILLE VINGT-CINQ");
        assertThat(vars.get("RESULTAT_MONTANT")).isEqualTo(250_000L);
        assertThat(vars.get("CHIFFRE_AFFAIRES")).isEqualTo(1_200_000L);
        assertThat(vars.get("RESERVE_LEGALE")).isEqualTo(12_500L);
        assertThat(vars.get("DIVIDENDES")).isEqualTo(150_000L);
        assertThat(vars.get("REPORT_A_NOUVEAU")).isEqualTo(87_500L);
        assertThat(vars.get("COMMENTAIRE_ACTIVITE")).asString().contains("croissance");
        assertThat(vars.get("EVENEMENTS_PERSPECTIVES")).asString().contains("Tanger");

        // Gérant + signature
        assertThat(vars.get("GERANT_NOM")).isEqualTo("Karim BENALI");
        assertThat(vars.get("LIEU_DATE_EMISSION")).asString().contains("Casablanca");
    }

    @Test
    @DisplayName("Payload null → map vide sans crasher")
    void tolerantToNullPayload() {
        Map<String, Object> vars = mapper.map("RAPPORT_GESTION", null);
        assertThat(vars).isEmpty();
    }
}

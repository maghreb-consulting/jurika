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

class SuccursaleMaMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;

    private final SuccursaleMaMapper mapper = new SuccursaleMaMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = SuccursaleMaMapperTest.class.getResourceAsStream(
                "/workflow-fixtures/succursale_ma.json")) {
            if (is == null) {
                throw new IllegalStateException("Fixture succursale_ma.json introuvable");
            }
            fixtures = JSON.readValue(is, Map.class);
        }
    }

    @Test
    @DisplayName("Contrat : workflow SUCCURSALE_MA + 2 PV directeur + 2 annonces d'ouverture")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("SUCCURSALE_MA");
        assertThat(mapper.supportedTemplates()).containsExactlyInAnyOrder(
                "PV_CREATION_SUCCURSALE_MAROC_SARL",
                "PV_CREATION_SUCCURSALE_MAROC_SARL_AU",
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL",
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU");
    }

    @Test
    @DisplayName("Lot DIVERS §B : le compagnon LEGACY ANNONCE_JAL_OUVERTURE_SUCCURSALE est retiré")
    void legacyJalTemplateIsGone() {
        // Garde-fou anti-doublon : deux avis d'ouverture concurrents pour le même acte
        // auraient pu être générés (variables incompatibles, aucun bloc conditionnel).
        assertThat(mapper.supportedTemplates()).doesNotContain("ANNONCE_JAL_OUVERTURE_SUCCURSALE");
        assertThatThrownBy(() -> mapper.map("ANNONCE_JAL_OUVERTURE_SUCCURSALE", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Annonce d'ouverture : identité BD + bloc succursale IDENTIQUE au PV, sans résolutions")
    void mapsAnnonceOuverture() {
        Map<String, Object> payload = fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL");
        Map<String, Object> pv = mapper.map("PV_CREATION_SUCCURSALE_MAROC_SARL", payload);
        Map<String, Object> annonce =
                mapper.map("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL", payload);

        assertThat(annonce.get("DENOMINATION")).isEqualTo("PARACOSME");
        assertThat(annonce.get("ASSEMBLEE_DATE")).isEqualTo(pv.get("ASSEMBLEE_DATE"));
        // L'avis et l'acte partagent EXACTEMENT les mêmes faits succursale : aucune
        // divergence possible entre ce qui est décidé et ce qui est publié.
        for (String key : new String[]{
                "SUCCURSALE_ENSEIGNE", "SUCCURSALE_ADRESSE", "SUCCURSALE_VILLE",
                "SUCCURSALE_ACTIVITE", "SUCCURSALE_DATE_OUVERTURE", "SUCCURSALE_VILLE_GREFFE",
                "SUCCURSALE_DOTATION_PRESENTE", "SUCCURSALE_DOTATION_CHIFFRES",
                "SUCCURSALE_DOTATION_LETTRES", "SUCCURSALE_RESPONSABLE_PRESENT"}) {
            assertThat(annonce.get(key)).as(key).isEqualTo(pv.get(key));
        }
        assertThat(annonce.get("SUCCURSALE_RESPONSABLE_NOM")).asString().contains("TAZI");
        assertThat(annonce.get("SUCCURSALE_RESPONSABLE_POUVOIRS")).asString().isNotBlank();
        // L'avis ne porte AUCUNE résolution : le noyau séance expose la clé (vide),
        // mais la boucle du PV (ouverture / responsable / formalités) n'est pas construite.
        assertThat((List<?>) annonce.get("RESOLUTIONS")).isEmpty();
        assertThat((List<?>) pv.get("RESOLUTIONS")).hasSize(3);
    }

    @Test
    @DisplayName("Dépôt légal inconnu à la génération → rendu VIDE (aucun marqueur résiduel)")
    void depotLegalRenduVideParDefaut() {
        Map<String, Object> annonce = mapper.map("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL",
                fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL"));
        // 2026-08-17 — le greffe attribue ces valeurs APRÈS le dépôt : marqueur
        // explicite au lieu d'un blanc (« … le  sous le numéro  »).
        assertThat(annonce.get("DATE_DEPOT_LEGAL")).isEqualTo("[à compléter après immatriculation]");
        assertThat(annonce.get("DEPOT_LEGAL_NUMERO")).isEqualTo("[à compléter après immatriculation]");
    }

    @Test
    @DisplayName("SARL AU : même jeu de variables, seul le chapeau du modèle diffère")
    void annonceAuMemesVariables() {
        Map<String, Object> sarl = mapper.map("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL",
                fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL_AU"));
        Map<String, Object> au = mapper.map("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU",
                fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL_AU"));
        for (String key : new String[]{
                "SUCCURSALE_ENSEIGNE", "SUCCURSALE_ADRESSE", "SUCCURSALE_VILLE",
                "SUCCURSALE_VILLE_GREFFE", "SUCCURSALE_DOTATION_PRESENTE",
                "SUCCURSALE_RESPONSABLE_PRESENT", "DATE_DEPOT_LEGAL", "DEPOT_LEGAL_NUMERO"}) {
            assertThat(au.get(key)).as(key).isEqualTo(sarl.get(key));
        }
    }

    @Test
    @DisplayName("Template inconnu → IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("UNKNOWN", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
    }

    @Test
    @DisplayName("SARL pluri : identité BD + succursale + dotation + responsable + RESOLUTIONS")
    @SuppressWarnings("unchecked")
    void mapsSarl() {
        Map<String, Object> vars = mapper.map("PV_CREATION_SUCCURSALE_MAROC_SARL",
                fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL"));

        assertThat(vars.get("DENOMINATION")).isEqualTo("PARACOSME");
        assertThat(vars.get("CAPITAL_LETTRES")).asString().isNotBlank();
        assertThat(vars.get("SUCCURSALE_ENSEIGNE")).asString().contains("Marrakech");
        assertThat(vars.get("SUCCURSALE_VILLE_GREFFE")).isEqualTo("MARRAKECH");
        assertThat(vars.get("SUCCURSALE_DOTATION_PRESENTE")).isEqualTo("oui");
        assertThat(vars.get("SUCCURSALE_DOTATION_LETTRES")).asString().isNotBlank();
        assertThat(vars.get("SUCCURSALE_RESPONSABLE_PRESENT")).isEqualTo("oui");

        List<Map<String, Object>> associes = (List<Map<String, Object>>) vars.get("ASSOCIES");
        assertThat(associes).hasSize(2);
        assertThat(associes.get(0).get("ASSOCIE_NOM")).asString().contains("BENALI");

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        // ouverture + responsable + formalités
        assertThat(res).hasSize(3);
        assertThat(res.get(0).get("RESOLUTION_NUMERO")).isEqualTo("PREMIÈRE RÉSOLUTION");
        assertThat(res.get(1).get("RESOLUTION_INTITULE")).asString().contains("responsable");
        assertThat(res.get(2).get("RESOLUTION_TEXTE")).asString().contains("registre du commerce");
    }

    @Test
    @DisplayName("SARL AU : associé unique hors boucle + GERANTS + sans dotation/responsable → 2 résolutions")
    @SuppressWarnings("unchecked")
    void mapsSarlAu() {
        Map<String, Object> vars = mapper.map("PV_CREATION_SUCCURSALE_MAROC_SARL_AU",
                fixtures.get("PV_CREATION_SUCCURSALE_MAROC_SARL_AU"));

        assertThat(vars.get("ASSOCIE_TYPE")).isEqualTo("personne physique");
        assertThat(vars.get("ASSOCIE_NOM")).asString().contains("CHERKAOUI");
        assertThat(vars.get("SUCCURSALE_DOTATION_PRESENTE")).isEqualTo("non");

        List<Map<String, Object>> gerants = (List<Map<String, Object>>) vars.get("GERANTS");
        assertThat(gerants).hasSize(1);

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertThat(res).hasSize(2); // pas de responsable → ouverture + formalités
        assertThat(res.get(0).get("RESOLUTION_NUMERO")).isEqualTo("PREMIÈRE DÉCISION");
        assertThat(res.get(0).get("RESOLUTION_VOIX_POUR")).isEqualTo(""); // AU : pas de voix
    }

    @Test
    @DisplayName("Payload null : pas de crash, RESOLUTIONS présentes")
    @SuppressWarnings("unchecked")
    void tolerantToNullPayload() {
        Map<String, Object> vars = mapper.map("PV_CREATION_SUCCURSALE_MAROC_SARL", null);
        assertThat(vars).containsKey("RESOLUTIONS");
        assertThat((List<?>) vars.get("RESOLUTIONS")).isNotEmpty();
    }
}

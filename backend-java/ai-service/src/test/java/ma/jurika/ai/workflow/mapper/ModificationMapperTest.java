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
 * Test unitaire (no Spring) du mapper {@link ModificationMapper} après Phase E2.
 *
 * <p>Templates supportés : PV de Modification directeur (E1) et statut refondu
 * directeur (E2, {@code STATUTS_REFONDUS_*}). Les voies LEGACY {@code PV_AGE_*},
 * {@code CONVOCATION_ASSOCIES}, {@code FEUILLE_DE_PRESENCE} et l'avenant
 * {@code STATUTS_MODIFIES_*} (doublon des refondus) ont été retirés.
 */
class ModificationMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;

    private final ModificationMapper mapper = new ModificationMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = ModificationMapperTest.class.getResourceAsStream(
                "/workflow-fixtures/modification.json")) {
            if (is == null) {
                throw new IllegalStateException("Fixture modification.json introuvable");
            }
            fixtures = JSON.readValue(is, Map.class);
        }
    }

    @Test
    @DisplayName("Contrat L4 : workflow MODIFICATION + 6 templates (E1 PV + E2 refonte + Phase 2 annonce)")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("MODIFICATION");
        assertThat(mapper.supportedTemplates()).containsExactlyInAnyOrder(
                "PV_MODIFICATION_SARL",
                "PV_MODIFICATION_SARL_AU",
                "STATUTS_REFONDUS_SARL",
                "STATUTS_REFONDUS_SARL_AU",
                "ANNONCE_LEGALE_MODIFICATION_SARL",
                "ANNONCE_LEGALE_MODIFICATION_SARL_AU"
        );
    }

    @Test
    @DisplayName("Phase 2 : ANNONCE_LEGALE_MODIFICATION_* routé vers le builder d'annonce (boucle DECISIONS)")
    @SuppressWarnings("unchecked")
    void mapsAnnonceModification() {
        Map<String, Object> payload = Map.of(
                "formeJuridique", "SARL",
                "societe", Map.of("denomination", "PARACOSME", "capitalChiffres", 100000,
                        "siegeSocial", "Casablanca", "rcNumero", "123456", "villeGreffe", "CASABLANCA"),
                "seance", Map.of("type", "extraordinaire", "date", "2026-05-15"),
                "resolutions", List.of(
                        Map.of("type", "modification_denomination", "nouvelleDenomination", "NEWCO"),
                        Map.of("type", "commissaire_comptes"))); // non publiable → filtré

        Map<String, Object> vars = mapper.map("ANNONCE_LEGALE_MODIFICATION_SARL", payload);
        assertThat(vars).containsKey("DECISIONS");
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) vars.get("DECISIONS");
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0)).containsEntry("DECISION_TYPE", "denomination");
    }

    @Test
    @DisplayName("Template inconnu (dont voies LEGACY retirées) → IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("PV_AGE_SARL", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
        assertThatThrownBy(() -> mapper.map("CONVOCATION_ASSOCIES", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Avenant STATUTS_MODIFIES_* retiré (doublon des refondus) → IllegalArgumentException")
    void rejectsRemovedStatutsModifies() {
        assertThatThrownBy(() -> mapper.map("STATUTS_MODIFIES_SARL", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
        assertThatThrownBy(() -> mapper.map("STATUTS_MODIFIES_SARL_AU", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("STATUTS_REFONDUS_SARL (E2) : routé vers la voie DIRECTEUR (variables $UPPER) ; overlay gagne")
    @SuppressWarnings("unchecked")
    void mapsStatutsRefondusViaDirecteur() {
        Map<String, Object> payload = Map.of(
                "ficheStructuree", Map.of(
                        "denomination", "ANCIENNE DENOMINATION SARL",
                        "formeJuridique", "SARL",
                        "objetSocial", "Conseil",
                        "adresseSiege", "12 rue A, Casablanca",
                        "capitalSocial", 100000,
                        "valeurNominale", 100,
                        "nombreParts", 1000,
                        "dureeAnnees", 99,
                        "gerants", List.of(Map.of("nom", "BENALI", "prenom", "Karim"))),
                "modifications", List.of(Map.of(
                        "typeId", "CHANGEMENT_DENOMINATION",
                        "details", Map.of("nouvelleDenomination", "NOUVELLE DENOMINATION SARL"))));

        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", payload);

        // Voie directeur : variables du dictionnaire officiel en $UPPER.
        assertThat(vars).containsKey("DENOMINATION");
        assertThat(vars).containsKey("OBJET_SOCIAL");
        assertThat(vars).containsKey("ASSOCIE_UNIQUE");
        // Overlay gagne : la nouvelle dénomination écrase l'ancienne.
        assertThat(vars.get("DENOMINATION")).isEqualTo("NOUVELLE DENOMINATION SARL");
        // Les boucles directeur sont présentes.
        assertThat(vars).containsKey("GERANTS");
        assertThat((List<Map<String, Object>>) vars.get("GERANTS")).isNotEmpty();
    }
}

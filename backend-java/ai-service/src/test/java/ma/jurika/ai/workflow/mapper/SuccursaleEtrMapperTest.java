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

class SuccursaleEtrMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;

    private final SuccursaleEtrMapper mapper = new SuccursaleEtrMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = SuccursaleEtrMapperTest.class.getResourceAsStream(
                "/workflow-fixtures/succursale_etr.json")) {
            if (is == null) {
                throw new IllegalStateException("Fixture succursale_etr.json introuvable");
            }
            fixtures = JSON.readValue(is, Map.class);
        }
    }

    @Test
    @DisplayName("Contrat : workflow SUCCURSALE_ETR + 2 PV directeur + 2 annonces DÉRIVÉES")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("SUCCURSALE_ETR");
        assertThat(mapper.supportedTemplates()).containsExactlyInAnyOrder(
                "PV_CREATION_SUCCURSALE_ETRANGERE_SARL",
                "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL",
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU");
    }

    @Test
    @DisplayName("Annonce étrangère : identité de la mère SAISIE + organe, sans variable marocaine")
    void mapsAnnonceEtrangere() {
        Map<String, Object> payload = fixtures.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL");
        Map<String, Object> pv = mapper.map("PV_CREATION_SUCCURSALE_ETRANGERE_SARL", payload);
        Map<String, Object> annonce =
                mapper.map("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL", payload);

        assertThat(annonce.get("SOCIETE_MERE_DENOMINATION")).isEqualTo("GLOBAL TRADING LTD");
        assertThat(annonce.get("SOCIETE_MERE_PAYS")).isEqualTo("Royaume-Uni");
        assertThat(annonce.get("ORGANE_COMPETENT")).isEqualTo("le conseil d'administration");
        // Le corps succursale est produit par les MÊMES helpers que le PV : l'avis et
        // l'acte ne peuvent pas diverger.
        for (String key : new String[]{
                "SUCCURSALE_ENSEIGNE", "SUCCURSALE_ADRESSE", "SUCCURSALE_VILLE",
                "SUCCURSALE_ACTIVITE", "SUCCURSALE_DATE_OUVERTURE", "SUCCURSALE_VILLE_GREFFE",
                "SUCCURSALE_DOTATION_PRESENTE", "SUCCURSALE_DOTATION_CHIFFRES",
                "SUCCURSALE_DOTATION_LETTRES",
                "SOCIETE_MERE_FORME", "SOCIETE_MERE_SIEGE", "SOCIETE_MERE_REGISTRE",
                "SOCIETE_MERE_REGISTRE_NUMERO", "SOCIETE_MERE_LOI_APPLICABLE"}) {
            assertThat(annonce.get(key)).as(key).isEqualTo(pv.get(key));
        }
        // Dépôt légal : inconnu à la génération -> rendu vide.
        // 2026-08-17 — le greffe attribue ces valeurs APRÈS le dépôt : marqueur
        // explicite au lieu d'un blanc (« … le  sous le numéro  »).
        assertThat(annonce.get("DATE_DEPOT_LEGAL")).isEqualTo("[à compléter après immatriculation]");
        assertThat(annonce.get("DEPOT_LEGAL_NUMERO")).isEqualTo("[à compléter après immatriculation]");
        // L'avis ne porte aucune résolution.
        assertThat((List<?>) annonce.get("RESOLUTIONS")).isEmpty();
    }

    @Test
    @DisplayName("Annonce étrangère : le représentant résident alimente le bloc responsable")
    void representantAlimenteLeResponsable() {
        Map<String, Object> annonce = mapper.map(
                "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL",
                fixtures.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL"));
        // Le PV étranger porte le représentant sous `representant` ; l'avis publie un
        // « responsable ». Sans reprise, la mention serait vide alors que la donnée existe.
        assertThat(annonce.get("SUCCURSALE_RESPONSABLE_PRESENT")).isEqualTo("oui");
        assertThat(annonce.get("SUCCURSALE_RESPONSABLE_NOM")).asString().contains("EL FASSI");
    }

    @Test
    @DisplayName("Template inconnu → IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("UNKNOWN", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non supporté");
    }

    @Test
    @DisplayName("Organe collégial : société mère saisie + organe + représentant + RESOLUTIONS")
    @SuppressWarnings("unchecked")
    void mapsSarl() {
        Map<String, Object> vars = mapper.map("PV_CREATION_SUCCURSALE_ETRANGERE_SARL",
                fixtures.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL"));

        assertThat(vars.get("SOCIETE_MERE_DENOMINATION")).isEqualTo("GLOBAL TRADING LTD");
        // Grammaire d'assemblage (2026-08-17) — ATTENTE CORRIGÉE. Le PV écrit
        // « $SOCIETE_MERE_FORME de droit $SOCIETE_MERE_PAYS » : cette locution appelle un
        // ADJECTIF. Injecter le nom du pays donnait « Limited de droit Royaume-Uni ».
        // L'ANNONCE, elle, publie « Siège social : …, $SOCIETE_MERE_PAYS » et garde le NOM
        // du pays (cf. SuccursaleEtrAnnonceRenderTest) — c'est le modèle appelant qui
        // tranche, pas la saisie.
        assertThat(vars.get("SOCIETE_MERE_PAYS")).isEqualTo("britannique");
        assertThat(vars.get("ORGANE_COMPETENT")).isEqualTo("le conseil d'administration");
        assertThat(vars.get("ORGANE_DECISION_DATE")).isEqualTo("10/09/2026");
        assertThat(vars.get("ORGANE_DECISION_LIEU")).isEqualTo("Londres");
        assertThat(vars.get("SUCCURSALE_VILLE_GREFFE")).isEqualTo("CASABLANCA");
        // Pas de société marocaine, pas d'associés marocains.
        assertThat((List<?>) vars.get("ASSOCIES")).isEmpty();

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertThat(res).hasSize(3);
        assertThat(res.get(1).get("RESOLUTION_TEXTE")).asString().contains("EL FASSI");
        assertThat(res.get(0).get("RESOLUTION_VOIX_POUR")).isEqualTo("5");
    }

    @Test
    @DisplayName("Organe unique (AU) : sans décompte de voix")
    @SuppressWarnings("unchecked")
    void mapsSarlAu() {
        Map<String, Object> vars = mapper.map("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                fixtures.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU"));

        assertThat(vars.get("SOCIETE_MERE_DENOMINATION")).isEqualTo("MEDITERRANEA GMBH");
        assertThat(vars.get("ORGANE_COMPETENT")).isEqualTo("le gérant unique");
        assertThat(vars.get("PRESIDENT_SEANCE_NOM")).asString().contains("MÜLLER");

        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertThat(res.get(0).get("RESOLUTION_VOIX_POUR")).isEqualTo(""); // AU : pas de voix
    }
}

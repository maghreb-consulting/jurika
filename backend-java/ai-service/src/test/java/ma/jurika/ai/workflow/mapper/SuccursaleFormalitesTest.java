package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4a (E1) : le mandataire des formalites et le nombre d'originaux des PV de succursale.
 * Revele par le controle strict des documents : saisis a l'ecran, ils n'atteignaient pas
 * le modele du corpus ($FORMALITES_MANDATAIRE_NOM, $NOMBRE_ORIGINAUX) ; et le mandataire
 * absent etait remplace en silence par le president de seance, puis par un texte fixe.
 */
class SuccursaleFormalitesTest {

    private static Map<String, Object> payload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", Map.of("denomination", "PARACOSME", "formeJuridique", "SARL_AU"));
        p.put("seance", Map.of("date", "2026-09-05", "presidentNom", "M. Karim BENALI"));
        p.put("associes", List.of(Map.of("civilite", "M.", "prenom", "Karim", "nom", "BENALI",
                "nombreParts", 1000, "presence", "pr\u00e9sent")));
        p.put("succursale", Map.of("enseigne", "PCM", "adresse", "1 rue X", "ville", "Rabat",
                "activite", "le conseil", "dateOuverture", "2026-10-01"));
        return p;
    }

    @Test
    void mandataire_et_originaux_saisis_atteignent_le_modele() {
        Map<String, Object> p = payload();
        p.put("formalitesMandataireNom", "Mme Salma IDRISSI");
        p.put("nombreOriginaux", "quatre");
        Map<String, Object> v = SuccursaleVarsBuilder.ouvertureMaVars("PV_CREATION_SUCCURSALE_MAROC_SARL_AU", p);
        assertThat(v).containsEntry("FORMALITES_MANDATAIRE_NOM", "Mme Salma IDRISSI")
                .containsEntry("NOMBRE_ORIGINAUX", "quatre");
    }

    @Test
    void mandataire_absent_n_est_pas_suppose_la_generation_le_nomme() {
        // Avant : "Tous pouvoirs sont donnes a M. Karim BENALI" (le president), sans saisie.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        SuccursaleVarsBuilder.ouvertureMaVars("PV_CREATION_SUCCURSALE_MAROC_SARL_AU", payload()))
                .isInstanceOf(ma.jurika.ai.document.GenerationRefuseeException.class)
                .hasMessageContaining("Mandataire");
    }
}

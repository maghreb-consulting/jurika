package ma.jurika.workflow.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E14) : la consolidation du dossier a la fin d'une creation lit les etapes 1 a 4.
 *
 * <p>Constate sur la pile (parcours de creation joue de bout en bout) : apres la finalisation, le
 * dossier n'avait ni capital, ni ICE, ni adresse, ni ville, ni la denomination de l'etape 1. Le
 * passage du ticket au deroulement de la demarche etait refuse ("$CAPITAL_CHIFFRES non renseignee
 * sur le dossier") alors que le capital etait saisi a l'etape 3. Cause : l'enregistrement
 * automatique de l'ecran remplace {@code data.stepN} par la saisie A PLAT ; les cles imbriquees
 * posees par la strategie ({@code denomination}, {@code siege}, {@code capital}) disparaissent et la
 * consolidation ne trouvait plus rien.
 */
class CreationConsolidationNormalisationTest {

    private static Map<String, Object> plat() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step1", new LinkedHashMap<>(Map.of("denomination", "Explo SARL", "ice", "001234567000089",
                "formeJuridique", "SARL")));
        data.put("step2", new LinkedHashMap<>(Map.of("adresse", "12 rue de la D\u00e9monstration",
                "commune", "Casablanca", "justificatifType", "BAIL")));
        data.put("step3", new LinkedHashMap<>(Map.of("capitalSocialMad", 10000, "nombreParts", 100)));
        data.put("step4", new LinkedHashMap<>(Map.of("description", "Conseil")));
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sous(Map<String, Object> data, String etape, String cle) {
        Object o = ((Map<String, Object>) data.get(etape)).get(cle);
        assertThat(o).as(etape + "." + cle + " est une carte").isInstanceOf(Map.class);
        return (Map<String, Object>) o;
    }

    @Test
    @DisplayName("Saisie a plat (enregistrement automatique) : les etapes sont relues sous leurs cles")
    void saisieAPlatRelue() {
        Map<String, Object> n = WorkflowUseCases.normaliserCreation(plat());

        assertThat(sous(n, "step1", "denomination")).containsEntry("denomination", "Explo SARL")
                .containsEntry("ice", "001234567000089");
        assertThat(sous(n, "step2", "siege")).containsEntry("adresse", "12 rue de la D\u00e9monstration")
                .containsEntry("commune", "Casablanca");
        assertThat(sous(n, "step3", "capital")).containsEntry("capitalSocialMad", 10000);
        assertThat(sous(n, "step4", "activite")).containsEntry("description", "Conseil");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Saisie deja imbriquee (strategie) : rien n'est change")
    void saisieImbriqueeInchangee() {
        Map<String, Object> data = plat();
        Map<String, Object> capital = Map.of("capitalSocialMad", 25000);
        ((Map<String, Object>) data.get("step3")).put("capital", capital);

        Map<String, Object> n = WorkflowUseCases.normaliserCreation(data);

        assertThat(sous(n, "step3", "capital")).isEqualTo(capital);
    }

    @Test
    @DisplayName("La saisie d'origine n'est pas modifiee (copie)")
    void origineIntacte() {
        Map<String, Object> data = plat();
        WorkflowUseCases.normaliserCreation(data);
        assertThat(((Map<?, ?>) data.get("step1")).get("denomination")).isEqualTo("Explo SARL");
        assertThat(((Map<?, ?>) data.get("step3")).containsKey("capital")).isFalse();
    }
}

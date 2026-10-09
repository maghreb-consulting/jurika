package ma.jurika.ai.api;

import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.corpus.RapportChargement;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2, etape E6 : rapport de chargement du corpus, reserve au super
 * administrateur (demonstration A : « le rapport de chargement du corpus »).
 */
class CorpusControllerTest {

    private final CorpusController controller = new CorpusController(
            ChargeurCorpus.charger(Path.of("src/test/resources/corpus-test/CORPUS_TEST")));

    @Test
    void route_du_rapport() throws Exception {
        Method m = CorpusController.class.getMethod("rapport");
        assertThat(m.getAnnotation(GetMapping.class).value()).containsExactly("/rapport");
        assertThat(CorpusController.class.getAnnotation(
                org.springframework.web.bind.annotation.RequestMapping.class).value())
                .containsExactly("/api/v1/ai/corpus");
    }

    @Test
    void reserve_au_super_administrateur() throws Exception {
        Method m = AiControllerSecurityTest.methode(CorpusController.class, "rapport");
        assertThat(AiControllerSecurityTest.autorise("ROLE_SUPER_ADMIN", m)).isTrue();
        assertThat(AiControllerSecurityTest.autorise("ROLE_SUPERVISEUR", m)).isFalse();
        assertThat(AiControllerSecurityTest.autorise("ROLE_EMPLOYE", m)).isFalse();
        assertThat(AiControllerSecurityTest.autorise("ROLE_CLIENT", m)).isFalse();
    }

    @Test
    void seuls_les_codes_absents_du_corpus_sont_signales_tries() {
        assertThat(CorpusController.horsCorpus(java.util.List.of("ZZ_CLASSPATH", "PV_TEST_SARL", "AA_CLASSPATH"),
                ChargeurCorpus.charger(Path.of("src/test/resources/corpus-test/CORPUS_TEST"))))
                .containsExactly("AA_CLASSPATH", "ZZ_CLASSPATH");
    }

    @Test
    void contenu_du_rapport() {
        RapportChargement r = controller.rapport();
        assertThat(r.version()).isEqualTo("CORPUS_TEST");
        assertThat(r.modeles()).isEqualTo(3);
        assertThat(r.empreintes()).containsOnlyKeys("ACTE_TEST_SIMPLE", "PV_TEST_SARL", "PV_TEST_SARL_AU");
        assertThat(r.nonRendables()).isEmpty();
        // Les gabarits du classpath sont tous absents du corpus fictif : chacun est signale.
        assertThat(r.horsCorpusClasspath()).contains("ACTE_NOMINATION_GERANT", "ANNONCE_LEGALE_CONSTITUTION")
                .hasSize(57).isSorted();
    }
}

package ma.jurika.ai.document.corpus;

import ma.jurika.ai.document.DocxTemplateEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2, etape E5 : {@code jurika.corpus.root} est obligatoire, sans repli ; un
 * corpus absent ou defectueux empeche le demarrage ; le moteur recoit le corpus.
 */
class CorpusConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorpusConfiguration.class, DocxTemplateEngine.class);

    @Test
    void application_yml_n_a_aucun_repli_pour_la_racine_du_corpus() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).contains("root: ${JURIKA_CORPUS_ROOT}\n");
        assertThat(yml).doesNotContain("${JURIKA_CORPUS_ROOT:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void compose_conserve_le_nom_du_dossier_date_du_corpus() throws Exception {
        // Defaut vu a l'activation de L2 (2026-10-09) : monte sur /corpus, le corpus perdait
        // son nom de dossier date et le rapport annoncait la version "corpus". La racine vue
        // par ai-service doit etre le chemin meme de JURIKA_CORPUS_DIR, monte a l'identique.
        Map<String, Object> compose = new org.yaml.snakeyaml.Yaml().load(
                Files.readString(Path.of("../../infrastructure/docker-compose.services.yml")));
        Map<String, Object> ai = (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get("ai-service");
        Object racine = ((Map<String, Object>) ai.get("environment")).get("JURIKA_CORPUS_ROOT");
        String montage = ((java.util.List<String>) ai.get("volumes")).stream()
                .filter(v -> v.contains("JURIKA_CORPUS_DIR")).findFirst().orElseThrow();
        String cible = montage.substring(montage.indexOf("}:") + 2, montage.lastIndexOf(":ro"));
        assertThat(racine).isEqualTo("${JURIKA_CORPUS_DIR}");
        assertThat(cible).isEqualTo("${JURIKA_CORPUS_DIR}");
    }

    @Test
    void propriete_absente_refus_de_demarrer() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void propriete_vide_refus_de_demarrer() {
        runner.withPropertyValues("jurika.corpus.root=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(CorpusException.class);
        });
    }

    @Test
    void corpus_defectueux_refus_de_demarrer() {
        runner.withPropertyValues("jurika.corpus.root=target/corpus-absent").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(CorpusException.class);
        });
    }

    @Test
    void corpus_charge_et_injecte_dans_le_moteur() {
        runner.withPropertyValues("jurika.corpus.root=" + CorpusLecturesTest.CORPUS_TEST).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CorpusCharge.class).gabarits()).hasSize(3);
            byte[] doc = ctx.getBean(DocxTemplateEngine.class)
                    .generate("PV_TEST_SARL", Map.of("DENOMINATION", "ACME", "LIEU_SIGNATURE", "Rabat")).bytes();
            assertThat(doc).isNotEmpty();
            assertThat(ctx.getBean(DocxTemplateEngine.class).codesServisHorsCorpus()).isEmpty();
        });
    }
}

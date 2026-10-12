package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E11) : l'avis d'ouverture de la succursale d'une societe etrangere est rendu par
 * le modele du corpus ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SOCIETE_ETRANGERE (sans variante
 * de forme : une societe etrangere n'est ni SARL ni SARL AU).
 */
class SuccursaleEtrangereAnnonceCorpusTest {

    static final String CIBLE = "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SOCIETE_ETRANGERE";
    static final Path RACINE = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));

    @Test
    void les_deux_codes_logiques_rendent_le_modele_du_corpus() {
        SuccursaleEtrMapper m = new SuccursaleEtrMapper();
        assertThat(m.gabarit("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL", Map.of())).isEqualTo(CIBLE);
        assertThat(m.gabarit("ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU", Map.of())).isEqualTo(CIBLE);
        // Le PV d'organe unique reste un ecart du cabinet (variante _AU absente du corpus).
        assertThat(m.gabarit("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU", Map.of()))
                .isEqualTo("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendu_par_le_corpus_sans_donnee_manquante() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(RACINE.resolve("INDEX_CORPUS.xlsx")), "corpus reel absent");
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        DocxTemplateEngine engine = new DocxTemplateEngine(loader, null,
                ma.jurika.ai.document.corpus.ChargeurCorpus.charger(RACINE));
        Map<String, Object> fixture;
        try (InputStream in = getClass().getResourceAsStream("/workflow-fixtures/succursale_etr.json")) {
            fixture = new ObjectMapper().readValue(in, Map.class);
        }
        for (String forme : new String[]{"SARL", "SARL_AU"}) {
            Map<String, Object> payload = (Map<String, Object>) fixture.get("PV_CREATION_SUCCURSALE_ETRANGERE_" + forme);
            String code = "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_" + forme;
            SuccursaleEtrMapper m = new SuccursaleEtrMapper();
            DocxTemplateEngine.DocumentResult r = engine.generate(m.gabarit(code, payload), m.map(code, payload));
            assertThat(r.missingVariables()).as(code).isEmpty();
            String texte;
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()));
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                texte = ex.getText();
            }
            assertThat(texte).doesNotContain("$").doesNotContain("\u25c7").doesNotContain("\u25bc");
            Path sortie = Path.of("target", "output", "annonce-succursale-etrangere");
            Files.createDirectories(sortie);
            Files.write(sortie.resolve(code + ".docx"), r.bytes());
        }
    }
}

package ma.jurika.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.mapper.ModificationMapper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11c) : les proces-verbaux du corpus (PV_AG_SARL, PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU)
 * prevoient l'emplacement d'une clause libre : la boucle RESOLUTIONS imprime
 * "$RESOLUTION_ORDINAL ... -- $RESOLUTION_OBJET" et, pour une resolution d'un type non catalogue,
 * "$RESOLUTION_TEXTE_LIBRE" ("texte de la resolution lorsqu'aucun type catalogue ne convient").
 * Une clause y devient une resolution de plus, numerotee a la suite, rendue par cette branche.
 */
class ClausesLibresPvCorpusTest {

    private static final Path CORPUS = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
    private static DocxTemplateEngine engine;
    private final ModificationMapper mapper = new ModificationMapper();

    @BeforeAll
    static void charger() {
        if (!Files.isDirectory(CORPUS)) return;
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader, null, ChargeurCorpus.charger(CORPUS));
    }

    @Test
    void les_pv_du_corpus_prevoient_l_emplacement() {
        Assumptions.assumeTrue(engine != null, "corpus reel absent");
        assertThat(engine.emplacementClausesPrevu("PV_AG_SARL")).isTrue();
        assertThat(engine.emplacementClausesPrevu("PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU")).isTrue();
    }

    @Test
    void la_clause_s_imprime_dans_le_pv_d_assemblee_a_la_suite_des_resolutions() throws Exception {
        Assumptions.assumeTrue(engine != null, "corpus reel absent");
        Map<String, Object> payload = fixture("PV_MODIFICATION_SARL");
        Map<String, Object> vars = mapper.map("PV_MODIFICATION_SARL", payload);
        Map<String, Object> avec = ClausesLibres.inserer("PV_MODIFICATION_SARL", vars, List.of(Map.of(
                "titre", "Pouvoirs particuliers", "texte", "L'assembl\u00e9e g\u00e9n\u00e9rale conf\u00e8re \u00e0 M. Karim BENALI tous pouvoirs pour signer le nouveau bail.",
                "resultat", "adopt\u00e9e", "emplacement", ClausesLibres.EMPLACEMENT)));
        String texte = texte(engine.generate(mapper.gabarit("PV_MODIFICATION_SARL", payload),
                ma.jurika.ai.workflow.identity.RepriseFicheSociete.completer(avec, payload)));
        assertThat(texte).contains("TROISI\u00c8ME R\u00c9SOLUTION \u2014 Pouvoirs particuliers\n"
                        + "L'assembl\u00e9e g\u00e9n\u00e9rale conf\u00e8re \u00e0 M. Karim BENALI tous pouvoirs pour signer le nouveau bail.\n"
                        + "Cette r\u00e9solution, mise aux voix, est adopt\u00e9e \u00e0 l'unanimit\u00e9.")
                .doesNotContain("VALEUR MANQUANTE");
    }

    @Test
    void la_clause_placee_avant_une_decision_de_l_associe_unique_renumerote() throws Exception {
        Assumptions.assumeTrue(engine != null, "corpus reel absent");
        Map<String, Object> payload = fixture("PV_MODIFICATION_SARL_AU");
        Map<String, Object> vars = mapper.map("PV_MODIFICATION_SARL_AU", payload);
        Map<String, Object> avec = ClausesLibres.inserer("PV_MODIFICATION_SARL_AU", vars, List.of(Map.of(
                "titre", "Ouverture d'un compte bancaire", "texte", "L'associ\u00e9 unique autorise l'ouverture d'un compte.",
                "emplacement", ClausesLibres.AVANT_RESOLUTION + "2")));
        String texte = texte(engine.generate(mapper.gabarit("PV_MODIFICATION_SARL_AU", payload),
                ma.jurika.ai.workflow.identity.RepriseFicheSociete.completer(avec, payload)));
        assertThat(texte).contains("PREMI\u00c8RE D\u00c9CISION \u2014 Transfert du si\u00e8ge social",
                "DEUXI\u00c8ME D\u00c9CISION \u2014 Ouverture d'un compte bancaire\nL'associ\u00e9 unique autorise l'ouverture d'un compte.",
                "TROISI\u00c8ME D\u00c9CISION \u2014 Changement de d\u00e9nomination sociale").doesNotContain("VALEUR MANQUANTE");
    }

    /** "Cette resolution, mise aux voix, est adoptee $RESOLUTION_RESULTAT" : une clause rejetee ne s'y ecrit pas. */
    @Test
    void une_clause_rejetee_ne_s_ecrit_pas_dans_le_pv_d_assemblee() throws Exception {
        Map<String, Object> vars = mapper.map("PV_MODIFICATION_SARL", fixture("PV_MODIFICATION_SARL"));
        assertThatThrownBy(() -> ClausesLibres.inserer("PV_MODIFICATION_SARL", vars, List.of(Map.of(
                "titre", "Pouvoirs", "texte", "Texte.", "resultat", "rejet\u00e9e"))))
                .isInstanceOf(ClauseLibreImpossibleException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fixture(String code) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/workflow-fixtures/modification.json")) {
            return (Map<String, Object>) new ObjectMapper().readValue(in, Map.class).get(code);
        }
    }

    private static String texte(DocxTemplateEngine.DocumentResult r) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}

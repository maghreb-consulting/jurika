package ma.jurika.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.mapper.AnnualReportMapper;
import ma.jurika.ai.workflow.mapper.ApprobationComptesMapper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L3 (RG-GEN-05 a 07) : une clause libre s'imprime a l'emplacement prevu par le
 * modele, telle que saisie, numerotee a la suite des resolutions ; un modele sans
 * emplacement n'en recoit pas.
 */
class ClausesLibresTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine(chargeur());

    private static TemplateManifestLoader chargeur() {
        TemplateManifestLoader l = new TemplateManifestLoader(new ObjectMapper());
        l.load();
        return l;
    }

    @Test
    void emplacement_prevu_seulement_par_la_boucle_des_resolutions_a_intitule_et_texte() {
        assertThat(engine.emplacementClausesPrevu("PV_APPROBATION_COMPTES_SARL")).isTrue();
        assertThat(engine.emplacementClausesPrevu("PV_CREATION_SUCCURSALE_MAROC_SARL")).isTrue();
        // Resolutions typees, sans branche libre : pas d'emplacement (le gabarit n'est pas modifie).
        assertThat(engine.emplacementClausesPrevu("PV_MODIFICATION_SARL")).isFalse();
        // Statuts : emplacement des clauses particulieres a fournir par le cabinet (A_DECIDER).
        assertThat(engine.emplacementClausesPrevu("STATUTS_SARL")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void la_clause_s_imprime_telle_que_saisie_a_la_suite_des_resolutions() throws Exception {
        Map<String, Object> fixture;
        try (InputStream in = getClass().getResourceAsStream("/workflow-fixtures/approbation_comptes.json")) {
            fixture = (Map<String, Object>) new ObjectMapper().readValue(in, Map.class).get("PV_APPROBATION_COMPTES_SARL");
        }
        Map<String, Object> vars = new ApprobationComptesMapper(new AnnualReportMapper()).map("PV_APPROBATION_COMPTES_SARL", fixture);
        int avant = ((List<?>) vars.get("RESOLUTIONS")).size();

        Map<String, Object> avec = ClausesLibres.inserer(vars, List.of(Map.of(
                "titre", "Pouvoirs particuliers", "texte", "L'assemblée confère tous pouvoirs à M. Karim BENALI.",
                "resultat", "adoptée", "voixPour", "1000", "voixContre", "0", "abstentions", "0")));
        DocxTemplateEngine.DocumentResult r = engine.generate("PV_APPROBATION_COMPTES_SARL", avec);

        String texte;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            texte = ex.getText();
        }
        assertThat(texte).contains("Pouvoirs particuliers")
                .contains("L'assemblée confère tous pouvoirs à M. Karim BENALI.");
        List<Map<String, Object>> res = (List<Map<String, Object>>) avec.get("RESOLUTIONS");
        assertThat(res).hasSize(avant + 1);
        assertThat(texte).contains(String.valueOf(res.get(avant).get("RESOLUTION_NUMERO")));
        assertThat(r.manquantes()).noneMatch(m -> m.nom().startsWith("RESOLUTION_"));
    }

    @Test
    void numerotation_a_la_suite_de_celle_du_mapper() {
        assertThat(ClausesLibres.numero("TROISIÈME RÉSOLUTION", 4)).isEqualTo("QUATRIÈME RÉSOLUTION");
        assertThat(ClausesLibres.numero("Deuxième résolution", 3)).isEqualTo("Troisième résolution");
        assertThat(ClausesLibres.numero("3", 4)).isEqualTo("4");
        assertThat(ClausesLibres.numero(null, 1)).isEqualTo("1");
    }
}

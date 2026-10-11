package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.ControleCompletude;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import ma.jurika.ai.workflow.identity.RepriseFicheSociete;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.fixture;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.liquidateurPersonneMorale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11d) : les documents de la dissolution et de la liquidation rendus par les
 * modeles du CORPUS REEL (CORPUS_2026-10-11), avec les variables des mappers et la reprise de
 * la fiche societe (meme chaine qu'en production). Garde : aucune donnee manquante, aucune
 * variable ni balise residuelle ; le texte produit est ecrit sous
 * {@code target/output/liquidation-corpus/} pour lecture. Sans corpus reel, le test est saute.
 *
 * <p>Le rapport du liquidateur n'est pas resolu au lot L4d : sa generation est refusee et les
 * donnees manquantes sont nommees (aucun document incomplet ne sort).
 */
class LiquidationCorpusRenderTest {

    private static final Path CORPUS = Path.of(System.getProperty("user.home"), "corpus", "CORPUS_2026-10-11");
    private static final Path OUT = Path.of("target", "output", "liquidation-corpus");
    private static final Pattern RESIDU = Pattern.compile("\\$[A-Z][A-Z0-9_]+|[\\u25C7\\u25C6\\u25BC\\u25B2\\u25C8]");

    private static DocxTemplateEngine engine;

    @BeforeAll
    static void corpus() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(CORPUS), "corpus reel absent : " + CORPUS);
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader, null, ChargeurCorpus.charger(CORPUS));
        Files.createDirectories(OUT);
    }

    private static DocxTemplateEngine.DocumentResult rendre(WorkflowDocumentMapper mapper, String code,
                                                            Map<String, Object> payload) {
        Map<String, Object> vars = RepriseFicheSociete.completer(mapper.map(code, payload), payload);
        return engine.generate(mapper.gabarit(code, payload), vars);
    }

    /** Rendu complet : aucune donnee manquante, aucun residu ; texte ecrit pour lecture. */
    private static String complet(WorkflowDocumentMapper mapper, String code, Map<String, Object> payload,
                                  String echantillon) throws Exception {
        DocxTemplateEngine.DocumentResult r = rendre(mapper, code, payload);
        String texte;
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(r.bytes()));
             XWPFWordExtractor ex = new XWPFWordExtractor(d)) {
            texte = ex.getText();
        }
        Files.write(OUT.resolve(echantillon + ".docx"), r.bytes());
        Files.writeString(OUT.resolve(echantillon + ".txt"), texte);
        assertThat(r.manquantes()).describedAs("%s : donnees manquantes", echantillon).isEmpty();
        assertThat(RESIDU.matcher(texte).find()).describedAs("%s : variable ou balise residuelle", echantillon).isFalse();
        assertThat(texte).doesNotContain("VALEUR MANQUANTE").doesNotContain("A OBTENIR");
        return texte.replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    @Test
    void pv_de_dissolution_sarl_liquidateur_personne_physique() throws Exception {
        String t = complet(new DissolutionMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL", fixture("dissolution.json"),
                "PV_DISSOLUTION_ANTICIPEE_SARL_personne_physique");
        assertThat(t).contains("PROC\u00c8S-VERBAL DE L'ASSEMBL\u00c9E G\u00c9N\u00c9RALE EXTRAORDINAIRE DU 15/05/2026")
                .contains("le 15/05/2026, \u00e0 10 heures, les associ\u00e9s de la soci\u00e9t\u00e9 PARACOSME")
                .contains("divis\u00e9 en 1000 parts sociales de 100 dirhams chacune")
                .contains("Mme Fatima Bennani, personne physique, propri\u00e9taire de 400 parts sociales, pr\u00e9sente.")
                .contains("M. Ahmed Alaoui, de nationalit\u00e9 marocaine, n\u00e9 le 12/03/1975, demeurant \u00e0 5 RUE D'ALGER, CASABLANCA, titulaire de la CIN n\u00b0 BE123456.")
                .contains("Son mandat est conf\u00e9r\u00e9 pour une dur\u00e9e de trois ans")
                .contains("Les fonctions de liquidateur sont exerc\u00e9es \u00e0 titre gratuit.")
                .contains("fixe le si\u00e8ge de la liquidation \u00e0 20 BOULEVARD ZERKTOUNI, CASABLANCA")
                .contains("M. Ahmed Alaoui, g\u00e9rant, \u00e9tant appel\u00e9 aux fonctions de liquidateur")
                .contains("est adopt\u00e9e \u00e0 l'unanimit\u00e9.")
                .contains("la s\u00e9ance lev\u00e9e \u00e0 12 heures")
                .doesNotContain("La soci\u00e9t\u00e9 occupant habituellement dix salari\u00e9s");
    }

    @Test
    void pv_de_dissolution_sarl_liquidateur_personne_morale() throws Exception {
        Map<String, Object> p = fixture("dissolution.json");
        p.put("liquidateur", liquidateurPersonneMorale());
        String t = complet(new DissolutionMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL", p,
                "PV_DISSOLUTION_ANTICIPEE_SARL_personne_morale");
        assertThat(t).contains("La soci\u00e9t\u00e9 FIDUCIAIRE ATLAS, SARL au capital de 500 000 dirhams, dont le si\u00e8ge social est situ\u00e9 \u00e0 8 RUE IBN BATOUTA, RABAT")
                .contains("repr\u00e9sent\u00e9e de fa\u00e7on permanente, pour l'ex\u00e9cution du pr\u00e9sent mandat, par Mme Salma Idrissi, agissant en qualit\u00e9 de g\u00e9rante.")
                .contains("la r\u00e9mun\u00e9ration du liquidateur \u00e0 la somme de 20 000 dirhams")
                .contains("FIDUCIAIRE ATLAS, repr\u00e9sent\u00e9e par Mme Salma Idrissi")
                .doesNotContain("Le liquidateur ayant eu la qualit\u00e9 de g\u00e9rant");
    }

    @Test
    void decisions_de_dissolution_sarl_au() throws Exception {
        String t = complet(new DissolutionMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL_AU", fixture("dissolution.json"),
                "PV_DISSOLUTION_ANTICIPEE_SARL_AU_personne_physique");
        // L'identite de l'associe unique, jamais le drapeau "oui".
        assertThat(t).contains("M. Ahmed Alaoui, personne physique, propri\u00e9taire de la totalit\u00e9 des 1000 parts")
                .doesNotContainPattern("(?m)^oui, ");
        Map<String, Object> p = fixture("dissolution.json");
        p.put("liquidateur", liquidateurPersonneMorale());
        assertThat(complet(new DissolutionMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL_AU", p,
                "PV_DISSOLUTION_ANTICIPEE_SARL_AU_personne_morale")).contains("La soci\u00e9t\u00e9 FIDUCIAIRE ATLAS");
    }

    @Test
    void pv_de_cloture_sarl() throws Exception {
        String t = complet(new LiquidationMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL", fixture("liquidation.json"),
                "PV_CLOTURE_LIQUIDATION_SARL_personne_physique");
        assertThat(t).contains("actif net disponible apr\u00e8s paiement du passif, des frais et des impositions : 140 000 dirhams")
                .contains("solde de liquidation constituant un boni de liquidation : 40 000 dirhams")
                .contains("M. Ahmed Alaoui, propri\u00e9taire de 600 parts sociales : remboursement du nominal 60 000 dirhams ; quote-part du boni de liquidation 24 000 dirhams ; retenue \u00e0 la source 3 600 dirhams ; net revenant 80 400 dirhams.")
                .contains("Mme Fatima Bennani, personne physique, titulaire de la CIN n\u00b0 BK654321, propri\u00e9taire de 400 parts sociales, pr\u00e9sente.")
                .contains("jusqu'au 25/05/2031")
                .contains("au plus tard le 04/08/2026");
        Map<String, Object> p = fixture("liquidation.json");
        p.put("liquidateur", liquidateurPersonneMorale());
        assertThat(complet(new LiquidationMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL", p,
                "PV_CLOTURE_LIQUIDATION_SARL_personne_morale"))
                .contains("la soci\u00e9t\u00e9 FIDUCIAIRE ATLAS, SARL dont le si\u00e8ge social est situ\u00e9 \u00e0 8 RUE IBN BATOUTA, RABAT")
                .contains("r\u00e9mun\u00e9ration du liquidateur : 20 000 dirhams");
    }

    @Test
    void decisions_de_cloture_sarl_au() throws Exception {
        String t = complet(new LiquidationMapper(), "PV_DISSOLUTION_LIQUIDATION_SARL_AU", fixture("liquidation.json"),
                "PV_CLOTURE_LIQUIDATION_SARL_AU_personne_physique");
        assertThat(t).contains("M. Ahmed Alaoui, personne physique, titulaire de la CIN n\u00b0 BE123456")
                .contains("net revenant \u00e0 l'associ\u00e9 unique 134 000 dirhams");
    }

    @Test
    void rapport_du_liquidateur_refuse_donnees_nommees() {
        DocxTemplateEngine.DocumentResult r = rendre(new LiquidationMapper(), "RAPPORT_LIQUIDATION_DIRECTEUR",
                fixture("liquidation.json"));
        assertThat(r.manquantesBloquantes()).extracting(m -> m.nom())
                .contains("REALISATION_BIEN_DESIGNATION", "APUREMENT_CREANCIER", "FRAIS_LIQUIDATION_NATURE",
                        "DILIGENCE_NATURE", "RAPPORT_LIQUIDATEUR_PERIODE_DEBUT");
        assertThatThrownBy(() -> ControleCompletude.verifier("RAPPORT_LIQUIDATEUR_SARL", r, engine.dictionnaire()))
                .isInstanceOf(GenerationRefuseeException.class);
    }
}

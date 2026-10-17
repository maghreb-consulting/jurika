package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.fixture;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.liquidateurPersonneMorale;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E11d) : les documents de la dissolution et de la liquidation rendus par les
 * modeles du CORPUS REEL (CORPUS_2026-10-11), avec les variables des mappers et la reprise de
 * la fiche societe (meme chaine qu'en production). Garde : aucune donnee manquante, aucune
 * variable ni balise residuelle ; le texte produit est ecrit sous
 * {@code target/output/liquidation-corpus/} pour lecture. Sans corpus reel, le test est saute.
 *
 * <p>Lot L5 (E4) : le rapport final du liquidateur est rendu par RAPPORT_LIQUIDATEUR_SARL(_AU),
 * operations saisies, totaux et resultat calcules.
 */
class LiquidationCorpusRenderTest {

    private static final Path CORPUS = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
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
        // L6b (E19) : le compte du PV vient du rapport final, dont les frais comprennent la
        // remuneration du liquidateur (20 000).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fraisRapport = (List<Map<String, Object>>) DissolutionMapperTest.bloc(p, "rapportLiquidateur").get("frais");
        fraisRapport.add(new LinkedHashMap<>(Map.of("nature", "R\u00e9mun\u00e9ration du liquidateur", "montant", 20_000)));
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

    // Lot L5 (E4) : rapport final du liquidateur rendu par RAPPORT_LIQUIDATEUR_SARL(_AU) du corpus,
    // operations saisies, totaux et resultat calcules.
    @Test
    void rapport_du_liquidateur_sarl_boni_reparti() throws Exception {
        String t = complet(new LiquidationMapper(), "RAPPORT_LIQUIDATION_DIRECTEUR", fixture("liquidation.json"),
                "RAPPORT_LIQUIDATEUR_SARL_boni");
        assertThat(t).contains("RAPPORT FINAL DU LIQUIDATEUR")
                .contains("Total des produits de r\u00e9alisation encaiss\u00e9s : 300 000 dirhams (TROIS CENT MILLE)")
                .contains("Total des plus-values de r\u00e9alisation : 30 000 dirhams")
                .contains("Total des moins-values de r\u00e9alisation : 5 000 dirhams")
                .contains("R\u00e9sultat de la r\u00e9alisation : plus-value d'un montant de 30 000 dirhams")
                .contains("Total du passif r\u00e9gl\u00e9 : 150 000 dirhams (CENT CINQUANTE MILLE)")
                .contains("Tr\u00e9sorerie disponible \u00e0 la date d'arr\u00eat\u00e9 des comptes : 140 000 dirhams")
                .contains("Total des frais et honoraires de liquidation, tel qu'arr\u00eat\u00e9 par le dossier : 10 000 dirhams (DIX MILLE)")
                .contains("remboursement du capital social \u00e0 concurrence de 100 000 dirhams")
                .contains("boni de liquidation d'un montant de 40 000 dirhams (QUARANTE MILLE)")
                .contains("M. Ahmed Alaoui, titulaire de 600 parts sociales : 24 000 dirhams.")
                .contains("Mme Fatima Bennani, titulaire de 400 parts sociales : 16 000 dirhams.")
                .contains("au taux de 15 %, pour un montant de 6 000 dirhams")
                .contains("Fait \u00e0 CASABLANCA, le 12/06/2026, en quatre exemplaires originaux.")
                .doesNotContain("mali de liquidation d'un montant");
    }

    @Test
    void rapport_du_liquidateur_sarl_au_mali_liquidateur_personne_morale() throws Exception {
        Map<String, Object> p = fixture("liquidation.json");
        DissolutionMapperTest.bloc(p, "societe").put("formeJuridique", "SARL_AU");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) p.get("associes");
        associes.remove(1);
        p.put("liquidateur", liquidateurPersonneMorale());
        // Liquidateur remunere (20 000, compris dans les frais) ; actif realise 200 000 ;
        // disponible 200 000 - 150 000 - 30 000 = 20 000 : mali de 80 000.
        Map<String, Object> r = DissolutionMapperTest.bloc(p, "rapportLiquidateur");
        DissolutionMapperTest.bloc(p, "cloture").put("actifRealise", 200_000);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> real = (List<Map<String, Object>>) r.get("realisations");
        real.get(0).put("prix", 150_000);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frais = (List<Map<String, Object>>) r.get("frais");
        frais.add(new LinkedHashMap<>(Map.of("nature", "R\u00e9mun\u00e9ration du liquidateur",
                "beneficiaire", "FIDUCIAIRE ATLAS", "montant", 20_000, "date", "2026-06-10", "statut", "\u00e0 r\u00e9gler")));
        r.put("maliObservations", "le fonds de commerce a \u00e9t\u00e9 c\u00e9d\u00e9 en dessous de sa valeur d'inventaire");
        String t = complet(new LiquidationMapper(), "RAPPORT_LIQUIDATION_DIRECTEUR", p, "RAPPORT_LIQUIDATEUR_SARL_AU_mali");
        assertThat(t).contains("mali de liquidation d'un montant de 80 000 dirhams (QUATRE-VINGT MILLE)")
                .contains("demeure \u00e0 la charge de l'associ\u00e9 unique")
                .contains("R\u00e9sultat de la r\u00e9alisation : moins-value d'un montant de 70 000 dirhams")
                .contains("Total des frais et honoraires de liquidation, tel qu'arr\u00eat\u00e9 par le dossier : 30 000 dirhams (TRENTE MILLE)")
                .contains("remboursement du capital social \u00e0 concurrence de 20 000 dirhams")
                .contains("M. Ahmed Alaoui, associ\u00e9 unique")
                .contains("FIDUCIAIRE ATLAS, repr\u00e9sent\u00e9e par Mme Salma Idrissi")
                .doesNotContain("boni de liquidation d'un montant");
    }
}

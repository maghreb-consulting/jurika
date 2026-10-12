package ma.jurika.ai.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.corpus.CorpusCharge;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import ma.jurika.ai.workflow.identity.RepriseFicheSociete;
import ma.jurika.ai.workflow.mapper.AnnualReportMapper;
import ma.jurika.ai.workflow.mapper.ApprobationComptesMapper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E11e) : le rapport de gestion de la gerance est rendu par les modeles du corpus
 * RAPPORT_GESTION_EXERCICE_SARL(_AU), choisis par la forme de la societe, avec les variables
 * construites en un seul endroit (RapportGestionVarsBuilder) pour les deux parcours qui le
 * servent (PV_AGO et ANNUAL_REPORT). Rendu par la chaine de production (mapper, reprise de
 * la fiche, modele choisi, vrai corpus) ; aucun manquant, aucun residu de balisage, et le
 * texte se relit (beneficiaire et deficitaire, SARL et SARL AU).
 */
class RapportGestionCorpusTest {

    private static final Path SORTIE = Path.of("target", "output", "rapport-gestion");
    private static DocxTemplateEngine engine;

    @BeforeAll
    static void chargerCorpus() throws Exception {
        if (!CorpusReelIT.corpusReelPresent()) return;
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        CorpusCharge corpus = ChargeurCorpus.charger(CorpusReelIT.RACINE);
        engine = new DocxTemplateEngine(loader, null, corpus);
        Files.createDirectories(SORTIE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture(String fichier) throws Exception {
        try (InputStream in = RapportGestionCorpusTest.class.getResourceAsStream("/workflow-fixtures/" + fichier)) {
            Map<String, Object> root = new ObjectMapper().readValue(in, Map.class);
            return (Map<String, Object>) root.get("RAPPORT_GESTION");
        }
    }

    private static String texte(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    /** Rend le rapport comme en production ; ecrit le .docx et son texte a relire. */
    private static String rendre(WorkflowDocumentMapper mapper, Map<String, Object> payload,
                                 String modeleAttendu, String nomSortie) throws Exception {
        Assumptions.assumeTrue(engine != null, "corpus reel absent");
        Map<String, Object> vars = RepriseFicheSociete.completer(mapper.map("RAPPORT_GESTION", payload), payload);
        String gabarit = mapper.gabarit("RAPPORT_GESTION", payload);
        assertThat(gabarit).isEqualTo(modeleAttendu);
        DocxTemplateEngine.DocumentResult r = engine.generate(gabarit, vars);
        Files.write(SORTIE.resolve(nomSortie + ".docx"), r.bytes());
        String t = texte(r.bytes());
        Files.writeString(SORTIE.resolve(nomSortie + ".txt"), t);
        assertThat(r.missingVariables()).as("variables manquantes").isEmpty();
        assertThat(ControleDocumentProduit.defauts(t, Set.of())).as("defauts du document").isEmpty();
        List<String> residus = new ArrayList<>();
        var m = CorpusReelIT.RESIDU_RENDU.matcher(t);
        while (m.find()) residus.add(m.group());
        assertThat(residus).as("residus de balisage").isEmpty();
        return t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> enPerte(Map<String, Object> payload) {
        Map<String, Object> p = new LinkedHashMap<>(payload);
        Map<String, Object> a = new LinkedHashMap<>((Map<String, Object>) p.get("approbation"));
        a.put("resultatType", "perte");
        a.put("resultatNet", 300000);
        a.put("affectations", List.of(Map.of("libelle", "Report \u00e0 nouveau d\u00e9biteur", "montant", 300000)));
        a.put("dividendeDistribue", "non");
        a.remove("dividendeParPart");
        a.remove("dividendeMiseEnPaiementDate");
        p.put("approbation", a);
        Map<String, Object> rg = new LinkedHashMap<>((Map<String, Object>) p.get("rapportGestion"));
        rg.remove("reserveLegaleDoteeIntegralement");
        rg.put("capitauxPropres", 20000);
        rg.put("pertesCumulees", 300000);
        rg.put("evenementsPostCloture", "oui");
        rg.put("evenementsPostClotureListe", List.of(Map.of("date", "2026-02-10",
                "libelle", "perte d'un client important", "incidence", "baisse attendue du chiffre d'affaires de l'exercice 2026")));
        rg.put("rechercheDeveloppement", "oui");
        rg.put("rechercheDeveloppementExpose", "mise au point d'un outil de veille r\u00e9glementaire, achev\u00e9e \u00e0 moiti\u00e9, pour un co\u00fbt de 40 000 dirhams");
        p.put("rapportGestion", rg);
        Map<String, Object> app = (Map<String, Object>) p.get("approbation");
        ((Map<String, Object>) app).put("commissairePresent", "oui");
        ((Map<String, Object>) app).put("commissaireNom", "Mme Nadia TAZI");
        return p;
    }

    @Test
    void pv_ago_sarl_beneficiaire_rendu_par_le_modele_sarl_du_corpus() throws Exception {
        String t = rendre(new ApprobationComptesMapper(new AnnualReportMapper()), fixture("approbation_comptes.json"),
                "RAPPORT_GESTION_EXERCICE_SARL", "RAPPORT_GESTION_EXERCICE_SARL_benefice");
        assertThat(t).contains("ACME SARL")
                .contains("exercice ouvert le 01/01/2025 et clos le 31/12/2025")
                .contains("divis\u00e9 en 1\u202f000 parts sociales de 100 dirhams chacune")
                .contains("le conseil juridique et fiscal aux entreprises")
                .contains("se solde par un b\u00e9n\u00e9fice net de 250\u202f000 dirhams (DEUX CENT CINQUANTE MILLE)")
                .contains("compar\u00e9e \u00e0 l'exercice pr\u00e9c\u00e9dent clos le 31/12/2024")
                .contains("Fait \u00e0 Casablanca, le 25/05/2026")
                .contains("M. Karim BENALI")
                .doesNotContain("inf\u00e9rieurs au quart du capital social. En cons\u00e9quence")
                .doesNotContain("cumul\u00e9 des pertes");
    }

    @Test
    void annual_report_sarl_au_beneficiaire_rendu_par_le_modele_sarl_au_du_corpus() throws Exception {
        String t = rendre(new AnnualReportMapper(), fixture("annual_report.json"),
                "RAPPORT_GESTION_EXERCICE_SARL_AU", "RAPPORT_GESTION_EXERCICE_SARL_AU_benefice");
        assertThat(t).contains("ACME SARL AU").contains("\u00c0 l'associ\u00e9 unique de la soci\u00e9t\u00e9 ACME SARL AU")
                .contains("Aucune distribution de dividende n'est propos\u00e9e");
    }

    @Test
    void sarl_deficitaire_capitaux_propres_sous_le_quart() throws Exception {
        String t = rendre(new ApprobationComptesMapper(new AnnualReportMapper()), enPerte(fixture("approbation_comptes.json")),
                "RAPPORT_GESTION_EXERCICE_SARL", "RAPPORT_GESTION_EXERCICE_SARL_perte");
        assertThat(t).contains("se solde par une perte nette de 300\u202f000 dirhams (TROIS CENT MILLE)")
                .contains("inf\u00e9rieurs au quart du capital social")
                .contains("s'\u00e9tablit, apr\u00e8s cette affectation, \u00e0 300\u202f000 dirhams")
                .contains("perte d'un client important")
                .contains("Mme Nadia TAZI, commissaire aux comptes")
                .doesNotContain("r\u00e9serve l\u00e9gale ayant atteint");
    }

    @Test
    void sarl_au_deficitaire() throws Exception {
        String t = rendre(new AnnualReportMapper(), enPerte(fixture("annual_report.json")),
                "RAPPORT_GESTION_EXERCICE_SARL_AU", "RAPPORT_GESTION_EXERCICE_SARL_AU_perte");
        assertThat(t).contains("vous serez appel\u00e9 \u00e0 vous prononcer");
    }

    /** Liste annoncee mais vide : jamais effacee en silence, ses donnees sont nommees. */
    @Test
    @SuppressWarnings("unchecked")
    void listes_vides_nommees_a_la_generation() throws Exception {
        Assumptions.assumeTrue(engine != null, "corpus reel absent");
        Map<String, Object> p = new LinkedHashMap<>(fixture("approbation_comptes.json"));
        Map<String, Object> rg = new LinkedHashMap<>((Map<String, Object>) p.get("rapportGestion"));
        rg.put("faitsMarquants", List.of());
        rg.put("indicateurs", List.of());
        p.put("rapportGestion", rg);
        ApprobationComptesMapper mapper = new ApprobationComptesMapper(new AnnualReportMapper());
        Map<String, Object> vars = RepriseFicheSociete.completer(mapper.map("RAPPORT_GESTION", p), p);
        DocxTemplateEngine.DocumentResult r = engine.generate(mapper.gabarit("RAPPORT_GESTION", p), vars);
        assertThat(r.missingVariables()).contains("FAIT_MARQUANT_DATE", "FAIT_MARQUANT_LIBELLE",
                "INDICATEUR_LIBELLE", "INDICATEUR_MONTANT_EXERCICE", "INDICATEUR_MONTANT_EXERCICE_PRECEDENT");
    }
}

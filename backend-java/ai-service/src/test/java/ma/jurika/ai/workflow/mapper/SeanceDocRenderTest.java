package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.ControleCompletude;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.identity.RepriseFicheSociete;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static ma.jurika.ai.workflow.mapper.SeanceDocumentMapperTest.fixture;
import static ma.jurika.ai.workflow.mapper.SeanceDocumentMapperTest.sous;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lot L4d (E11b) : les documents de seance partages (convocation, feuille de presence)
 * rendus par les modeles du corpus REEL du cabinet, avec les variables du mapper (fixture
 * seance.json, meme chaine que le controleur : mapper, reprise de la fiche, modele choisi).
 *
 * <p>Assertions : aucune variable manquante (interne ou externe), aucun marqueur residuel
 * ($VAR, boucle, condition), aucune soudure fautive ; puis lecture du texte produit (les
 * echantillons sont ecrits sous target/output/seance-corpus/ pour etre relus).
 *
 * <p>Remplace le rendu des gabarits du classpath CONVOCATION_AG / FEUILLE_PRESENCE_AG, que
 * plus rien ne sert.
 */
class SeanceDocRenderTest {

    private static final Path CORPUS = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
    private static final Path SORTIE = Path.of("target", "output", "seance-corpus");
    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");
    private static DocxTemplateEngine engine;

    private final SeanceDocumentMapper mapper = new SeanceDocumentMapper();

    @BeforeAll
    static void chargerLeCorpus() throws Exception {
        assumeTrue(Files.isDirectory(CORPUS), "corpus reel absent : " + CORPUS);
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader, null, ChargeurCorpus.charger(CORPUS));
        Files.createDirectories(SORTIE);
    }

    private record Rendu(String modele, DocumentResult res, String texte) {}

    private Rendu rendre(String code, Map<String, Object> payload, String nom) throws Exception {
        Map<String, Object> vars = RepriseFicheSociete.completer(mapper.map(code, payload), payload);
        String modele = mapper.gabarit(code, payload);
        DocumentResult res = engine.generate(modele, vars);
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) sb.append(cell.getText()).append(" | ");
                    sb.append('\n');
                }
            }
        }
        Files.write(SORTIE.resolve(nom + ".docx"), res.bytes());
        Files.writeString(SORTIE.resolve(nom + ".txt"), sb.toString());
        return new Rendu(modele, res, sb.toString());
    }

    private static void propre(Rendu r) {
        assertThat(r.res().templateFound()).as("modele %s introuvable", r.modele()).isTrue();
        assertThat(r.res().missingVariables()).as("%s : variables manquantes", r.modele()).isEmpty();
        assertThat(ControleCompletude.donneesManquantes(r.res(), null)).as("%s : donnees bloquantes", r.modele()).isEmpty();
        assertThat(BARE_VAR.matcher(r.texte()).find()).as("%s : $VAR residuel :\n%s", r.modele(), r.texte()).isFalse();
        for (String marqueur : List.of("\u25c7", "\u25c6", "\u25bc", "\u25b2", "\u25c8", "VALEUR MANQUANTE", "A OBTENIR")) {
            assertThat(r.texte()).as("%s : marqueur residuel %s", r.modele(), marqueur).doesNotContain(marqueur);
        }
        assertThat(ma.jurika.ai.document.format.AssemblageFautif.dans(r.texte()))
                .as("%s : assemblage fautif", r.modele()).isEmpty();
    }

    @Test
    @DisplayName("convocation ordinaire : lettre AGO du corpus, une par associe, sans manque")
    void convocation_ordinaire() throws Exception {
        Rendu r = rendre("CONVOCATION_AG", fixture("CONVOCATION_AG"), "CONVOCATION_AGO_SARL");
        assertThat(r.modele()).isEqualTo("CONVOCATION_AGO_SARL");
        propre(r);
        String t = r.texte();
        assertThat(t).contains("PARACOSME", "SARL au capital de 100", "dirhams (CENT MILLE)",
                "Lettre recommand\u00e9e avec accus\u00e9 de r\u00e9ception",
                "M. Karim BENALI", "Mme Sofia ALAOUI",
                "En ma qualit\u00e9 de g\u00e9rant de la soci\u00e9t\u00e9 PARACOSME",
                "ce d\u00e9lai expire le 30/06/2026",
                "parts sociales de 100 dirhams chacune",
                "repr\u00e9sentant 60\u00a0% du capital social", "repr\u00e9sentant 40\u00a0% du capital social",
                "PREMI\u00c8RE R\u00c9SOLUTION \u2014 Approbation des comptes",
                "Dotation \u00e0 la r\u00e9serve l\u00e9gale",
                "le tableau d'affectation du r\u00e9sultat propos\u00e9",
                "Renouvellement du mandat de la g\u00e9rance ;",
                "Une formule de pouvoir est jointe",
                "R\u00e9f. dossier : 2026-0007 \u2014 PARACOSME");
        assertThat(t).doesNotContain("Distribution de dividendes", "seconde convocation**");
    }

    @Test
    @DisplayName("convocation extraordinaire (et mixte) : lettre AGE du corpus")
    void convocation_extraordinaire_et_mixte() throws Exception {
        for (String type : List.of("extraordinaire", "mixte")) {
            Map<String, Object> p = fixture("CONVOCATION_AG");
            sous(p, "seance").put("type", type);
            p.put("documentsJoints", List.of("le rapport de la g\u00e9rance", "le texte des r\u00e9solutions propos\u00e9es"));
            Rendu r = rendre("CONVOCATION_AG", p, "CONVOCATION_AGE_SARL_" + type);
            assertThat(r.modele()).isEqualTo("CONVOCATION_AGE_SARL");
            propre(r);
            assertThat(r.texte()).contains("assembl\u00e9e g\u00e9n\u00e9rale extraordinaire du 15/06/2026",
                    "M. Karim BENALI", "Mme Sofia ALAOUI", "En ma qualit\u00e9 de g\u00e9rant",
                    "le rapport de la g\u00e9rance", "Une formule de pouvoir est jointe");
        }
    }

    @Test
    @DisplayName("seconde convocation : modele des incidents, premiere assemblee rappelee")
    @SuppressWarnings("unchecked")
    void seconde_convocation() throws Exception {
        Map<String, Object> p = fixture("CONVOCATION_AG");
        Map<String, Object> c = sous(p, "convocation");
        c.put("rang", "deuxi\u00e8me");
        c.put("dateAgPremiere", "2026-06-01");
        c.put("date", "2026-06-05");
        Map<String, Object> res = ((List<Map<String, Object>>) p.get("resolutions")).get(0);
        res.put("resultat", "rejet\u00e9e");
        res.put("voixPour", "600");
        res.put("voixContre", "400");
        Rendu r = rendre("CONVOCATION_AG", p, "SECONDE_CONVOCATION_ASSEMBLEE_SARL");
        assertThat(r.modele()).isEqualTo("SECONDE_CONVOCATION_ASSEMBLEE_SARL");
        propre(r);
        assertThat(r.texte()).contains("ordinaire du 15/06/2026",
                "convoqu\u00e9s une premi\u00e8re fois par lettre recommand\u00e9e avec accus\u00e9 de r\u00e9ception en date du 15/05/2026",
                "se r\u00e9unir le 01/06/2026 \u00e0 10H00, au si\u00e8ge social",
                "d\u00e9tenaient ensemble 600 parts",
                "r\u00e9solution rejet\u00e9e par 600 voix pour, 400 voix contre",
                "Fait \u00e0 Casablanca, le 05/06/2026");
        assertThat(r.texte()).doesNotContain("ne s'applique pas");
    }

    @Test
    @DisplayName("feuille de presence ordinaire : modele AGO du corpus, presence lue, majorite calculee")
    void feuille_ordinaire() throws Exception {
        Rendu r = rendre("FEUILLE_PRESENCE_AG", fixture("FEUILLE_PRESENCE_AG"), "FEUILLE_PRESENCE_AGO_SARL");
        assertThat(r.modele()).isEqualTo("FEUILLE_PRESENCE_AGO_SARL");
        propre(r);
        assertThat(r.texte()).contains("Exercice clos le 31/12/2025",
                "sur convocation de M. Karim BENALI, g\u00e9rant",
                "Pr\u00e9sent en personne.", "Absent et non repr\u00e9sent\u00e9.",
                "titulaire de la CIN n\u00b0 BE123456",
                "Nombre d'associ\u00e9s pr\u00e9sents en personne : 1.",
                "600 parts (SIX CENTS)", "Quotit\u00e9 du capital correspondante : 60\u00a0%.",
                "susceptible d'\u00eatre r\u00e9unie",
                "R\u00e9f. dossier : 2026-0007 \u2014 PARACOSME");
    }

    @Test
    @DisplayName("feuille de presence extraordinaire : modele AGE, associe represente, seuil des trois quarts")
    @SuppressWarnings("unchecked")
    void feuille_extraordinaire() throws Exception {
        Map<String, Object> p = fixture("FEUILLE_PRESENCE_AG");
        sous(p, "seance").put("type", "extraordinaire");
        Map<String, Object> alaoui = ((List<Map<String, Object>>) p.get("associes")).get(1);
        alaoui.put("presence", "repr\u00e9sent\u00e9");
        alaoui.put("mandataireNom", "M. Omar TAZI");
        alaoui.put("pouvoirDate", "2026-06-10");
        Rendu r = rendre("FEUILLE_PRESENCE_AG", p, "FEUILLE_PRESENCE_AGE_SARL");
        assertThat(r.modele()).isEqualTo("FEUILLE_PRESENCE_AGE_SARL");
        propre(r);
        assertThat(r.texte()).contains("ASSEMBL\u00c9E G\u00c9N\u00c9RALE EXTRAORDINAIRE DU 15/06/2026",
                "repr\u00e9sent\u00e9(e) par M. Omar TAZI, en vertu d'un pouvoir en date du 10/06/2026",
                "1000 parts (MILLE)", "atteint. L'assembl\u00e9e peut valablement d\u00e9lib\u00e9rer");
        assertThat(r.texte()).doesNotContain("non atteint");
    }
}

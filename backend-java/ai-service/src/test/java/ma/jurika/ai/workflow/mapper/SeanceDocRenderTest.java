package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A — Rendu bout-en-bout des 2 documents de séance partagés (Convocation + Feuille
 * de présence) via {@link SeanceDocumentMapper} + {@link SeancePvVarsBuilder} +
 * {@link DocxTemplateEngine}, en SARL (pluripersonnelle) et SARL AU (associé unique).
 *
 * <p>Assertions : couverture 100 % ({@code missingVariables()} vide), 0 marqueur résiduel
 * ({@code $NU}, {@code ◇◆▼▲▶◀}, « VALEUR MANQUANTE »), et présence des mentions clés.
 */
class SeanceDocRenderTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static DocxTemplateEngine engine;
    private final SeanceDocumentMapper mapper = new SeanceDocumentMapper();

    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");

    private static synchronized DocxTemplateEngine engine() {
        if (engine == null) {
            TemplateManifestLoader loader = new TemplateManifestLoader(OM);
            loader.load();
            engine = new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
        }
        return engine;
    }

    private RenderResult render(String code, Map<String, Object> payload) {
        Map<String, Object> vars = mapper.map(code, payload);
        DocumentResult res = engine().generate(code, vars);
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(paraText(p)).append('\n');
            }
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) sb.append(paraText(p)).append('\t');
                    }
                    sb.append('\n');
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new RenderResult(res, sb.toString());
    }

    private static String paraText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    private record RenderResult(DocumentResult res, String text) {}

    private void assertClean(String code, RenderResult r) {
        // Grammaire d'assemblage (2026-08-17) — vérifie la SOUDURE entre le texte du
        // modèle et la valeur injectée (« à au siège social », « DE le gérant unique »,
        // « de droit Allemagne »), que la non-vacuité ne voit pas.
        java.util.List<String> assemblage__ =
                ma.jurika.ai.document.format.AssemblageFautif.dans(r.text());
        assertTrue(assemblage__.isEmpty(), code + " : assemblage fautif -> " + assemblage__);
        assertTrue(r.res().missingVariables().isEmpty(),
                code + " : variables NON résolues = " + r.res().missingVariables());
        assertFalse(BARE_VAR.matcher(r.text()).find(),
                code + " : marqueur $VAR résiduel dans:\n" + r.text());
        for (String marker : new String[]{"◇", "◆", "▼", "▲", "▶", "◀", "VALEUR MANQUANTE"}) {
            assertFalse(r.text().contains(marker), code + " : marqueur résiduel « " + marker + " ».");
        }
    }

    @SafeVarargs
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static Map<String, Object> societe(boolean au) {
        return m("denomination", "PARACOSME", "capitalChiffres", 100000L, "siegeSocial",
                "101 bd Zerktouni, Casablanca", "nombreParts", 1000L, "rcNumero", "123456",
                "villeGreffe", "Casablanca", "formeJuridique", au ? "SARL_AU" : "SARL");
    }

    private static Map<String, Object> seance() {
        return m("type", "ordinaire", "date", "2026-03-15", "heure", "10 heures",
                "lieu", "au siège social", "heureCloture", "11 heures",
                "presidentNom", "M. Yassine BENANI", "presidentQualite", "gérant");
    }

    private Map<String, Object> base(boolean au) {
        Map<String, Object> p = m(
                "societe", societe(au), "seance", seance(),
                "convocation", m("auteur", "la gérance", "date", "2026-03-01",
                        "mode", "lettre recommandée avec accusé de réception", "rang", "première",
                        "lieuSignature", "Casablanca"),
                "gerants", List.of(m("civilite", "M", "prenom", "Yassine", "nom", "BENANI")),
                "ordreDuJour", List.of("Approbation des comptes annuels", "Affectation du résultat"),
                "documentsJoints", List.of("Rapport de la gérance", "Texte des résolutions",
                        "Formule de pouvoir"),
                "resolutions", List.of(m("intitule", "Approbation des comptes", "texte",
                        "L'assemblée approuve les comptes.", "resultat", "adoptée")));
        if (au) {
            p.put("associeUnique", true);
            p.put("associes", List.of(m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom",
                    "Omar", "nom", "CHERKAOUI", "adresse", "12 rue X, Casablanca",
                    "nombreParts", 1000L, "presence", "présent")));
        } else {
            p.put("associes", List.of(
                    m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom",
                            "BENANI", "adresse", "5 rue A, Casablanca", "nombreParts", 600L,
                            "presence", "présent"),
                    m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom",
                            "IDRISSI", "adresse", "8 rue B, Rabat", "nombreParts", 400L,
                            "presence", "représenté", "mandataireNom", "M. BENANI")));
        }
        return p;
    }

    @Test
    void convocation_ag_sarl() {
        RenderResult r = render("CONVOCATION_AG", base(false));
        assertClean("CONVOCATION_AG", r);
        assertTrue(r.text().contains("PARACOSME"), "dénomination absente");
        assertTrue(r.text().contains("BENANI") && r.text().contains("IDRISSI"), "associés absents");
        assertTrue(r.text().contains("SOCIÉTÉ À RESPONSABILITÉ LIMITÉE"), "forme absente");
        assertTrue(r.text().toLowerCase().contains("ordre du jour")
                || r.text().contains("Approbation des comptes annuels"), "ODJ absent");
    }

    @Test
    void convocation_ag_sarl_au() {
        RenderResult r = render("CONVOCATION_AG", base(true));
        assertClean("CONVOCATION_AG", r);
        assertTrue(r.text().contains("CHERKAOUI"), "associé unique absent");
        assertTrue(r.text().contains("ASSOCIÉ UNIQUE"), "mention associé unique absente");
    }

    @Test
    void feuille_presence_ag_sarl() {
        RenderResult r = render("FEUILLE_PRESENCE_AG", base(false));
        assertClean("FEUILLE_PRESENCE_AG", r);
        assertTrue(r.text().contains("PARACOSME"), "dénomination absente");
        assertTrue(r.text().contains("BENANI") && r.text().contains("IDRISSI"), "associés absents");
        assertTrue(r.text().contains("1"), "totaux absents");
    }

    @Test
    void feuille_presence_ag_sarl_au() {
        RenderResult r = render("FEUILLE_PRESENCE_AG", base(true));
        assertClean("FEUILLE_PRESENCE_AG", r);
        assertTrue(r.text().contains("CHERKAOUI"), "associé unique absent");
        assertTrue(r.text().contains("ASSOCIÉ UNIQUE"), "mention associé unique absente");
    }

    @Test
    void genererEchantillons() {
        java.nio.file.Path out = java.nio.file.Path.of("target", "echantillons-seance-ag");
        try {
            java.nio.file.Files.createDirectories(out);
            for (String code : new String[]{"CONVOCATION_AG", "FEUILLE_PRESENCE_AG"}) {
                DocumentResult res = engine().generate(code, mapper.map(code, base(false)));
                java.nio.file.Files.write(out.resolve(code + "_SARL.docx"), res.bytes());
                DocumentResult resAu = engine().generate(code, mapper.map(code, base(true)));
                java.nio.file.Files.write(out.resolve(code + "_SARL_AU.docx"), resAu.bytes());
            }
        } catch (Exception ex) {
            System.out.println("[echantillons séance] ignoré : " + ex.getMessage());
        }
    }
}

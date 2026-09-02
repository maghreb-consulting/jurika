package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lot DIVERS §C (2026-08-13) — annonce légale d'ouverture, <b>variante ÉTRANGÈRE</b>.
 *
 * <p>⚠ Modèles {@code origin = derive-jurika} : dérivés des modèles 05_ du directeur (qui
 * ne couvrent qu'une mère marocaine), à faire valider par lui.
 *
 * <p>Couvre : (1) les variables produites ({@code $SOCIETE_MERE_*}, {@code $ORGANE_COMPETENT},
 * corps succursale identique au PV) ; (2) le rendu de bout en bout, SARL et SARL AU, dans les
 * quatre combinaisons des blocs conditionnels, sans marqueur résiduel ; (3) l'ABSENCE totale
 * de mention marocaine dans l'avis publié (« DH » du capital mère, RC du siège) — c'est
 * précisément ce qui interdisait de réutiliser le modèle 05_ tel quel.
 *
 * <p>Échantillons persistés dans {@code target/echantillons-annonce-succursale-etr}.
 */
class SuccursaleEtrAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-succursale-etr");

    private static final String TPL_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_ETR_SARL;
    private static final String TPL_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_ETR_SARL_AU;

    private static DocxTemplateEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
        Files.createDirectories(OUT);
    }

    // ── Payload helpers ────────────────────────────────────────────────────

    private static Map<String, Object> societeMere() {
        Map<String, Object> m = new HashMap<>();
        m.put("denomination", "GLOBAL TRADING");
        m.put("forme", "Limited");
        m.put("pays", "Royaume-Uni");
        m.put("capital", "500 000 GBP");
        m.put("siege", "10 Downing Street, Londres");
        m.put("registre", "Companies House");
        m.put("registreNumero", "GB1234567");
        m.put("loiApplicable", "loi anglaise");
        return m;
    }

    private static Map<String, Object> succursale(boolean dotation, boolean responsable) {
        Map<String, Object> s = new HashMap<>();
        s.put("enseigne", "GLOBAL TRADING — Succursale Maroc");
        s.put("adresse", "18 Boulevard Anfa");
        s.put("ville", "Casablanca");
        s.put("villeGreffe", "CASABLANCA");
        s.put("activite", "import-export");
        s.put("dateOuverture", "2026-11-02");
        s.put("dotationPresente", dotation);
        if (dotation) s.put("dotationMontant", 1_000_000);
        s.put("responsablePresent", responsable);
        if (responsable) {
            s.put("responsable", Map.of(
                    "civilite", "M.", "prenom", "Youssef", "nom", "EL FASSI",
                    "nationalite", "marocaine", "adresse", "42 RUE ANFA, CASABLANCA",
                    "pieceType", "CIN", "pieceNumero", "BK998877",
                    "pouvoirs", "diriger la succursale et representer la societe au Maroc"));
        }
        return s;
    }

    private static Map<String, Object> payload(String forme, boolean dotation, boolean responsable) {
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societeMere", societeMere());
        p.put("organe", Map.of("competent", "le conseil d'administration",
                "date", "2026-10-05", "lieu", "Londres"));
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-10-05"));
        p.put("succursale", succursale(dotation, responsable));
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        return new SuccursaleEtrMapper().map(templateCode, payload);
    }

    // ── (1) Variables produites ────────────────────────────────────────────

    @Test
    void variables_mereEtrangere_organe_etGreffeSuccursale() {
        Map<String, Object> v = vars(TPL_SARL, payload("SARL", true, true));
        assertEquals("GLOBAL TRADING", v.get("SOCIETE_MERE_DENOMINATION"));
        assertEquals("Limited", v.get("SOCIETE_MERE_FORME"));
        assertEquals("Royaume-Uni", v.get("SOCIETE_MERE_PAYS"));
        assertEquals("500 000 GBP", v.get("SOCIETE_MERE_CAPITAL"));
        assertEquals("Companies House", v.get("SOCIETE_MERE_REGISTRE"));
        assertEquals("le conseil d'administration", v.get("ORGANE_COMPETENT"));
        assertEquals("05/10/2026", v.get("ASSEMBLEE_DATE"));
        assertEquals("CASABLANCA", v.get("SUCCURSALE_VILLE_GREFFE"));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        assertEquals("[à compléter après immatriculation]", v.get("DATE_DEPOT_LEGAL"));
        assertEquals("[à compléter après immatriculation]", v.get("DEPOT_LEGAL_NUMERO"));
    }

    @Test
    void assembleeDate_repliSurLaDateDeDecisionDeLOrgane() {
        Map<String, Object> p = payload("SARL", false, false);
        p.remove("seance"); // aucune séance : seule la décision de l'organe existe
        assertEquals("05/10/2026", vars(TPL_SARL, p).get("ASSEMBLEE_DATE"));
    }

    // ── (2) Rendu — 4 combinaisons + SARL AU ───────────────────────────────

    @Test
    void render_sarl_dotationOui_responsableOui() throws Exception {
        String text = render(TPL_SARL, "ETR_SARL_dotation_oui_resp_oui",
                payload("SARL", true, true));
        assertTrue(text.contains("GLOBAL TRADING"), "mère rendue");
        assertTrue(text.contains("Royaume-Uni"), "pays rendu");
        assertTrue(text.contains("500 000 GBP"), "capital en devise d'origine rendu");
        assertTrue(text.contains("Companies House"), "registre étranger rendu");
        assertTrue(text.contains("le conseil d'administration"), "organe rendu");
        assertTrue(text.contains("dotation"), "bloc dotation rendu");
        assertTrue(text.contains("EL FASSI"), "bloc responsable rendu");
    }

    /**
     * L'organe compétent est une valeur SAISIE qui porte son propre article
     * (« le conseil d'administration », « l'associé unique »…). La phrase dérivée doit
     * donc être « prise PAR », jamais « de » — sinon on publie « de le conseil ».
     */
    @Test
    void aucuneElisionFautiveDevantLOrganeCompetent() throws Exception {
        for (String organe : new String[]{
                "le conseil d'administration", "l'associé unique", "les actionnaires",
                "la gérance"}) {
            Map<String, Object> p = payload("SARL", false, false);
            p.put("organe", Map.of("competent", organe, "date", "2026-10-05"));
            String text = render(TPL_SARL, "ETR_elision_" + organe.replaceAll("[^a-zA-Z]", ""), p);
            assertTrue(text.contains("prise par " + organe),
                    "organe « " + organe + " » mal introduit : " + text);
            for (String faute : new String[]{"de le ", "de les ", "à le ", "à les "}) {
                assertFalse(text.contains(faute),
                        "élision fautive « " + faute.trim() + " » avec l'organe « " + organe + " »");
            }
        }
    }

    @Test
    void render_sarl_dotationNon_responsableNon() throws Exception {
        String text = render(TPL_SARL, "ETR_SARL_dotation_non_resp_non",
                payload("SARL", false, false));
        assertFalse(text.contains("dotation de"), "bloc dotation NON rendu");
        assertFalse(text.contains("en qualité de responsable"), "bloc responsable NON rendu");
    }

    @Test
    void render_sarl_dotationOui_responsableNon() throws Exception {
        String text = render(TPL_SARL, "ETR_SARL_dotation_oui_resp_non",
                payload("SARL", true, false));
        assertTrue(text.contains("dotation"), "bloc dotation rendu");
        assertFalse(text.contains("en qualité de responsable"), "bloc responsable NON rendu");
    }

    @Test
    void render_sarl_dotationNon_responsableOui() throws Exception {
        String text = render(TPL_SARL, "ETR_SARL_dotation_non_resp_oui",
                payload("SARL", false, true));
        assertFalse(text.contains("dotation de"), "bloc dotation NON rendu");
        assertTrue(text.contains("EL FASSI"), "bloc responsable rendu");
    }

    @Test
    void render_sarlAu() throws Exception {
        Map<String, Object> p = payload("SARL_AU", true, true);
        p.put("organe", Map.of("competent", "le gérant unique", "date", "2026-10-05"));
        String text = render(TPL_SARL_AU, "ETR_AU_dotation_oui_resp_oui", p);
        assertTrue(text.contains("le gérant unique"), "organe unipersonnel rendu");
    }

    // ── (3) Aucune mention marocaine résiduelle ────────────────────────────

    @Test
    void aucuneMentionMarocaineDansLeChapeau() throws Exception {
        String text = render(TPL_SARL, "ETR_SARL_verif_chapeau", payload("SARL", true, true));
        String chapeau = text.substring(0, Math.min(text.length(), 500));
        // Le capital de la mère est publié dans SA devise : « DH » ne doit pas y figurer.
        assertFalse(chapeau.contains(" DH"),
                "le chapeau ne doit plus imposer le dirham au capital de la mère : " + chapeau);
        // Le siège d'une société étrangère n'a pas de RC marocain.
        assertFalse(chapeau.contains("RC N°"),
                "le chapeau ne doit plus référencer un RC marocain : " + chapeau);
        // La dotation, elle, est bien en dirhams (elle est affectée au Maroc).
        assertTrue(text.contains("DH"), "la dotation reste libellée en dirhams");
    }

    // ── Rendering helper ───────────────────────────────────────────────────

    private String render(String templateCode, String sample, Map<String, Object> payload)
            throws Exception {
        DocxTemplateEngine.DocumentResult result =
                engine.generate(templateCode, vars(templateCode, payload));
        assertTrue(result.templateFound(), "template introuvable : " + templateCode);
        assertTrue(result.bytes() != null && result.bytes().length > 0, "0 octet : " + templateCode);
        Files.write(OUT.resolve(sample + ".docx"), result.bytes());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result.bytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            String text = sb.toString();
            assertNoResidual(text, sample);
            return text;
        }
    }

    private void assertNoResidual(String text, String label) {
        // Grammaire d'assemblage (2026-08-17) — la non-vacuité ne dit RIEN de la façon
        // dont la valeur se raccorde au texte du modèle. On vérifie donc ici, sur chaque
        // document rendu, l'absence de soudure fautive : « à au siège social »,
        // « DE le gérant unique », « de droit Allemagne »…
        java.util.List<String> assemblage =
                ma.jurika.ai.document.format.AssemblageFautif.dans(text);
        assertTrue(assemblage.isEmpty(),
                label + " : assemblage fautif -> " + assemblage);
        Matcher v = RESIDUAL_VAR.matcher(text);
        List<String> residuals = new ArrayList<>();
        while (v.find()) residuals.add(v.group());
        assertTrue(residuals.isEmpty(), label + " : marqueur(s) variable résiduel(s) " + residuals);
        Matcher m = RESIDUAL_MARKER.matcher(text);
        assertFalse(m.find(), label + " : marqueur condition/boucle résiduel");
        assertFalse(text.contains("VALEUR MANQUANTE"), label + " : variable manquante dans le rendu");
    }
}

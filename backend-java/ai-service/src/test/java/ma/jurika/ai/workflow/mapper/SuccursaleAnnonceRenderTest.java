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
 * Lot DIVERS §B (2026-08-13) — annonce légale d'OUVERTURE de succursale.
 *
 * <p>Couvre : (1) les variables produites par
 * {@link SuccursaleVarsBuilder#ouvertureAnnonceVars} (identité mère BD, date d'assemblée,
 * greffe PROPRE à la succursale, dotation, responsable, dépôt légal vide par défaut) ;
 * (2) le rendu de bout en bout (mapper → {@link DocxTemplateEngine}) SARL et SARL AU sans
 * marqueur résiduel ({@code $VAR}, {@code ◇/◆/▼/▲}) ni « VALEUR MANQUANTE », dans les
 * QUATRE combinaisons des deux blocs conditionnels (dotation oui/non × responsable
 * oui/non) ; (3) la cohérence PV ↔ annonce (mêmes faits succursale).
 *
 * <p>Les échantillons rendus sont persistés dans {@code target/echantillons-annonce-succursale}.
 */
class SuccursaleAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-succursale");

    private static final String TPL_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_SARL;
    private static final String TPL_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_SARL_AU;

    private static DocxTemplateEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
        Files.createDirectories(OUT);
    }

    // ── Payload helpers ────────────────────────────────────────────────────

    private static Map<String, Object> societe() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA");
    }

    private static Map<String, Object> succursale(boolean dotation, boolean responsable) {
        Map<String, Object> s = new HashMap<>();
        s.put("enseigne", "PARACOSME — Agence Marrakech");
        s.put("adresse", "5 Avenue Mohammed VI");
        s.put("ville", "Marrakech");
        // Greffe PROPRE à la succursale, distinct de celui du siège (CASABLANCA).
        s.put("villeGreffe", "MARRAKECH");
        s.put("activite", "conseil juridique et fiscal");
        s.put("dateOuverture", "2026-10-01");
        s.put("dotationPresente", dotation);
        if (dotation) s.put("dotationMontant", 250_000);
        s.put("responsablePresent", responsable);
        if (responsable) {
            s.put("responsable", Map.of(
                    "civilite", "M.", "prenom", "Yassine", "nom", "TAZI",
                    "nationalite", "marocaine", "adresse", "8 RUE IBN SINA, MARRAKECH",
                    "pieceType", "CIN", "pieceNumero", "EE123456",
                    "pouvoirs", "la gestion courante de la succursale"));
        }
        return s;
    }

    private static Map<String, Object> payload(String forme, boolean dotation, boolean responsable) {
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-09-05"));
        p.put("succursale", succursale(dotation, responsable));
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        return new SuccursaleMaMapper().map(templateCode, payload);
    }

    // ── (1) Variables produites ────────────────────────────────────────────

    @Test
    void variables_identiteMereBd_greffePropre_etDotation() {
        Map<String, Object> v = vars(TPL_SARL, payload("SARL", true, true));
        assertEquals("PARACOSME", v.get("DENOMINATION"));
        assertEquals("CASABLANCA", v.get("VILLE_GREFFE"), "greffe du SIÈGE");
        assertEquals("MARRAKECH", v.get("SUCCURSALE_VILLE_GREFFE"), "greffe de la SUCCURSALE");
        assertEquals("123456", v.get("RC_NUMERO"));
        assertEquals("05/09/2026", v.get("ASSEMBLEE_DATE"));
        assertEquals("01/10/2026", v.get("SUCCURSALE_DATE_OUVERTURE"));
        assertEquals("oui", v.get("SUCCURSALE_DOTATION_PRESENTE"));
        assertTrue(String.valueOf(v.get("SUCCURSALE_DOTATION_LETTRES")).length() > 0,
                "montant en lettres calculé, jamais saisi");
        assertEquals("oui", v.get("SUCCURSALE_RESPONSABLE_PRESENT"));
        assertEquals("TAZI", v.get("SUCCURSALE_RESPONSABLE_NOM"));
    }

    @Test
    void greffeSuccursale_repliSurLaVille_siNonSaisi() {
        Map<String, Object> p = payload("SARL", false, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> s = (Map<String, Object>) p.get("succursale");
        s.remove("villeGreffe");
        assertEquals("Marrakech", vars(TPL_SARL, p).get("SUCCURSALE_VILLE_GREFFE"));
    }

    @Test
    void depotLegal_videParDefaut_etFormateSiRenseigne() {
        Map<String, Object> v = vars(TPL_SARL, payload("SARL", true, true));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DATE_DEPOT_LEGAL") == null || v.get("DATE_DEPOT_LEGAL").toString().isBlank(), "L3 : DATE_DEPOT_LEGAL externe absente, marquee par le moteur");
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DEPOT_LEGAL_NUMERO") == null || v.get("DEPOT_LEGAL_NUMERO").toString().isBlank(), "L3 : DEPOT_LEGAL_NUMERO externe absente, marquee par le moteur");

        Map<String, Object> p = payload("SARL", true, true);
        p.put("depotLegal", Map.of("date", "2026-10-20", "numero", "78945"));
        Map<String, Object> v2 = vars(TPL_SARL, p);
        assertEquals("20/10/2026", v2.get("DATE_DEPOT_LEGAL"));
        assertEquals("78945", v2.get("DEPOT_LEGAL_NUMERO"));
    }

    // ── (2) Rendu — 4 combinaisons des blocs conditionnels ─────────────────

    @Test
    void render_sarl_dotationOui_responsableOui() throws Exception {
        String text = render(TPL_SARL, "SARL_dotation_oui_resp_oui", payload("SARL", true, true));
        assertTrue(text.contains("assemblée générale extraordinaire"), "chapeau SARL");
        assertTrue(text.contains("PARACOSME — Agence Marrakech"), "enseigne rendue");
        assertTrue(text.contains("dotation"), "bloc dotation rendu");
        assertTrue(text.contains("TAZI"), "bloc responsable rendu");
        assertTrue(text.contains("MARRAKECH"), "greffe de la succursale rendu");
    }

    @Test
    void render_sarl_dotationNon_responsableNon() throws Exception {
        String text = render(TPL_SARL, "SARL_dotation_non_resp_non", payload("SARL", false, false));
        assertFalse(text.contains("dotation de"), "bloc dotation NON rendu");
        assertFalse(text.contains("en qualité de responsable"), "bloc responsable NON rendu");
    }

    @Test
    void render_sarl_dotationOui_responsableNon() throws Exception {
        String text = render(TPL_SARL, "SARL_dotation_oui_resp_non", payload("SARL", true, false));
        assertTrue(text.contains("dotation"), "bloc dotation rendu");
        assertFalse(text.contains("en qualité de responsable"), "bloc responsable NON rendu");
    }

    @Test
    void render_sarl_dotationNon_responsableOui() throws Exception {
        String text = render(TPL_SARL, "SARL_dotation_non_resp_oui", payload("SARL", false, true));
        assertFalse(text.contains("dotation de"), "bloc dotation NON rendu");
        assertTrue(text.contains("TAZI"), "bloc responsable rendu");
    }

    @Test
    void render_sarlAu_chapeauAssocieUnique() throws Exception {
        String text = render(TPL_SARL_AU, "AU_dotation_oui_resp_oui", payload("SARL_AU", true, true));
        assertTrue(text.contains("l'associé unique"), "chapeau SARL AU rendu");
        assertTrue(text.contains("SARL AU au capital"), "forme SARL AU rendue");
    }

    @Test
    void render_avecDepotLegal() throws Exception {
        Map<String, Object> p = payload("SARL", true, true);
        p.put("depotLegal", Map.of("date", "2026-10-20", "numero", "78945"));
        String text = render(TPL_SARL, "SARL_depot_renseigne", p);
        assertTrue(text.contains("78945"), "numéro de dépôt rendu");
        assertTrue(text.contains("20/10/2026"), "date de dépôt rendue");
    }

    // ── (3) Cohérence PV ↔ annonce ─────────────────────────────────────────

    @Test
    void pvEtAnnonce_partagentLesMemesFaitsSuccursale() {
        Map<String, Object> p = payload("SARL", true, true);
        SuccursaleMaMapper mapper = new SuccursaleMaMapper();
        Map<String, Object> pv = mapper.map(SuccursaleVarsBuilder.TPL_MA_SARL, p);
        Map<String, Object> annonce = mapper.map(TPL_SARL, p);
        for (String key : new String[]{
                "SUCCURSALE_ENSEIGNE", "SUCCURSALE_ADRESSE", "SUCCURSALE_VILLE",
                "SUCCURSALE_ACTIVITE", "SUCCURSALE_DATE_OUVERTURE", "SUCCURSALE_VILLE_GREFFE",
                "SUCCURSALE_DOTATION_PRESENTE", "SUCCURSALE_DOTATION_CHIFFRES",
                "SUCCURSALE_DOTATION_LETTRES", "SUCCURSALE_RESPONSABLE_PRESENT",
                "SUCCURSALE_RESPONSABLE_NOM", "SUCCURSALE_RESPONSABLE_POUVOIRS"}) {
            assertEquals(pv.get(key), annonce.get(key), key + " doit être identique PV / annonce");
        }
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

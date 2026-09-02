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
 * Lot DIVERS §D (2026-08-13) — annonce légale de FERMETURE de succursale.
 *
 * <p>Couvre : (1) les variables produites (identité mère BD, RC de la succursale, date
 * d'effet, motif, dépôt légal vide par défaut) ; (2) le rendu de bout en bout SARL et
 * SARL AU sans marqueur résiduel ; (3) la publication effective du <b>motif</b> — c'est
 * la différence de fond avec l'avis de dissolution, où le motif reste au PV.
 *
 * <p>Échantillons persistés dans {@code target/echantillons-annonce-fermeture}.
 */
class FermetureAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-fermeture");

    private static final String TPL_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_FERM_SARL;
    private static final String TPL_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_FERM_SARL_AU;

    private static DocxTemplateEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
        Files.createDirectories(OUT);
    }

    private static Map<String, Object> societe() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA");
    }

    private static Map<String, Object> payload(String forme) {
        Map<String, Object> succ = new HashMap<>();
        succ.put("enseigne", "PARACOSME — Agence Marrakech");
        succ.put("adresse", "5 Avenue Mohammed VI");
        succ.put("ville", "Marrakech");
        succ.put("villeGreffe", "MARRAKECH");
        succ.put("activite", "conseil juridique et fiscal");
        succ.put("rcNumero", "78901");
        succ.put("dateFermeture", "2026-10-31");
        succ.put("motif", "la reorganisation du reseau commercial");

        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-09-20"));
        p.put("succursale", succ);
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        return new FermetureSuccursaleMapper().map(templateCode, payload);
    }

    // ── (1) Variables produites ────────────────────────────────────────────

    @Test
    void variables_rc_dateEffet_motif_etGreffeSuccursale() {
        Map<String, Object> v = vars(TPL_SARL, payload("SARL"));
        assertEquals("PARACOSME", v.get("DENOMINATION"));
        assertEquals("CASABLANCA", v.get("VILLE_GREFFE"), "greffe du SIÈGE");
        assertEquals("MARRAKECH", v.get("SUCCURSALE_VILLE_GREFFE"), "greffe de la SUCCURSALE");
        assertEquals("78901", v.get("SUCCURSALE_RC_NUMERO"));
        assertEquals("31/10/2026", v.get("SUCCURSALE_DATE_FERMETURE"));
        assertEquals("la reorganisation du reseau commercial", v.get("SUCCURSALE_MOTIF"));
        assertEquals("20/09/2026", v.get("ASSEMBLEE_DATE"));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        assertEquals("[à compléter après immatriculation]", v.get("DATE_DEPOT_LEGAL"));
        assertEquals("[à compléter après immatriculation]", v.get("DEPOT_LEGAL_NUMERO"));
    }

    @Test
    void dateFermeture_accepteLAliasDateEffet() {
        Map<String, Object> p = payload("SARL");
        @SuppressWarnings("unchecked")
        Map<String, Object> s = (Map<String, Object>) p.get("succursale");
        s.remove("dateFermeture");
        s.put("dateEffet", "2026-11-15");
        assertEquals("15/11/2026", vars(TPL_SARL, p).get("SUCCURSALE_DATE_FERMETURE"));
    }

    // ── (2) Rendu de bout en bout ──────────────────────────────────────────

    @Test
    void render_sarl() throws Exception {
        String text = render(TPL_SARL, "FERMETURE_SARL", payload("SARL"));
        assertTrue(text.contains("assemblée générale extraordinaire"), "chapeau SARL");
        assertTrue(text.contains("PARACOSME — Agence Marrakech"), "enseigne rendue");
        assertTrue(text.contains("78901"), "RC de la succursale rendu");
        assertTrue(text.contains("31/10/2026"), "date d'effet rendue");
        assertTrue(text.contains("MARRAKECH"), "greffe de radiation rendu");
    }

    @Test
    void render_sarlAu_chapeauAssocieUnique() throws Exception {
        String text = render(TPL_SARL_AU, "FERMETURE_AU", payload("SARL_AU"));
        assertTrue(text.contains("l'associé unique"), "chapeau SARL AU rendu");
    }

    @Test
    void render_avecDepotLegal() throws Exception {
        Map<String, Object> p = payload("SARL");
        p.put("depotLegal", Map.of("date", "2026-11-05", "numero", "44112"));
        String text = render(TPL_SARL, "FERMETURE_SARL_depot_renseigne", p);
        assertTrue(text.contains("44112"), "numéro de dépôt rendu");
        assertTrue(text.contains("05/11/2026"), "date de dépôt rendue");
    }

    // ── (3) Le motif EST publié ────────────────────────────────────────────

    @Test
    void leMotifEstPublieDansLAvis() throws Exception {
        // Différence de fond avec l'avis de dissolution, où le motif reste au PV.
        String text = render(TPL_SARL, "FERMETURE_SARL_motif", payload("SARL"));
        assertTrue(text.contains("motivée par"), "la phrase de motivation est rendue");
        assertTrue(text.contains("la reorganisation du reseau commercial"),
                "le motif saisi est publié : " + text);
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

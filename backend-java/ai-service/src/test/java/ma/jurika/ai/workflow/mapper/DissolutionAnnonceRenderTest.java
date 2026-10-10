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
 * Lot « Dissolution 4 étapes » (2026-08-12) — annonce légale de dissolution.
 *
 * <p>Couvre : (1) les variables produites par {@link DissolutionAnnonceVarsBuilder} (identité BD,
 * date de dissolution, liquidateur, siège de la liquidation, dépôt légal vide par défaut) ;
 * (2) le rendu de bout en bout (mapper → {@link DocxTemplateEngine}) SARL et SARL AU sans marqueur
 * résiduel ({@code $VAR}, {@code ◇/◆/▼/▲}) ni « VALEUR MANQUANTE », y compris avec
 * {@code $DEPOT_LEGAL_*} vides ; (3) le routage par {@link DissolutionMapper}.
 *
 * <p>Les échantillons rendus sont persistés dans {@code target/echantillons-annonce-dissolution}.
 */
class DissolutionAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-dissolution");
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

    private static Map<String, Object> liquidateur() {
        Map<String, Object> l = new HashMap<>();
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
        l.put("siege", "12 RUE DES FOULES, CASABLANCA");
        return l;
    }

    private static Map<String, Object> payload(String forme) {
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-05-15"));
        p.put("dissolution", Map.of("motif", "volontaire", "date", "2026-05-15"));
        p.put("liquidateur", liquidateur());
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        return new DissolutionMapper().map(templateCode, payload);
    }

    // ── (1) Variables produites ────────────────────────────────────────────

    @Test
    void variables_identiteBd_liquidateur_etSiegeLiquidation() {
        Map<String, Object> v = vars(DissolutionAnnonceVarsBuilder.TPL_SARL, payload("SARL"));
        assertEquals("PARACOSME", v.get("DENOMINATION"));
        assertEquals("CASABLANCA", v.get("VILLE_GREFFE"));
        assertEquals("123456", v.get("RC_NUMERO"));
        assertEquals("15/05/2026", v.get("DATE_DISSOLUTION"));
        assertEquals("M.", v.get("LIQUIDATEUR_CIVILITE"));
        assertEquals("Ahmed", v.get("LIQUIDATEUR_PRENOM"));
        assertEquals("ALAOUI", v.get("LIQUIDATEUR_NOM"));
        assertEquals("45 BD ZERKTOUNI, CASABLANCA", v.get("LIQUIDATEUR_ADRESSE"));
        assertEquals("12 RUE DES FOULES, CASABLANCA", v.get("SIEGE_LIQUIDATION"));
    }

    @Test
    void depotLegalVide_parDefaut() {
        Map<String, Object> v = vars(DissolutionAnnonceVarsBuilder.TPL_SARL, payload("SARL"));
        // Grammaire d'assemblage (2026-08-17) — ATTENTE CORRIGÉE. Ces deux valeurs sont
        // attribuées par le greffe APRÈS le dépôt : les rendre VIDES produisait la phrase
        // trouée « … le  sous le numéro  RC N° 123456 », que rien ne signalait. On reprend
        // le marqueur déjà employé par la CRÉATION : l'avis dit ce qui reste à compléter.
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DATE_DEPOT_LEGAL") == null || v.get("DATE_DEPOT_LEGAL").toString().isBlank(), "L3 : DATE_DEPOT_LEGAL externe absente, marquee par le moteur");
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DEPOT_LEGAL_NUMERO") == null || v.get("DEPOT_LEGAL_NUMERO").toString().isBlank(), "L3 : DEPOT_LEGAL_NUMERO externe absente, marquee par le moteur");
    }

    @Test
    void depotLegalRenseigne_estFormate() {
        Map<String, Object> p = payload("SARL");
        p.put("depotLegal", Map.of("date", "2026-06-01", "numero", "78945"));
        Map<String, Object> v = vars(DissolutionAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("01/06/2026", v.get("DATE_DEPOT_LEGAL"));
        assertEquals("78945", v.get("DEPOT_LEGAL_NUMERO"));
    }

    @Test
    void siegeLiquidation_repliSurSiegeSocial_siNonSaisi() {
        Map<String, Object> p = payload("SARL");
        Map<String, Object> l = liquidateur();
        l.remove("siege");
        p.put("liquidateur", l);
        Map<String, Object> v = vars(DissolutionAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("12 RUE DES FOULES, CASABLANCA", v.get("SIEGE_LIQUIDATION"));
    }

    @Test
    void dateDissolution_repliSurDateSeance() {
        Map<String, Object> p = payload("SARL");
        p.remove("dissolution");
        Map<String, Object> v = vars(DissolutionAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("15/05/2026", v.get("DATE_DISSOLUTION"));
    }

    // ── (2) Rendu de bout en bout — aucun marqueur résiduel ────────────────

    @Test
    void render_sarl_depotLegalVide() throws Exception {
        String text = render(DissolutionAnnonceVarsBuilder.TPL_SARL, "SARL_depot_vide",
                payload("SARL"));
        assertTrue(text.contains("PARACOSME"), "dénomination rendue");
        assertTrue(text.contains("assemblée générale extraordinaire"), "chapeau SARL rendu");
        assertTrue(text.contains("Ahmed"), "liquidateur rendu");
        assertTrue(text.contains("12 RUE DES FOULES, CASABLANCA"), "siège de la liquidation rendu");
    }

    @Test
    void render_sarlAu_chapeauAssocieUnique() throws Exception {
        String text = render(DissolutionAnnonceVarsBuilder.TPL_SARL_AU, "AU_depot_vide",
                payload("SARL_AU"));
        assertTrue(text.contains("l'associé unique"), "chapeau SARL AU rendu");
        assertTrue(text.contains("SARL AU au capital"), "forme SARL AU rendue");
    }

    @Test
    void render_sarl_avecDepotLegal() throws Exception {
        Map<String, Object> p = payload("SARL");
        p.put("depotLegal", Map.of("date", "2026-06-01", "numero", "78945"));
        String text = render(DissolutionAnnonceVarsBuilder.TPL_SARL, "SARL_depot_renseigne", p);
        assertTrue(text.contains("78945"), "numéro de dépôt rendu");
        assertTrue(text.contains("01/06/2026"), "date de dépôt rendue");
    }

    // ── (3) Routage mapper ─────────────────────────────────────────────────

    @Test
    void mapper_supporteLesDeuxAnnonces_etLePvUnifie() {
        DissolutionMapper mapper = new DissolutionMapper();
        assertTrue(mapper.supportedTemplates().contains(DissolutionAnnonceVarsBuilder.TPL_SARL));
        assertTrue(mapper.supportedTemplates().contains(DissolutionAnnonceVarsBuilder.TPL_SARL_AU));
        assertTrue(mapper.supportedTemplates()
                .contains(DissolutionLiquidationMapper.TPL_PV_SARL));
        assertEquals("DISSOLUTION", mapper.workflowCode());
    }

    // ── Rendering helper ───────────────────────────────────────────────────

    private String render(String templateCode, String sample, Map<String, Object> payload)
            throws Exception {
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars(templateCode, payload));
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
        assertFalse(!residuals.isEmpty(), label + " : marqueur(s) variable résiduel(s) " + residuals);
        Matcher m = RESIDUAL_MARKER.matcher(text);
        assertFalse(m.find(), label + " : marqueur condition/boucle résiduel");
        assertFalse(text.contains("VALEUR MANQUANTE"), label + " : variable manquante dans le rendu");
    }
}

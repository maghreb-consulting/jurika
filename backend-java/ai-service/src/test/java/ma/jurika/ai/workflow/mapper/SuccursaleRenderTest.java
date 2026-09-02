package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D — Test de rendu de bout en bout (mapper → {@link DocxTemplateEngine}) des 6 modèles
 * directeur Succursale, avec persistance des échantillons dans {@code target/echantillons-succursale/}
 * pour contrôle visuel.
 *
 * <p>Couvre : création MA (avec/sans dotation & responsable), création étrangère (société mère +
 * organe compétent), fermeture — pour SARL et SARL AU. Pour chaque rendu : aucun marqueur résiduel
 * ({@code $VAR} directeur, condition {@code ◇/◆}, boucle {@code ▼/▲}), et présence du contenu
 * directeur attendu.
 */
class SuccursaleRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path SAMPLES = Path.of("target", "echantillons-succursale");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SuccursaleMaMapper maMapper = new SuccursaleMaMapper();
    private final SuccursaleEtrMapper etrMapper = new SuccursaleEtrMapper();
    private final FermetureSuccursaleMapper fermMapper = new FermetureSuccursaleMapper();
    private DocxTemplateEngine engine;

    @BeforeAll
    static void prepareSamplesDir() throws Exception {
        Files.createDirectories(SAMPLES);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> fixtures(String file) throws Exception {
        try (InputStream is = SuccursaleRenderTest.class.getResourceAsStream(
                "/workflow-fixtures/" + file)) {
            return JSON.readValue(is, Map.class);
        }
    }

    private String render(WorkflowMapperCall call, String templateCode, Map<String, Object> payload,
                          String sampleName) throws Exception {
        Map<String, Object> vars = call.map(templateCode, payload);
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars);
        assertTrue(result.templateFound(), "template introuvable : " + templateCode);
        assertTrue(result.bytes() != null && result.bytes().length > 0, "0 octet : " + templateCode);
        Files.write(SAMPLES.resolve(sampleName + ".docx"), result.bytes());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result.bytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            return sb.toString();
        }
    }

    @FunctionalInterface
    interface WorkflowMapperCall {
        Map<String, Object> map(String templateCode, Map<String, Object> payload);
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
        assertFalse(v.find(), label + " : marqueur variable résiduel " + (v.reset().find() ? v.group() : ""));
        Matcher m = RESIDUAL_MARKER.matcher(text);
        assertFalse(m.find(), label + " : marqueur condition/boucle résiduel");
    }

    // ── Création MA ────────────────────────────────────────────────────────

    @Test
    void creationMaSarl_avecDotationEtResponsable() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_ma.json");
        String text = render(maMapper::map, "PV_CREATION_SUCCURSALE_MAROC_SARL",
                fx.get("PV_CREATION_SUCCURSALE_MAROC_SARL"), "MA_SARL_avec-dotation-responsable");
        assertNoResidual(text, "MA SARL (dotation + responsable)");
        assertTrue(text.contains("PARACOSME"), "dénomination rendue");
        assertTrue(text.contains("dotation"), "dotation rendue");
        assertTrue(text.contains("TAZI"), "responsable rendu (résolution)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void creationMaSarl_sansDotationNiResponsable() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_ma.json");
        Map<String, Object> payload = new HashMap<>(fx.get("PV_CREATION_SUCCURSALE_MAROC_SARL"));
        Map<String, Object> succ = new HashMap<>((Map<String, Object>) payload.get("succursale"));
        succ.put("dotationPresente", "non");
        succ.remove("dotationMontant");
        succ.put("responsablePresent", "non");
        succ.remove("responsable");
        payload.put("succursale", succ);
        String text = render(maMapper::map, "PV_CREATION_SUCCURSALE_MAROC_SARL", payload,
                "MA_SARL_sans-dotation-responsable");
        assertNoResidual(text, "MA SARL (sans dotation ni responsable)");
    }

    @Test
    void creationMaSarlAu() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_ma.json");
        String text = render(maMapper::map, "PV_CREATION_SUCCURSALE_MAROC_SARL_AU",
                fx.get("PV_CREATION_SUCCURSALE_MAROC_SARL_AU"), "MA_SARL_AU");
        assertNoResidual(text, "MA SARL AU");
        assertTrue(text.contains("CHERKAOUI"), "associé unique rendu");
    }

    // ── Création étrangère ───────────────────────────────────────────────────

    @Test
    void creationEtrSarl() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_etr.json");
        String text = render(etrMapper::map, "PV_CREATION_SUCCURSALE_ETRANGERE_SARL",
                fx.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL"), "ETR_SARL");
        assertNoResidual(text, "ETR SARL");
        assertTrue(text.contains("GLOBAL TRADING LTD"), "société mère rendue");
        assertTrue(text.contains("conseil d'administration"), "organe compétent rendu");
        assertTrue(text.contains("EL FASSI"), "représentant rendu (résolution)");
    }

    @Test
    void creationEtrSarlAu() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_etr.json");
        String text = render(etrMapper::map, "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                fx.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU"), "ETR_SARL_AU");
        assertNoResidual(text, "ETR SARL AU");
        assertTrue(text.contains("MEDITERRANEA GMBH"), "société mère rendue");
        assertTrue(text.contains("gérant unique"), "organe unique rendu");
    }

    // ── Fermeture ────────────────────────────────────────────────────────────

    @Test
    void fermetureSarl() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("fermeture_succursale.json");
        String text = render(fermMapper::map, "PV_FERMETURE_SUCCURSALE_SARL",
                fx.get("PV_FERMETURE_SUCCURSALE_SARL"), "FERMETURE_SARL");
        assertNoResidual(text, "Fermeture SARL");
        assertTrue(text.contains("fermer la succursale") || text.contains("FERMETURE"),
                "objet fermeture rendu");
        assertTrue(text.contains("78901"), "RC succursale rendu");
    }

    @Test
    void fermetureSarlAu() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("fermeture_succursale.json");
        String text = render(fermMapper::map, "PV_FERMETURE_SUCCURSALE_SARL_AU",
                fx.get("PV_FERMETURE_SUCCURSALE_SARL_AU"), "FERMETURE_SARL_AU");
        assertNoResidual(text, "Fermeture SARL AU");
        assertTrue(text.contains("CHERKAOUI"), "associé unique rendu");
    }
}

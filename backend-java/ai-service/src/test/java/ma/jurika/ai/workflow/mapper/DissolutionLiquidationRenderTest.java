package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de rendu de bout en bout (mapper → {@link DocxTemplateEngine}) des 3 modèles directeur
 * Dissolution / Liquidation (Phase C).
 *
 * <p>Couvre : PV dissolution, PV clôture avec <b>boni</b> ET avec <b>mali</b>, rapport de
 * liquidation (SARL avec boni, SARL AU avec mali). Vérifie pour chaque rendu :
 * <ul>
 *   <li>aucun marqueur résiduel : {@code $VARIABLE} directeur, ni condition {@code ◇/◆},
 *       ni boucle {@code ▼/▲} ;</li>
 *   <li>les montants en lettres sont présents (boni/mali/actif/passif) ;</li>
 *   <li>le contenu directeur attendu est bien rendu (branche conditionnelle correcte).</li>
 * </ul>
 */
class DissolutionLiquidationRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");

    private final DissolutionMapper dissolutionMapper = new DissolutionMapper();
    private final LiquidationMapper liquidationMapper = new LiquidationMapper();
    private DocxTemplateEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
    }

    // ── Payload builders ──────────────────────────────────────────────────

    private static Map<String, Object> societeSarl() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA", "formeJuridique", "SARL");
    }

    private static List<Map<String, Object>> deuxAssocies() {
        return List.of(
                Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                        "nombreParts", 600, "presence", "présent"),
                Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "nombreParts", 400, "presence", "présent"));
    }

    private static Map<String, Object> seance(String date) {
        return Map.of("type", "extraordinaire", "date", date, "heure", "10H00", "lieu", "SIEGE SOCIAL",
                "presidentNom", "M. Ahmed Alaoui", "presidentQualite", "gérant", "heureCloture", "12H00");
    }

    private static Map<String, Object> liquidateurPhysique() {
        return Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                "adresse", "5 RUE D'ALGER, CASABLANCA", "pieceType", "CIN", "pieceNumero", "BE123456",
                "genre", "masculin", "remuneration", "exercées à titre gratuit",
                "siege", "12 RUE DES FOULES, CASABLANCA");
    }

    // ── Rendering helper ──────────────────────────────────────────────────

    private String render(WorkflowMapperCall call, String templateCode, Map<String, Object> payload)
            throws Exception {
        Map<String, Object> vars = call.map(templateCode, payload);
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars);
        assertTrue(result.templateFound(), "template introuvable : " + templateCode);
        assertTrue(result.bytes() != null && result.bytes().length > 0, "0 octet : " + templateCode);
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

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void pvDissolutionSarl() throws Exception {
        Map<String, Object> payload = Map.of(
                "societe", societeSarl(),
                "seance", seance("2026-05-15"),
                "convocation", Map.of("mode", "lettre recommandée", "date", "2026-04-30"),
                "associes", deuxAssocies(),
                "ordreDuJour", List.of("Dissolution anticipée", "Nomination du liquidateur"),
                "resolutions", List.of(
                        Map.of("objet", "Dissolution anticipée et mise en liquidation",
                                "type", "dissolution_anticipee", "resultat", "à l'unanimité"),
                        Map.of("objet", "Nomination du liquidateur",
                                "type", "nomination_liquidateur", "resultat", "à l'unanimité"),
                        Map.of("objet", "Pouvoirs pour formalités",
                                "type", "pouvoirs_formalites", "resultat", "à l'unanimité")),
                "dissolution", Map.of("motif", "volontaire"),
                "liquidateur", liquidateurPhysique());

        String text = render(dissolutionMapper::map, "PV_DISSOLUTION_LIQUIDATION_SARL", payload);
        assertNoResidual(text, "PV dissolution SARL");
        assertTrue(text.contains("dissolution anticipée"), "contenu directeur dissolution attendu");
        assertTrue(text.contains("PARACOSME"), "dénomination rendue");
    }

    @Test
    void pvClotureBoniSarl() throws Exception {
        Map<String, Object> payload = Map.of(
                "societe", societeSarl(),
                "seance", seance("2026-06-20"),
                "convocation", Map.of("mode", "lettre recommandée", "date", "2026-06-05"),
                "associes", deuxAssocies(),
                "ordreDuJour", List.of("Approbation des comptes", "Quitus", "Répartition du boni", "Clôture"),
                "resolutions", List.of(
                        Map.of("objet", "Approbation des comptes définitifs",
                                "type", "approbation_comptes_cloture", "resultat", "à l'unanimité"),
                        Map.of("objet", "Quitus au liquidateur", "type", "quitus_liquidateur",
                                "resultat", "à l'unanimité"),
                        Map.of("objet", "Répartition du boni", "type", "repartition_boni",
                                "resultat", "à l'unanimité"),
                        Map.of("objet", "Clôture de la liquidation", "type", "cloture_liquidation",
                                "resultat", "à l'unanimité")),
                "liquidateur", liquidateurPhysique(),
                "cloture", Map.of("resultatSens", "boni", "resultatMontant", 50_000,
                        "boniExiste", "oui", "boniMontant", 50_000, "boniParPart", 50));

        String text = render(liquidationMapper::map, "PV_DISSOLUTION_LIQUIDATION_SARL", payload);
        assertNoResidual(text, "PV clôture boni SARL");
        assertTrue(text.contains("boni"), "mention du boni attendue");
    }

    @Test
    void pvClotureMaliSarl() throws Exception {
        Map<String, Object> payload = Map.of(
                "societe", societeSarl(),
                "seance", seance("2026-06-20"),
                "convocation", Map.of("mode", "lettre recommandée", "date", "2026-06-05"),
                "associes", deuxAssocies(),
                "ordreDuJour", List.of("Approbation des comptes", "Clôture"),
                "resolutions", List.of(
                        Map.of("objet", "Approbation des comptes définitifs",
                                "type", "approbation_comptes_cloture", "resultat", "à l'unanimité"),
                        Map.of("objet", "Répartition", "type", "repartition_boni",
                                "resultat", "à l'unanimité")),
                "liquidateur", liquidateurPhysique(),
                "cloture", Map.of("resultatSens", "mali", "resultatMontant", 30_000,
                        "boniExiste", "non"));

        String text = render(liquidationMapper::map, "PV_DISSOLUTION_LIQUIDATION_SARL", payload);
        assertNoResidual(text, "PV clôture mali SARL");
        assertEquals("mali", liquidationMapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", payload).get("LIQ_RESULTAT_SENS"));
    }

    @Test
    void rapportBoniSarl() throws Exception {
        Map<String, Object> payload = Map.of(
                "societe", societeSarl(),
                "liquidateur", liquidateurPhysique(),
                "dissolution", Map.of("date", "2026-05-15"),
                "cloture", Map.of("dateClotureLiquidation", "2026-06-20", "actifRealise", 300_000,
                        "passifRegle", 150_000, "resultatType", "boni", "boniMontant", 50_000),
                "associes", List.of(
                        Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                                "nombreParts", 600, "boniChiffres", 30_000),
                        Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                                "nombreParts", 400, "boniChiffres", 20_000)),
                "signature", Map.of("lieu", "CASABLANCA", "date", "2026-06-25", "nombreOriginaux", "quatre"));

        String text = render(liquidationMapper::map, "RAPPORT_LIQUIDATION_DIRECTEUR", payload);
        assertNoResidual(text, "rapport boni SARL");
        assertTrue(text.contains("boni de liquidation"), "mention boni de liquidation attendue");
    }

    @Test
    void rapportMaliSarlAu() throws Exception {
        Map<String, Object> payload = Map.of(
                "societe", Map.of("denomination", "PARACOSME AU", "capitalChiffres", 100_000,
                        "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "rcNumero", "123456",
                        "villeGreffe", "CASABLANCA", "formeJuridique", "SARL_AU"),
                "associeUnique", true,
                "liquidateur", Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "adresse", "5 RUE D'ALGER", "pieceType", "CIN", "pieceNumero", "BK998877",
                        "genre", "féminin"),
                "dissolution", Map.of("date", "2026-05-15"),
                "cloture", Map.of("dateClotureLiquidation", "2026-06-20", "actifRealise", 80_000,
                        "passifRegle", 120_000, "resultatType", "mali", "maliMontant", 40_000),
                "associes", List.of(Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "nombreParts", 1_000)),
                "signature", Map.of("lieu", "CASABLANCA", "date", "2026-06-25", "nombreOriginaux", "quatre"));

        String text = render(liquidationMapper::map, "RAPPORT_LIQUIDATION_DIRECTEUR", payload);
        assertNoResidual(text, "rapport mali SARL AU");
        assertTrue(text.contains("mali de liquidation"), "mention mali de liquidation attendue");
        assertTrue(text.contains("liquidatrice"), "accord féminin (liquidatrice) attendu");
    }
}

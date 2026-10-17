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
 * Lot « Liquidation 4 étapes » (2026-08-13) — annonce légale de clôture de liquidation.
 *
 * <p>Couvre : (1) les variables produites par {@link LiquidationAnnonceVarsBuilder} (identité BD,
 * date de clôture, liquidateur repris de la dissolution, boni/mali DÉRIVÉ des comptes finaux,
 * dépôt légal vide par défaut) ; (2) le rendu de bout en bout (mapper →
 * {@link DocxTemplateEngine}) SARL et SARL AU, <b>branche boni</b> et <b>branche mali</b>, sans
 * marqueur résiduel ({@code $VAR}, {@code ◇/◆/▼/▲}) ni « VALEUR MANQUANTE », y compris avec
 * {@code $DEPOT_LEGAL_*} vides ; (3) le routage par {@link LiquidationMapper}.
 *
 * <p>Les échantillons rendus sont persistés dans {@code target/echantillons-annonce-liquidation}.
 */
class LiquidationAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-liquidation");
    private static DocxTemplateEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        // Lot L4d (E11) : l'avis est rendu par le modele du corpus (copies du classpath retirees).
        java.nio.file.Path racine = java.nio.file.Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
                System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.isRegularFile(racine.resolve("INDEX_CORPUS.xlsx")), "corpus reel absent");
        engine = new DocxTemplateEngine(loader, null, ma.jurika.ai.document.corpus.ChargeurCorpus.charger(racine));
        Files.createDirectories(OUT);
    }

    // ── Payload helpers ────────────────────────────────────────────────────

    private static Map<String, Object> societe() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA", "ville", "Casablanca");
    }

    /** Liquidateur nommé à la dissolution, relu depuis la BD (jamais re-saisi ici). */
    private static Map<String, Object> liquidateur() {
        Map<String, Object> l = new HashMap<>();
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 boulevard Zerktouni, Casablanca");
        l.put("pieceType", "CIN");
        l.put("pieceNumero", "BE123456");
        return l;
    }

    /**
     * Payload de clôture : seuls l'actif et le passif sont saisis — le sens (boni / mali) et
     * le montant en découlent, jamais re-saisis.
     */
    private static Map<String, Object> payload(String forme, long actif, long passif) {
        Map<String, Object> cloture = new HashMap<>();
        cloture.put("dateClotureLiquidation", "2026-06-15");
        cloture.put("actifRealise", actif);
        cloture.put("passifRegle", passif);
        // Lot L4d (E11) : donnees du modele du corpus (avis de cloture), saisies au parcours.
        cloture.put("dissolutionDateEffet", "2026-03-01");
        cloture.put("compteDefinitifDate", "2026-06-10");
        cloture.put("siegeLiquidation", "12 rue des Foules, Casablanca");
        Map<String, Object> p = new HashMap<>();
        p.put("associes", java.util.List.of(Map.of("civilite", "M.", "prenom", "Karim", "nom", "BENALI",
                "typePersonne", "PHYSIQUE", "nombreParts", 1000)));
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-06-15"));
        p.put("liquidateur", liquidateur());
        p.put("cloture", cloture);
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        // Meme chaine qu'en production : mapper, puis reprise de la fiche de la societe.
        return ma.jurika.ai.workflow.identity.RepriseFicheSociete.completer(
                new LiquidationMapper().map(templateCode, payload), payload);
    }

    // ── (1) Variables produites ────────────────────────────────────────────

    @Test
    void variables_identiteBd_dateCloture_etLiquidateur() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 500_000, 300_000));
        assertEquals("PARACOSME", v.get("DENOMINATION"));
        assertEquals("CASABLANCA", v.get("VILLE_GREFFE"));
        assertEquals("123456", v.get("RC_NUMERO"));
        assertEquals("15/06/2026", v.get("DATE_CLOTURE_LIQUIDATION"));
        assertEquals("M.", v.get("LIQUIDATEUR_CIVILITE"));
        assertEquals("Ahmed", v.get("LIQUIDATEUR_PRENOM"));
        assertEquals("ALAOUI", v.get("LIQUIDATEUR_NOM"));
    }

    /**
     * L6b (E19) : l'avis du corpus ne publie pas le resultat ; il ne porte plus de variable de
     * resultat (actif - passif, ou sens envoye par l'ecran). La seule source est le rapport final.
     */
    @Test
    void aucuneVariableDeResultat_niSoldeNiSensDeLEcran() {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        @SuppressWarnings("unchecked")
        Map<String, Object> cloture = (Map<String, Object>) p.get("cloture");
        cloture.put("resultatType", "mali");
        cloture.put("resultatMontant", 200_000);
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, p);
        org.assertj.core.api.Assertions.assertThat(v).doesNotContainKeys("RESULTAT_LIQUIDATION_TYPE",
                "BONI_LIQUIDATION_CHIFFRES", "MALI_LIQUIDATION_CHIFFRES");
    }

    @Test
    void depotLegalVide_parDefaut() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 500_000, 300_000));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DATE_DEPOT_LEGAL") == null || v.get("DATE_DEPOT_LEGAL").toString().isBlank(), "L3 : DATE_DEPOT_LEGAL externe absente, marquee par le moteur");
        org.junit.jupiter.api.Assertions.assertTrue(v.get("DEPOT_LEGAL_NUMERO") == null || v.get("DEPOT_LEGAL_NUMERO").toString().isBlank(), "L3 : DEPOT_LEGAL_NUMERO externe absente, marquee par le moteur");
    }

    @Test
    void depotLegalRenseigne_estFormate() {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        p.put("depotLegal", Map.of("date", "2026-07-01", "numero", "78945"));
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("01/07/2026", v.get("DATE_DEPOT_LEGAL"));
        assertEquals("78945", v.get("DEPOT_LEGAL_NUMERO"));
    }

    @Test
    void dateCloture_repliSurDateSeance() {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        @SuppressWarnings("unchecked")
        Map<String, Object> cloture = (Map<String, Object>) p.get("cloture");
        cloture.remove("dateClotureLiquidation");
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("15/06/2026", v.get("DATE_CLOTURE_LIQUIDATION"));
    }

    // ── (2) Rendu de bout en bout — aucun marqueur résiduel ────────────────

    @Test
    void render_sarl_boni() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL, "SARL_boni",
                payload("SARL", 500_000, 300_000));
        assertTrue(text.contains("PARACOSME"), "dénomination rendue");
        // Lot L4d (E11) : l'avis du corpus ne publie pas le resultat (boni ou mali).
        assertTrue(text.contains("r\u00e9unis en assembl\u00e9e de cl\u00f4ture"), "chapeau SARL rendu");
        assertTrue(text.contains("ont approuv\u00e9 le compte d\u00e9finitif de la liquidation"), "compte d\u00e9finitif approuv\u00e9");
        assertTrue(text.contains("ALAOUI"), "liquidateur rendu");
    }

    @Test
    void render_sarl_mali() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL, "SARL_mali",
                payload("SARL", 300_000, 500_000));
        assertTrue(text.contains("Ils ont d\u00e9charg\u00e9 le liquidateur de son mandat"), "quitus SARL");
    }

    @Test
    void render_sarlAu_boni_chapeauAssocieUnique() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL_AU, "AU_boni",
                payload("SARL_AU", 500_000, 300_000));
        assertTrue(text.contains("M. Karim BENALI, associ\u00e9 unique de la soci\u00e9t\u00e9"), "associ\u00e9 unique nomm\u00e9");
        assertFalse(text.contains("oui, associ\u00e9 unique"), "drapeau non imprim\u00e9");
        assertTrue(text.contains("\u00e0 associ\u00e9 unique en liquidation"), "forme SARL AU rendue");
    }

    @Test
    void render_sarlAu_mali() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL_AU, "AU_mali",
                payload("SARL_AU", 300_000, 500_000));
        assertTrue(text.contains("L'associ\u00e9 unique a d\u00e9charg\u00e9 le liquidateur de son mandat"), "quitus SARL AU");
    }

    @Test
    void render_sarl_avecDepotLegal() throws Exception {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        p.put("depotLegal", Map.of("date", "2026-07-01", "numero", "78945"));
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL, "SARL_depot_renseigne", p);
        // Le modele du corpus publie la date du depot, pas son numero.
        assertTrue(text.contains("a \u00e9t\u00e9 effectu\u00e9 au greffe"), "d\u00e9p\u00f4t rendu");
        assertTrue(text.contains("01/07/2026"), "date de d\u00e9p\u00f4t rendue");
    }

    // ── (3) Routage mapper ─────────────────────────────────────────────────

    @Test
    void mapper_supporteLesDeuxAnnonces_lePvUnifie_etLeRapport() {
        LiquidationMapper mapper = new LiquidationMapper();
        assertTrue(mapper.supportedTemplates().contains(LiquidationAnnonceVarsBuilder.TPL_SARL));
        assertTrue(mapper.supportedTemplates().contains(LiquidationAnnonceVarsBuilder.TPL_SARL_AU));
        assertTrue(mapper.supportedTemplates().contains(LiquidationMapper.TPL_PV_SARL));
        assertTrue(mapper.supportedTemplates().contains(LiquidationMapper.TPL_RAPPORT));
        assertEquals("LIQUIDATION", mapper.workflowCode());
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

    /** Montant formate FR : les separateurs de milliers (NBSP / NNBSP) sont normalises. */
    private static String amount(Object raw) {
        return raw == null ? "" : String.valueOf(raw).replaceAll("[\s\u00A0\u202F]+", " ");
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

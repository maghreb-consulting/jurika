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
        engine = new DocxTemplateEngine(loader);
        Files.createDirectories(OUT);
    }

    // ── Payload helpers ────────────────────────────────────────────────────

    private static Map<String, Object> societe() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA");
    }

    /** Liquidateur nommé à la dissolution, relu depuis la BD (jamais re-saisi ici). */
    private static Map<String, Object> liquidateur() {
        Map<String, Object> l = new HashMap<>();
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
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
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-06-15"));
        p.put("liquidateur", liquidateur());
        p.put("cloture", cloture);
        return p;
    }

    private static Map<String, Object> vars(String templateCode, Map<String, Object> payload) {
        return new LiquidationMapper().map(templateCode, payload);
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

    @Test
    void boni_deriveDesComptesFinaux_sansSaisieSeparee() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 500_000, 300_000));
        assertEquals("boni", v.get("RESULTAT_LIQUIDATION_TYPE"));
        assertEquals("200 000", amount(v.get("BONI_LIQUIDATION_CHIFFRES")));
        assertEquals("", v.get("MALI_LIQUIDATION_CHIFFRES"));
    }

    @Test
    void mali_deriveDesComptesFinaux_montantEnValeurAbsolue() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 300_000, 500_000));
        assertEquals("mali", v.get("RESULTAT_LIQUIDATION_TYPE"));
        assertEquals("200 000", amount(v.get("MALI_LIQUIDATION_CHIFFRES")));
        assertEquals("", v.get("BONI_LIQUIDATION_CHIFFRES"));
    }

    @Test
    void soldeNul_estUnBoni() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 300_000, 300_000));
        assertEquals("boni", v.get("RESULTAT_LIQUIDATION_TYPE"));
    }

    @Test
    void sensExplicite_prioritaireSurLeCalcul() {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        @SuppressWarnings("unchecked")
        Map<String, Object> cloture = (Map<String, Object>) p.get("cloture");
        cloture.put("resultatType", "mali");
        cloture.put("resultatMontant", 200_000);
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, p);
        assertEquals("mali", v.get("RESULTAT_LIQUIDATION_TYPE"));
        assertEquals("", v.get("BONI_LIQUIDATION_CHIFFRES"));
    }

    @Test
    void depotLegalVide_parDefaut() {
        Map<String, Object> v = vars(LiquidationAnnonceVarsBuilder.TPL_SARL,
                payload("SARL", 500_000, 300_000));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        assertEquals("[à compléter après immatriculation]", v.get("DATE_DEPOT_LEGAL"));
        assertEquals("[à compléter après immatriculation]", v.get("DEPOT_LEGAL_NUMERO"));
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
        assertTrue(text.contains("assemblée générale extraordinaire"), "chapeau SARL rendu");
        assertTrue(text.contains("boni de liquidation"), "branche boni retenue");
        assertFalse(text.contains("mali de liquidation"), "branche mali élaguée");
        assertTrue(text.contains("ALAOUI"), "liquidateur rendu");
    }

    @Test
    void render_sarl_mali() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL, "SARL_mali",
                payload("SARL", 300_000, 500_000));
        assertTrue(text.contains("mali de liquidation"), "branche mali retenue");
        assertFalse(text.contains("boni de liquidation"), "branche boni élaguée");
        assertTrue(text.contains("dans la limite de leurs apports"), "clause SARL pluripersonnelle");
    }

    @Test
    void render_sarlAu_boni_chapeauAssocieUnique() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL_AU, "AU_boni",
                payload("SARL_AU", 500_000, 300_000));
        assertTrue(text.contains("l'associé unique"), "chapeau SARL AU rendu");
        assertTrue(text.contains("SARL AU au capital"), "forme SARL AU rendue");
        assertTrue(text.contains("attribué en totalité à l'associé unique"), "boni attribué à l'AU");
    }

    @Test
    void render_sarlAu_mali() throws Exception {
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL_AU, "AU_mali",
                payload("SARL_AU", 300_000, 500_000));
        assertTrue(text.contains("mali de liquidation"), "branche mali retenue");
        assertTrue(text.contains("dans la limite de son apport"), "clause SARL AU");
    }

    @Test
    void render_sarl_avecDepotLegal() throws Exception {
        Map<String, Object> p = payload("SARL", 500_000, 300_000);
        p.put("depotLegal", Map.of("date", "2026-07-01", "numero", "78945"));
        String text = render(LiquidationAnnonceVarsBuilder.TPL_SARL, "SARL_depot_renseigne", p);
        assertTrue(text.contains("78945"), "numéro de dépôt rendu");
        assertTrue(text.contains("01/07/2026"), "date de dépôt rendue");
    }

    // ── (3) Routage mapper ─────────────────────────────────────────────────

    @Test
    void mapper_supporteLesDeuxAnnonces_lePvUnifie_etLeRapport() {
        LiquidationMapper mapper = new LiquidationMapper();
        assertTrue(mapper.supportedTemplates().contains(LiquidationAnnonceVarsBuilder.TPL_SARL));
        assertTrue(mapper.supportedTemplates().contains(LiquidationAnnonceVarsBuilder.TPL_SARL_AU));
        assertTrue(mapper.supportedTemplates().contains(DissolutionLiquidationMapper.TPL_PV_SARL));
        assertTrue(mapper.supportedTemplates().contains(DissolutionLiquidationMapper.TPL_RAPPORT));
        assertEquals("LIQUIDATION", mapper.workflowCode());
    }

    /**
     * L'avis et le rapport de liquidation doivent porter le MÊME résultat : les deux lisent
     * le même bloc {@code cloture} du payload (aucune double saisie possible).
     */
    @Test
    void annonceEtRapport_partagentLeMemeResultat() {
        Map<String, Object> p = payload("SARL", 300_000, 500_000);
        @SuppressWarnings("unchecked")
        Map<String, Object> cloture = (Map<String, Object>) p.get("cloture");
        cloture.put("resultatType", "mali");
        cloture.put("resultatMontant", 200_000);
        Map<String, Object> annonce = vars(LiquidationAnnonceVarsBuilder.TPL_SARL, p);
        Map<String, Object> rapport = vars(DissolutionLiquidationMapper.TPL_RAPPORT, p);
        assertEquals(rapport.get("RESULTAT_LIQUIDATION_TYPE"),
                annonce.get("RESULTAT_LIQUIDATION_TYPE"));
        assertEquals(rapport.get("MALI_LIQUIDATION_CHIFFRES"),
                annonce.get("MALI_LIQUIDATION_CHIFFRES"));
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

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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 (2026-08-11) — Annonce légale de modification.
 *
 * <p>Couvre : (1) la <b>correspondance</b> {@code resolutionType → $DECISION_TYPE} et le
 * <b>filtre des NON-PUBLIABLES</b> (assertions sur la boucle {@code DECISIONS} produite par
 * {@link ModificationAnnonceVarsBuilder}) ; (2) le <b>rendu de bout en bout</b> (mapper →
 * {@link DocxTemplateEngine}) sans marqueur résiduel ({@code $VAR}, {@code ◇/◆/▼/▲}), pour les
 * familles publiables, en SARL et SARL AU ; (3) le rendu avec {@code $DEPOT_LEGAL_*} vides.
 */
class ModificationAnnonceRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");
    private static final Path OUT = Path.of("target", "echantillons-annonce-modification");
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

    private static Map<String, Object> seance() {
        return Map.of("type", "extraordinaire", "date", "2026-05-15", "heure", "10H00",
                "lieu", "SIEGE SOCIAL", "presidentNom", "M. Ahmed Alaoui");
    }

    private static Map<String, Object> payload(String forme, List<Map<String, Object>> resolutions) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("formeJuridique", forme);
        p.put("societe", societe());
        p.put("seance", seance());
        p.put("resolutions", resolutions);
        return p;
    }

    private static Map<String, Object> res(String type, Object... kv) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) r.put(String.valueOf(kv[i]), kv[i + 1]);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> decisions(Map<String, Object> vars) {
        return (List<Map<String, Object>>) vars.getOrDefault("DECISIONS", List.of());
    }

    // ── (1) Correspondance + filtre des NON-PUBLIABLES ─────────────────────

    @Test
    void filtreNonPubliables_etCorrespondanceDecisionType() {
        List<Map<String, Object>> resolutions = List.of(
                res("approbation_comptes"),            // NON publiable
                res("modification_exercice"),          // NON publiable
                res("commissaire_comptes"),            // NON publiable
                res("nantissement_parts"),             // NON publiable
                res("modification_denomination", "nouvelleDenomination", "NEWCO"), // publiable
                res("transfert_siege", "siegeMemePrefecture", "non", "nouveauSiege", "RABAT")); // publiable
        Map<String, Object> vars =
                ModificationAnnonceVarsBuilder.build("ANNONCE_LEGALE_MODIFICATION_SARL", payload("SARL", resolutions));
        List<Map<String, Object>> dec = decisions(vars);
        assertEquals(2, dec.size(), "seules les 2 décisions publiables doivent produire une ligne");
        assertEquals("denomination", dec.get(0).get("DECISION_TYPE"));
        assertEquals("siege_hors_ressort", dec.get(1).get("DECISION_TYPE"));
        assertEquals("NEWCO", dec.get(0).get("DENOMINATION_NOUVELLE"));
    }

    @Test
    void aucuneDecisionPubliable_boucleVide() {
        Map<String, Object> vars = ModificationAnnonceVarsBuilder.build(
                "ANNONCE_LEGALE_MODIFICATION_SARL",
                payload("SARL", List.of(res("approbation_comptes"), res("commissaire_comptes"))));
        assertTrue(decisions(vars).isEmpty(), "aucune ligne d'avis si aucune décision publiable");
    }

    @Test
    void transfertSiege_memeRessort_vsHorsRessort() {
        Map<String, Object> vMeme = ModificationAnnonceVarsBuilder.build(
                "ANNONCE_LEGALE_MODIFICATION_SARL",
                payload("SARL", List.of(res("transfert_siege", "siegeMemePrefecture", "oui",
                        "nouveauSiege", "CASABLANCA"))));
        assertEquals("siege_meme_ressort", decisions(vMeme).get(0).get("DECISION_TYPE"));
        Map<String, Object> vHors = ModificationAnnonceVarsBuilder.build(
                "ANNONCE_LEGALE_MODIFICATION_SARL",
                payload("SARL", List.of(res("transfert_siege", "siegeMemePrefecture", "non",
                        "nouveauSiege", "RABAT", "villeGreffeArrivee", "RABAT"))));
        assertEquals("siege_hors_ressort", decisions(vHors).get(0).get("DECISION_TYPE"));
    }

    @Test
    void cessionPluripersonnelle_directionSelonForme() {
        Map<String, Object> au = ModificationAnnonceVarsBuilder.build(
                "ANNONCE_LEGALE_MODIFICATION_SARL_AU",
                payload("SARL_AU", List.of(res("cession_parts_pluripersonnelle",
                        "cessionnaireNom", "M. Tazi"))));
        assertEquals("passage_au_vers_sarl", decisions(au).get(0).get("DECISION_TYPE"));
    }

    @Test
    void depotLegalVide_parDefaut() {
        Map<String, Object> vars = ModificationAnnonceVarsBuilder.build(
                "ANNONCE_LEGALE_MODIFICATION_SARL",
                payload("SARL", List.of(res("modification_denomination", "nouvelleDenomination", "X"))));
        // 2026-08-17 — attribué par le greffe APRÈS le dépôt : marqueur explicite
        // au lieu d'un blanc, qui laissait « … le  sous le numéro  ».
        org.junit.jupiter.api.Assertions.assertTrue(vars.get("DEPOT_LEGAL_NUMERO") == null || vars.get("DEPOT_LEGAL_NUMERO").toString().isBlank(), "L3 : DEPOT_LEGAL_NUMERO externe absente, marquee par le moteur");
        org.junit.jupiter.api.Assertions.assertTrue(vars.get("DATE_DEPOT_LEGAL") == null || vars.get("DATE_DEPOT_LEGAL").toString().isBlank(), "L3 : DATE_DEPOT_LEGAL externe absente, marquee par le moteur");
    }

    // ── (2) Rendu de bout en bout — aucun marqueur résiduel ────────────────

    @Test
    void render_sarl_multiDecisions() throws Exception {
        List<Map<String, Object>> resolutions = List.of(
                res("modification_denomination", "nouvelleDenomination", "PARACOSME CONSULTING"),
                res("modification_objet", "objetAction", "extension",
                        "objetModification", "le conseil en organisation"),
                res("transfert_siege", "siegeMemePrefecture", "non", "nouveauSiege",
                        "45 BD ZERKTOUNI, RABAT", "villeGreffeArrivee", "RABAT"),
                res("augmentation_capital_numeraire", "augcapMontantChiffres", 50_000,
                        "augcapNouveauCapital", 150_000, "primeEmission", "oui",
                        "primeEmissionMontant", 20_000),
                res("agrement_cession", "cedantNom", "M. Alaoui", "cessionnaireNom", "M. Tazi",
                        "cessionNbParts", 100, "cessionPrix", 100_000),
                res("commissaire_comptes")); // filtré
        String text = render("ANNONCE_LEGALE_MODIFICATION_SARL", "SARL_multi", payload("SARL", resolutions));
        assertTrue(text.contains("PARACOSME CONSULTING"), "nouvelle dénomination rendue");
        assertTrue(text.contains("Augmentation du capital social"), "augmentation rendue");
        assertTrue(text.contains("Tazi"), "cessionnaire rendu");
    }

    @Test
    void render_sarlAu_denominationEtCession() throws Exception {
        List<Map<String, Object>> resolutions = List.of(
                res("modification_denomination", "nouvelleDenomination", "PARACOSME AU CONSULTING"),
                res("cession_parts_pluripersonnelle", "cessionnaireNom", "M. Youssef Tazi",
                        "cessionNbParts", 400, "cessionPrix", 400_000));
        String text = render("ANNONCE_LEGALE_MODIFICATION_SARL_AU", "AU_denom_cession",
                payload("SARL_AU", resolutions));
        assertTrue(text.contains("PARACOSME AU CONSULTING"), "nouvelle dénomination AU rendue");
    }

    @Test
    void render_sarl_reductionEtTransformation() throws Exception {
        List<Map<String, Object>> resolutions = List.of(
                res("reduction_capital", "redcapMontant", 30_000, "redcapNouveauCapital", 70_000,
                        "redcapMotif", "pertes"),
                res("transformation", "transformationForme", "société anonyme"));
        String text = render("ANNONCE_LEGALE_MODIFICATION_SARL", "SARL_reduction_transfo",
                payload("SARL", resolutions));
        assertTrue(text.contains("Réduction du capital social"), "réduction rendue");
        assertTrue(text.contains("société anonyme"), "transformation rendue");
    }

    // ── Rendering helper ───────────────────────────────────────────────────

    private String render(String templateCode, String sample, Map<String, Object> payload)
            throws Exception {
        Map<String, Object> vars = ModificationAnnonceVarsBuilder.build(templateCode, payload);
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars);
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

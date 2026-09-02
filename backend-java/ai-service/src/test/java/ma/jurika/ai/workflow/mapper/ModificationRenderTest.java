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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase E1 — Rendu de bout en bout (mapper → {@link DocxTemplateEngine}) du PV de Modification à
 * résolutions typées. Couvre <b>au moins un cas par famille</b> (transfert de siège, augmentation
 * numéraire avec DPS, réduction, cession de parts, nomination + révocation de gérant, affectation
 * du résultat + dividende, transformation) et un <b>PV multi-résolutions</b>, en SARL et SARL AU.
 *
 * <p>Vérifie pour chaque rendu : aucun marqueur résiduel ({@code $VAR}, {@code ◇/◆/▼/▲}), aucune
 * variable manquante (« VALEUR MANQUANTE »), et présence du contenu directeur attendu. Les
 * <b>échantillons</b> sont persistés dans {@code target/echantillons-modification/}.
 */
class ModificationRenderTest {

    private static final Pattern RESIDUAL_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    private static final Pattern RESIDUAL_MARKER = Pattern.compile("[\\u25C7\\u25C6\\u25BC\\u25B2]");

    private static final Path OUT = Path.of("target", "echantillons-modification");
    private static DocxTemplateEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
        Files.createDirectories(OUT);
    }

    // ── Payload fragments ──────────────────────────────────────────────────

    private static Map<String, Object> societeSarl() {
        return Map.of("denomination", "PARACOSME", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA", "formeJuridique", "SARL");
    }

    private static Map<String, Object> societeAu() {
        return Map.of("denomination", "PARACOSME AU", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "nombreParts", 1_000,
                "rcNumero", "123456", "villeGreffe", "CASABLANCA", "formeJuridique", "SARL_AU");
    }

    private static List<Map<String, Object>> deuxAssocies() {
        return List.of(
                Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                        "nombreParts", 600, "presence", "présent"),
                Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "nombreParts", 400, "presence", "présent"));
    }

    private static Map<String, Object> associeUniquePhysique() {
        return Map.of("typePersonne", "PHYSIQUE", "civilite", "M.", "prenom", "Ahmed",
                "nom", "Alaoui", "nationalite", "marocaine", "adresse", "Casablanca",
                "pieceType", "CIN", "pieceNumero", "BE123456", "nombreParts", 1_000);
    }

    /** Résolution mutable (contourne la limite de 10 paires de Map.of). */
    private static Map<String, Object> res(String type) {
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("type", type);
        return r;
    }

    private static Map<String, Object> seance() {
        return Map.of("type", "extraordinaire", "date", "2026-05-15", "heure", "10H00",
                "lieu", "SIEGE SOCIAL", "heureCloture", "12H00",
                "presidentNom", "M. Ahmed Alaoui", "presidentQualite", "gérant");
    }

    private static Map<String, Object> convocation() {
        return Map.of("mode", "lettre recommandée", "date", "2026-04-30");
    }

    private static Map<String, Object> pvSarl(List<String> odj, List<Map<String, Object>> resolutions) {
        return Map.of("formeJuridique", "SARL", "societe", societeSarl(), "seance", seance(),
                "convocation", convocation(), "associes", deuxAssocies(),
                "ordreDuJour", odj, "resolutions", resolutions);
    }

    private static Map<String, Object> pvAu(List<String> odj, List<Map<String, Object>> resolutions) {
        return Map.of("formeJuridique", "SARL_AU", "associeUnique", true, "societe", societeAu(),
                "seance", seance(), "associes", List.of(associeUniquePhysique()),
                "gerantEstAssocie", "oui", "ordreDuJour", odj, "resolutions", resolutions);
    }

    // ── SARL — un cas par famille ────────────────────────────────────────────

    @Test
    void sarl_transfertSiege() throws Exception {
        Map<String, Object> pv = pvSarl(List.of("Transfert du siège social"),
                List.of(Map.of("type", "transfert_siege", "siegeMemePrefecture", "non",
                        "nouveauSiege", "45 BD ZERKTOUNI, RABAT", "dateEffet", "2026-06-01",
                        "articlesModifies", "4")));
        String text = renderSarl("transfert_siege", pv);
        assertTrue(text.contains("transférer le siège social"), "contenu transfert de siège attendu");
    }

    @Test
    void sarl_augmentationNumeraireAvecDps() throws Exception {
        Map<String, Object> aug = res("augmentation_capital_numeraire");
        aug.put("augcapMontantChiffres", 50_000);
        aug.put("augcapNouveauCapital", 150_000);
        aug.put("augcapMode", "création de parts nouvelles");
        aug.put("augcapNbPartsNouvelles", 500);
        aug.put("valeurNominalePart", 100);
        aug.put("augcapPartsDe", 1_001);
        aug.put("augcapPartsA", 1_500);
        aug.put("augcapLiberationMode", "numéraire");
        aug.put("dpsSuppression", "oui");
        aug.put("articlesModifies", "6 et 7");
        aug.put("beneficiairesDps", List.of(
                Map.of("nom", "M. Idrissi", "nbParts", 300),
                Map.of("nom", "Mme Saadi", "nbParts", 200)));
        Map<String, Object> pv = pvSarl(List.of("Augmentation de capital en numéraire"), List.of(aug));
        String text = renderSarl("augmentation_numeraire_dps", pv);
        assertTrue(text.contains("augmenter le capital social"), "contenu augmentation attendu");
        assertTrue(text.contains("supprimer le droit préférentiel"), "suppression DPS attendue");
        assertTrue(text.contains("Idrissi") && text.contains("Saadi"), "bénéficiaires DPS rendus");
    }

    @Test
    void sarl_reductionCapital() throws Exception {
        Map<String, Object> pv = pvSarl(List.of("Réduction de capital"),
                List.of(Map.of("type", "reduction_capital", "redcapMotif", "pertes",
                        "redcapMontant", 30_000, "redcapNouveauCapital", 70_000,
                        "redcapMode", "diminution du nominal", "valeurNominalePart", 100,
                        "nouvelleValeurNominale", 70, "articlesModifies", "6 et 7")));
        String text = renderSarl("reduction_capital", pv);
        assertTrue(text.contains("réduire le capital social"), "contenu réduction attendu");
    }

    @Test
    void sarl_cessionParts() throws Exception {
        Map<String, Object> pv = pvSarl(List.of("Agrément d'une cession de parts"),
                List.of(Map.of("type", "agrement_cession", "cessionNbParts", 100,
                        "cessionPartsDe", 1, "cessionPartsA", 100, "cedantNom", "M. Ahmed Alaoui",
                        "cessionnaireNom", "M. Youssef Tazi", "cessionPrix", 100_000,
                        "cessionnaireTiers", "oui", "articlesModifies", "6 et 7")));
        String text = renderSarl("cession_parts", pv);
        assertTrue(text.contains("agrée expressément la cession"), "contenu agrément cession attendu");
        assertTrue(text.contains("Youssef Tazi"), "cessionnaire rendu");
    }

    @Test
    void sarl_nominationEtRevocationGerant() throws Exception {
        Map<String, Object> pv = pvSarl(
                List.of("Révocation d'un gérant", "Nomination d'un gérant"),
                List.of(
                        Map.of("type", "revocation_gerant", "gerantSortantNom", "M. Ahmed Alaoui"),
                        Map.of("type", "nomination_gerant", "dureeGerance", "durée illimitée",
                                "gerants", List.of(Map.of("civilite", "M.", "prenom", "Karim",
                                        "nom", "Bennani", "nationalite", "marocaine",
                                        "dateNaissance", "1985-03-12", "adresse", "Casablanca",
                                        "pieceType", "CIN", "pieceNumero", "BE998877")))));
        String text = renderSarl("nomination_revocation_gerant", pv);
        assertTrue(text.contains("révoquer"), "contenu révocation attendu");
        assertTrue(text.contains("nomme en qualité de gérant"), "contenu nomination attendu");
        assertTrue(text.contains("Karim") && text.contains("Bennani"), "gérant nommé rendu");
    }

    @Test
    void sarl_affectationResultatEtDividende() throws Exception {
        Map<String, Object> pv = pvSarl(
                List.of("Approbation des comptes", "Affectation du résultat", "Distribution de dividendes"),
                List.of(
                        Map.of("type", "approbation_comptes", "exerciceClosDate", "2025-12-31",
                                "resultatSens", "bénéfice", "resultatMontant", 200_000),
                        Map.of("type", "affectation_resultat", "exerciceClosDate", "2025-12-31",
                                "resultatMontant", 200_000, "affectations", List.of(
                                        Map.of("poste", "Réserve légale", "montant", 10_000),
                                        Map.of("poste", "Report à nouveau", "montant", 90_000))),
                        Map.of("type", "distribution_dividendes", "dividendeTotal", 100_000,
                                "dividendeParPart", 100, "dateMisePaiement", "2026-09-30")));
        String text = renderSarl("affectation_dividende", pv);
        assertTrue(text.contains("affecter le résultat"), "contenu affectation attendu");
        assertTrue(text.contains("Réserve légale"), "poste d'affectation rendu");
        assertTrue(text.contains("dividende"), "contenu dividende attendu");
    }

    @Test
    void sarl_transformation() throws Exception {
        Map<String, Object> pv = pvSarl(List.of("Transformation de la société"),
                List.of(Map.of("type", "transformation", "commissaireTransformationExiste", "oui",
                        "commissaireTransformationNom", "Cabinet Audit Conseil",
                        "transformationForme", "société anonyme",
                        "transformationOrganes", "un conseil d'administration de trois membres")));
        String text = renderSarl("transformation", pv);
        assertTrue(text.contains("transformer la société"), "contenu transformation attendu");
    }

    @Test
    void sarl_multiResolutions() throws Exception {
        Map<String, Object> pv = pvSarl(
                List.of("Changement de dénomination", "Modification de l'objet",
                        "Transfert du siège", "Pouvoirs"),
                List.of(
                        Map.of("type", "modification_denomination",
                                "nouvelleDenomination", "PARACOSME CONSULTING",
                                "dateEffet", "2026-06-01", "articlesModifies", "2"),
                        Map.of("type", "modification_objet", "objetAction", "extension",
                                "objetModification", "le conseil en organisation et la formation",
                                "dateEffet", "2026-06-01", "articlesModifies", "3"),
                        Map.of("type", "transfert_siege", "siegeMemePrefecture", "oui",
                                "nouveauSiege", "45 BD ZERKTOUNI, CASABLANCA",
                                "dateEffet", "2026-06-01", "articlesModifies", "4"),
                        Map.of("type", "pouvoirs_formalites")));
        String text = renderSarl("multi_resolutions", pv);
        assertTrue(text.contains("PARACOSME CONSULTING"), "nouvelle dénomination rendue");
        assertTrue(text.contains("étendre") || text.contains("objet social"), "modification objet rendue");
        assertTrue(text.contains("PREMIÈRE RÉSOLUTION") && text.contains("QUATRIÈME RÉSOLUTION"),
                "numérotation des 4 résolutions attendue");
    }

    // ── SARL AU — familles + multi ────────────────────────────────────────────

    @Test
    void au_transfertSiege() throws Exception {
        Map<String, Object> pv = pvAu(List.of("Transfert du siège social"),
                List.of(Map.of("type", "transfert_siege",
                        "nouveauSiege", "45 BD ZERKTOUNI, RABAT", "dateEffet", "2026-06-01",
                        "articlesModifies", "4")));
        String text = renderAu("transfert_siege", pv);
        assertTrue(text.contains("transférer le siège social"), "contenu transfert AU attendu");
        assertTrue(text.contains("L'ASSOCIÉ UNIQUE"), "en-tête décisions associé unique attendu");
    }

    @Test
    void au_cessionPartsPluripersonnelle() throws Exception {
        Map<String, Object> pv = pvAu(List.of("Cession de parts — passage en SARL pluripersonnelle"),
                List.of(Map.of("type", "cession_parts_pluripersonnelle", "cessionNbParts", 400,
                        "cessionnaireNom", "M. Youssef Tazi", "cessionPrix", 400_000,
                        "articlesModifies", "6, 7 et 8")));
        String text = renderAu("cession_pluripersonnelle", pv);
        assertTrue(text.contains("cesse d'être à associé unique"), "passage pluripersonnel attendu");
    }

    @Test
    void au_multiResolutions() throws Exception {
        Map<String, Object> pv = pvAu(
                List.of("Changement de dénomination", "Augmentation de capital", "Pouvoirs"),
                List.of(
                        Map.of("type", "modification_denomination",
                                "nouvelleDenomination", "PARACOSME AU CONSULTING",
                                "dateEffet", "2026-06-01", "articlesModifies", "2"),
                        Map.of("type", "augmentation_capital_numeraire",
                                "augcapMontantChiffres", 50_000, "augcapNouveauCapital", 150_000,
                                "augcapMode", "création de parts nouvelles",
                                "augcapNbPartsNouvelles", 500, "valeurNominalePart", 100,
                                "augcapPartsDe", 1_001, "augcapPartsA", 1_500,
                                "augcapLiberationMode", "numéraire", "articlesModifies", "6 et 7"),
                        Map.of("type", "pouvoirs_formalites")));
        String text = renderAu("multi_resolutions", pv);
        assertTrue(text.contains("PARACOSME AU CONSULTING"), "nouvelle dénomination AU rendue");
        assertTrue(text.contains("augmenter le capital social"), "augmentation AU rendue");
    }

    // ── Rendering + persistence ────────────────────────────────────────────

    private String renderSarl(String sample, Map<String, Object> pv) throws Exception {
        return render("PV_MODIFICATION_SARL", "SARL_" + sample, pv);
    }

    private String renderAu(String sample, Map<String, Object> pv) throws Exception {
        return render("PV_MODIFICATION_SARL_AU", "SARL_AU_" + sample, pv);
    }

    private String render(String templateCode, String sampleName, Map<String, Object> payload)
            throws Exception {
        Map<String, Object> vars = ModificationDirecteurMapper.pvVars(templateCode, payload);
        DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, vars);
        assertTrue(result.templateFound(), "template introuvable : " + templateCode);
        assertTrue(result.bytes() != null && result.bytes().length > 0, "0 octet : " + templateCode);
        Files.write(OUT.resolve(sampleName + ".docx"), result.bytes());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result.bytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            String text = sb.toString();
            assertNoResidual(text, sampleName);
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
        assertFalse(v.find(), label + " : marqueur variable résiduel " + (v.reset().find() ? v.group() : ""));
        Matcher m = RESIDUAL_MARKER.matcher(text);
        assertFalse(m.find(), label + " : marqueur condition/boucle résiduel");
        assertFalse(text.contains("VALEUR MANQUANTE"), label + " : variable manquante (rouge) dans le rendu");
    }
}

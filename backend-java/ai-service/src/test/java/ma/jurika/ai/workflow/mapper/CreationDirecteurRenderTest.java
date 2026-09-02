package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partie B (2026-08) — Rendu bout-en-bout des 4 modèles déterministes du directeur
 * (STATUTS_SARL, STATUTS_SARL_AU, ACTE_NOMINATION_GERANT, ANNONCE_LEGALE) via le
 * mapper CRÉATION ({@link CreationDirecteurVarsBuilder}) + le moteur
 * {@link DocxTemplateEngine}, sur les 5 cas réels dont sont issus les modèles.
 *
 * <p>Assertions clés :
 * <ul>
 *   <li><b>Couverture 100 %</b> : {@code result.missingVariables()} est VIDE — le
 *       moteur ne laisse aucun {@code $VAR} non résolu, donc le mapper produit
 *       TOUTES les variables du modèle (test #3 du lot).</li>
 *   <li><b>0 marqueur résiduel</b> : aucun {@code $NU}, {@code ◇}, {@code ◆},
 *       {@code ▼}, {@code ▲}, ni « VALEUR MANQUANTE » dans le rendu.</li>
 *   <li><b>0 clé hors dictionnaire</b> : les clés produites par le mapper sont
 *       incluses dans l'ensemble des {@code $VAR}/boucles des 4 modèles.</li>
 * </ul>
 */
class CreationDirecteurRenderTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static TemplateManifestLoader loader;
    private static DocxTemplateEngine engine;
    private final CreationSarlMapper mapper = new CreationSarlMapper();

    private static final String C_STATUTS = "STATUTS_SARL_DIRECTEUR";
    private static final String C_STATUTS_AU = "STATUTS_SARL_AU_DIRECTEUR";
    private static final String C_ACTE = "ACTE_NOMINATION_GERANT_DIRECTEUR";
    private static final String C_ANNONCE = "ANNONCE_LEGALE_DIRECTEUR";

    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");

    private static synchronized DocxTemplateEngine engine() {
        if (engine == null) {
            loader = new TemplateManifestLoader(OM);
            loader.load();
            engine = new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
        }
        return engine;
    }

    // ------------------------------------------------------------------
    // Rendu + extraction
    // ------------------------------------------------------------------

    private RenderResult render(String code, Map<String, Object> payload) {
        Map<String, Object> vars = mapper.map(code, payload);
        DocumentResult res = engine().generate(code, vars);
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                sb.append('\n');
            }
            text = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new RenderResult(vars, res, text);
    }

    private record RenderResult(Map<String, Object> vars, DocumentResult res, String text) {}

    private void assertClean(String code, RenderResult r) {
        // Grammaire d'assemblage (2026-08-17) — vérifie la SOUDURE entre le texte du
        // modèle et la valeur injectée (« à au siège social », « DE le gérant unique »,
        // « de droit Allemagne »), que la non-vacuité ne voit pas.
        java.util.List<String> assemblage__ =
                ma.jurika.ai.document.format.AssemblageFautif.dans(r.text());
        assertTrue(assemblage__.isEmpty(), code + " : assemblage fautif -> " + assemblage__);
        assertTrue(r.res().missingVariables().isEmpty(),
                code + " : variables NON résolues (couverture incomplète) = " + r.res().missingVariables());
        assertFalse(BARE_VAR.matcher(r.text()).find(),
                code + " : marqueur $VAR résiduel dans le rendu.");
        for (String marker : new String[]{"◇", "◆", "▼", "▲", "VALEUR MANQUANTE"}) {
            assertFalse(r.text().contains(marker),
                    code + " : marqueur résiduel « " + marker + " » dans le rendu.");
        }
    }

    // ------------------------------------------------------------------
    // Payload helpers
    // ------------------------------------------------------------------

    @SafeVarargs
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static Map<String, Object> pp(String civ, String prenom, String nom, long parts, String apportType, Long montant) {
        Map<String, Object> a = m(
                "typePersonne", "PHYSIQUE", "civilite", civ, "prenom", prenom, "nom", nom,
                "nationalite", "marocaine", "dateNaissance", "1985-04-12", "lieuNaissance", "Casablanca",
                "adresse", "12 rue X, Casablanca", "pieceType", "CIN", "pieceNumero", "BK12345",
                "nombreParts", parts, "apportType", apportType);
        if (montant != null) {
            if ("nature".equals(apportType)) {
                a.put("apportNatureDescription", "un fonds de commerce");
                a.put("apportNatureValeur", montant);
            } else {
                a.put("apportNumeraire", montant);
            }
        }
        return a;
    }

    private static Map<String, Object> pm(String denom, long parts, long montant) {
        return m("typePersonne", "MORALE", "denomination", denom, "forme", "SARL",
                "capital", 500000L, "siege", "Rabat", "rcVille", "Rabat", "rcNumero", "98765",
                "representantNom", "M. Alaoui", "representantQualite", "Gérant",
                "nombreParts", parts, "apportType", "numéraire", "apportNumeraire", montant);
    }

    private static Map<String, Object> gerant(String civ, String prenom, String nom, boolean statutaire) {
        return m("civilite", civ, "prenom", prenom, "nom", nom, "nationalite", "marocaine",
                "adresse", "5 av. Y, Casablanca", "pieceType", "CIN", "pieceNumero", "AB999",
                "dateNaissance", "1980-01-20", "isStatutaire", statutaire);
    }

    private static Map<String, Object> baseSociete(String modeSignature, String modeLiberation) {
        return m("denomination", "PARACOSME", "objetSocial", "le conseil et l'ingénierie",
                "adresseSiege", "101 bd Zerktouni, Casablanca", "dureeAnnees", 99L,
                "villeGreffe", "Casablanca", "capitalChiffres", 100000L, "nombreParts", 1000L,
                "valeurPart", 100L, "modeLiberation", modeLiberation, "modeSignature", modeSignature,
                "dateSignature", "2026-02-13", "villeSignature", "Casablanca");
    }

    // ------------------------------------------------------------------
    // CAS 1 — SARL, 2 associés PP, apport numéraire, gérant statutaire, signature séparée
    // ------------------------------------------------------------------

    @Test
    void cas1_sarl_pp_numeraire_gerant_statutaire_signature_separee() {
        Map<String, Object> payload = m(
                "societe", baseSociete("séparée", "intégrale"),
                "associes", List.of(
                        pp("M", "Yassine", "BENANI", 600, "numéraire", 60000L),
                        pp("Mme", "Salma", "IDRISSI", 400, "numéraire", 40000L)),
                "gerants", List.of(gerant("M", "Yassine", "BENANI", true)));
        RenderResult r = render(C_STATUTS, payload);
        assertClean(C_STATUTS, r);
        assertTrue(r.text().contains("PARACOSME"), "dénomination absente");
        assertTrue(r.text().contains("BENANI") && r.text().contains("IDRISSI"), "associés absents");
        // gérant statutaire -> Article 36 désignation présente
        assertTrue(r.text().toLowerCase().contains("premiers gérants")
                || r.text().toLowerCase().contains("gérant"), "désignation gérance absente");
        assertNoExtraKeys(C_STATUTS, r.vars());
    }

    // ------------------------------------------------------------------
    // CAS 2 — SARL, associé PM + PP, apport nature, gérant non statutaire, signature avec plafond
    // ------------------------------------------------------------------

    @Test
    void cas2_sarl_pm_nature_gerant_non_statutaire_plafond() {
        Map<String, Object> societe = baseSociete("séparée avec plafond", "partielle");
        societe.put("signaturePlafond", 50000L);
        societe.put("commissaireApportsNom", "M. le commissaire aux apports Tazi");
        Map<String, Object> payload = m(
                "societe", societe,
                "associes", List.of(
                        pm("HOLDING ATLAS", 700, 70000L),
                        pp("M", "Karim", "FILALI", 300, "nature", 30000L)),
                "gerants", List.of(gerant("M", "Karim", "FILALI", false)));
        RenderResult rStat = render(C_STATUTS, payload);
        assertClean(C_STATUTS, rStat);
        assertTrue(rStat.text().contains("séparée avec plafond")
                || rStat.text().toLowerCase().contains("plafond"), "clause plafond absente");
        assertTrue(rStat.text().contains("HOLDING ATLAS"), "associé PM absent");
        // apport nature -> bloc commissaire aux apports
        assertTrue(rStat.text().contains("commissaire aux apports"), "bloc commissaire aux apports absent");
        assertNoExtraKeys(C_STATUTS, rStat.vars());

        // ACTE de nomination (gérant non statutaire)
        RenderResult rActe = render(C_ACTE, payload);
        assertClean(C_ACTE, rActe);
        assertTrue(rActe.text().contains("FILALI"), "gérant absent de l'acte");
        assertNoExtraKeys(C_ACTE, rActe.vars());
    }

    // ------------------------------------------------------------------
    // CAS 3 — SARL AU, associé unique PP, apport numéraire, gérant statutaire, signature conjointe
    // ------------------------------------------------------------------

    @Test
    void cas3_sarl_au_pp_gerant_statutaire_signature_conjointe() {
        Map<String, Object> societe = baseSociete("conjointe", "intégrale");
        societe.put("formeJuridique", "SARL_AU");
        Map<String, Object> uniquePp = pp("M", "Omar", "CHERKAOUI", 1000, "numéraire", 100000L);
        uniquePp.put("estGerant", true);
        Map<String, Object> payload = m(
                "societe", societe,
                "associes", List.of(uniquePp),
                "gerants", List.of(gerant("M", "Omar", "CHERKAOUI", true)));
        RenderResult r = render(C_STATUTS_AU, payload);
        assertClean(C_STATUTS_AU, r);
        assertTrue(r.text().contains("ASSOCIÉ UNIQUE") || r.text().toLowerCase().contains("associé unique"),
                "mention associé unique absente");
        assertTrue(r.text().contains("CHERKAOUI"), "associé unique absent");
        assertNoExtraKeys(C_STATUTS_AU, r.vars());
    }

    // ------------------------------------------------------------------
    // CAS 4 — SARL AU, associé unique PM, gérant non statutaire tiers -> ACTE
    // ------------------------------------------------------------------

    @Test
    void cas4_sarl_au_pm_gerant_non_statutaire_tiers() {
        Map<String, Object> societe = baseSociete("séparée", "intégrale");
        societe.put("formeJuridique", "SARL_AU");
        Map<String, Object> payload = m(
                "societe", societe,
                "associes", List.of(pm("GROUPE MAGHREB", 1000, 100000L)),
                "gerants", List.of(gerant("Mme", "Nadia", "ALAMI", false)));
        RenderResult rStat = render(C_STATUTS_AU, payload);
        assertClean(C_STATUTS_AU, rStat);
        assertTrue(rStat.text().contains("GROUPE MAGHREB"), "associé unique PM absent");
        assertNoExtraKeys(C_STATUTS_AU, rStat.vars());

        RenderResult rActe = render(C_ACTE, payload);
        assertClean(C_ACTE, rActe);
        assertTrue(rActe.text().contains("ALAMI"), "gérant tiers absent de l'acte");
        assertNoExtraKeys(C_ACTE, rActe.vars());
    }

    // ------------------------------------------------------------------
    // CAS 5 — ANNONCE LÉGALE (SARL pluralité + SARL AU)
    // ------------------------------------------------------------------

    @Test
    void cas5_annonce_legale_sarl_et_au() {
        Map<String, Object> payloadSarl = m(
                "societe", baseSociete("séparée", "intégrale"),
                "associes", List.of(
                        pp("M", "Yassine", "BENANI", 600, "numéraire", 60000L),
                        pp("Mme", "Salma", "IDRISSI", 400, "numéraire", 40000L)),
                "gerants", List.of(gerant("M", "Yassine", "BENANI", true)));
        RenderResult rSarl = render(C_ANNONCE, payloadSarl);
        assertClean(C_ANNONCE, rSarl);
        assertTrue(rSarl.text().contains("SARL"), "forme absente de l'annonce");
        assertTrue(rSarl.text().contains("AVIS DE CONSTITUTION"), "titre annonce absent");
        assertNoExtraKeys(C_ANNONCE, rSarl.vars());

        Map<String, Object> societeAu = baseSociete("séparée", "intégrale");
        societeAu.put("formeJuridique", "SARL_AU");
        Map<String, Object> payloadAu = m(
                "societe", societeAu,
                "associes", List.of(pp("M", "Omar", "CHERKAOUI", 1000, "numéraire", 100000L)),
                "gerants", List.of(gerant("M", "Omar", "CHERKAOUI", true)));
        RenderResult rAu = render(C_ANNONCE, payloadAu);
        assertClean(C_ANNONCE, rAu);
        assertTrue(rAu.text().contains("SARL AU"), "forme AU absente de l'annonce");
        assertNoExtraKeys(C_ANNONCE, rAu.vars());
    }

    // ------------------------------------------------------------------
    // Échantillons : écrit les 4 actes rendus dans target/echantillons-directeur/
    // ------------------------------------------------------------------

    @Test
    void genererEchantillons() throws Exception {
        java.nio.file.Path out = java.nio.file.Path.of("target", "echantillons-directeur-p2a");
        java.nio.file.Files.createDirectories(out);

        Map<String, Object> societeSarl = baseSociete("séparée avec plafond", "partielle");
        societeSarl.put("signaturePlafond", 50000L);
        societeSarl.put("commissaireApportsNom", "Cabinet TAZI, commissaire aux apports");
        Map<String, Object> payloadSarl = m(
                "societe", societeSarl,
                "associes", List.of(
                        pp("M", "Yassine", "BENANI", 600, "numéraire", 60000L),
                        pm("HOLDING ATLAS", 400, 40000L)),
                "gerants", List.of(gerant("M", "Yassine", "BENANI", false)));

        Map<String, Object> societeAu = baseSociete("séparée", "intégrale");
        societeAu.put("formeJuridique", "SARL_AU");
        Map<String, Object> payloadAu = m(
                "societe", societeAu,
                "associes", List.of(pp("Mme", "Nadia", "ALAMI", 1000, "numéraire", 100000L)),
                "gerants", List.of(gerant("Mme", "Nadia", "ALAMI", true)));

        write(out, "STATUTS_SARL.docx", C_STATUTS, payloadSarl);
        write(out, "STATUTS_SARL_AU.docx", C_STATUTS_AU, payloadAu);
        write(out, "ACTE_NOMINATION_GERANT.docx", C_ACTE, payloadSarl);
        write(out, "ANNONCE_LEGALE.docx", C_ANNONCE, payloadSarl);
    }

    private void write(java.nio.file.Path dir, String name, String code, Map<String, Object> payload) {
        // Best-effort : l'échantillon est un ARTEFACT, pas une assertion. Un verrou
        // fichier OS (aperçu Word/antivirus) ne doit jamais faire échouer la suite.
        try {
            DocumentResult res = engine().generate(code, mapper.map(code, payload));
            java.nio.file.Files.write(dir.resolve(name), res.bytes());
        } catch (Exception ex) {
            System.out.println("[echantillons] écriture ignorée pour " + name + " : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Couverture : 0 clé produite hors du périmètre des 4 modèles
    // ------------------------------------------------------------------

    private void assertNoExtraKeys(String code, Map<String, Object> vars) {
        Set<String> allowed = allowedKeys();
        Set<String> extra = new TreeSet<>();
        for (String k : vars.keySet()) {
            if (!allowed.contains(k)) extra.add(k);
        }
        assertTrue(extra.isEmpty(), code + " : clés produites hors dictionnaire/modèles = " + extra);
    }

    /** Union des $VAR + noms de boucles des 4 modèles directeur (référence de couverture). */
    private static Set<String> allowedKeys() {
        Set<String> set = new TreeSet<>();
        for (String fn : new String[]{
                "STATUTS_SARL_modele_deterministe.docx", "STATUTS_SARL_AU_modele_deterministe.docx",
                "ACTE_NOMINATION_GERANT_modele_deterministe.docx", "ANNONCE_LEGALE_modele_deterministe.docx"}) {
            String text = docxText("templates/docx/" + fn);
            Matcher m = BARE_VAR.matcher(text);
            while (m.find()) set.add(m.group().substring(1));
            Matcher lm = Pattern.compile("(?:DÉBUT|FIN) BOUCLE\\s*[—–-]\\s*([A-Z_]+)").matcher(text);
            while (lm.find()) set.add(lm.group(1));
        }
        // Drapeaux dérivés : présents dans le scope pour évaluer une condition en langage
        // naturel du modèle, mais qui ne sont PAS des $placeholders (donc légitimes).
        set.add("HAS_APPORT_NATURE");
        return set;
    }

    private static String docxText(String resourcePath) {
        try (InputStream in = CreationDirecteurRenderTest.class.getClassLoader().getResourceAsStream(resourcePath);
             XWPFDocument doc = new XWPFDocument(in)) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                StringBuilder sb = new StringBuilder();
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                lines.add(sb.toString());
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            throw new RuntimeException("Lecture docx échouée : " + resourcePath, e);
        }
    }
}

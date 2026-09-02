package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase E2 (2026-08-09) — Preuve de rendu de la <b>refonte des statuts (v2)</b> par la
 * voie DIRECTEUR ({@code STATUTS_REFONDUS_SARL} / {@code _SARL_AU}), miroir de
 * {@link ModificationRenderTest} / {@code CreationDirecteurRenderTest}.
 *
 * <p>Chaîne identique à la production : {@link ModificationMapper#map} (qui route la
 * refonte vers {@link RefonteStatutsVarsBuilder} → {@link CreationDirecteurVarsBuilder})
 * puis rendu réel via {@link DocxTemplateEngine}.
 *
 * <p>Prouve que « overlay gagne » : à partir d'un état structuré COURANT (fiche), un
 * overlay de modifications (nouvelle dénomination, transfert de siège, augmentation de
 * capital) ressort dans le document v2, tandis que les valeurs NON modifiées (objet,
 * valeur nominale, associés) sont conservées depuis la fiche. 0 marqueur résiduel.
 *
 * <p>Persiste les échantillons dans {@code target/echantillons-refonte/} pour contrôle
 * visuel (artefacts, best-effort).
 */
class RefonteStatutsRenderTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");
    private static final Path OUT_DIR = Path.of("target", "echantillons-refonte");

    private final ModificationMapper mapper = new ModificationMapper();
    private final DocxTemplateEngine engine = buildEngine();

    private static DocxTemplateEngine buildEngine() {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        return new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
    }

    // ------------------------------------------------------------------
    // État structuré courant (fiche) + payload de modification
    // ------------------------------------------------------------------

    /** Fiche courante SARL réaliste (RC/ICE/IF/ville greffe renseignés). */
    private static Map<String, Object> fichePluri() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("denomination", "PARACOSME SARL");
        f.put("formeJuridique", "SARL");
        f.put("objetSocial", "Le conseil juridique et fiscal aux entreprises");
        f.put("adresseSiege", "12 RUE X, RABAT");
        f.put("ville", "Rabat");
        f.put("rcNumero", "123456");
        f.put("ice", "001234567000089");
        f.put("identifiantFiscal", "40123456");
        f.put("capitalSocial", 100000L);
        f.put("nombreParts", 1000L);
        f.put("valeurNominale", 100L);
        f.put("dureeAnnees", 99L);
        f.put("gerants", List.of(Map.of(
                "nom", "BENNANI", "prenom", "Karim", "cinNumero", "BK12345",
                "nationalite", "marocaine", "isStatutaire", Boolean.TRUE)));
        f.put("associes", List.of(
                Map.of("typePersonne", "PHYSIQUE", "nom", "BENNANI", "prenom", "Karim",
                        "cin", "BK12345", "nombreParts", 600L, "apportType", "numéraire",
                        "apportNumeraire", 60000L),
                Map.of("typePersonne", "PHYSIQUE", "nom", "IDRISSI", "prenom", "Salma",
                        "cin", "SA54321", "nombreParts", 400L, "apportType", "numéraire",
                        "apportNumeraire", 40000L)));
        return f;
    }

    /** Fiche courante SARL AU (associé unique ; gérant statutaire BENNANI conservé). */
    private static Map<String, Object> ficheUnique() {
        Map<String, Object> f = fichePluri();
        f.put("denomination", "PARACOSME SARL AU");
        f.put("formeJuridique", "SARL_AU");
        f.put("associes", List.of(
                Map.of("typePersonne", "PHYSIQUE", "nom", "CHERKAOUI", "prenom", "Omar",
                        "cin", "OC99999", "nombreParts", 1000L, "apportType", "numéraire",
                        "apportNumeraire", 100000L)));
        // Gérant BENNANI conservé (assertion « valeur non modifiée » partagée).
        return f;
    }

    /** Overlay de modifications : dénomination + transfert de siège + augmentation de capital. */
    private static List<Map<String, Object>> modifications() {
        return List.of(
                Map.of("typeId", "CHANGEMENT_DENOMINATION",
                        "details", Map.of("nouvelleDenomination", "PARACOSME HOLDING")),
                Map.of("typeId", "TRANSFERT_SIEGE",
                        "details", Map.of("nouvelleAdresse", "45 BOULEVARD ANFA",
                                "nouvelleVille", "CASABLANCA")),
                Map.of("typeId", "AUGMENTATION_CAPITAL",
                        "details", Map.of("nouveauCapital", 300000L, "nouvellesParts", 2000L)));
    }

    private static Map<String, Object> payload(Map<String, Object> fiche) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ficheStructuree", fiche);
        p.put("societe", Map.of("denomination", fiche.get("denomination"),
                "formeJuridique", fiche.get("formeJuridique")));
        p.put("modifications", modifications());
        return p;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Refonte SARL (v2) : valeurs modifiées ressortent, valeurs conservées, 0 résiduel")
    void refonte_sarl_render() throws Exception {
        assertRefonte("STATUTS_REFONDUS_SARL", payload(fichePluri()));
    }

    @Test
    @DisplayName("Refonte SARL AU (v2) : associé unique, valeurs modifiées, 0 résiduel")
    void refonte_sarl_au_render() throws Exception {
        String text = assertRefonte("STATUTS_REFONDUS_SARL_AU", payload(ficheUnique()));
        // Marque « associé unique » propre au modèle AU.
        assertThat(text.toLowerCase(Locale.ROOT)).contains("associé unique");
    }

    /** Rend, persiste l'échantillon, applique toutes les assertions communes ; renvoie le texte. */
    private String assertRefonte(String code, Map<String, Object> payload) throws Exception {
        Map<String, Object> vars = mapper.map(code, payload);
        DocumentResult res = engine.generate(code, vars);
        assertThat(res.templateFound()).as("%s : template introuvable", code).isTrue();

        // Échantillon pour contrôle visuel (best-effort — verrou fichier ne casse pas le test).
        try {
            Files.createDirectories(OUT_DIR);
            Files.write(OUT_DIR.resolve(code + ".docx"), res.bytes());
        } catch (Exception ex) {
            System.out.println("[echantillons-refonte] écriture ignorée pour " + code + " : " + ex.getMessage());
        }

        String text = extractText(res.bytes());
        String normalizedDigits = text.replaceAll("[\\s\\u00A0\\u202F]", "");

        // (1) VALEURS MODIFIÉES présentes (overlay gagne).
        assertThat(vars.get("DENOMINATION")).isEqualTo("PARACOSME HOLDING");
        assertThat(text).as("%s : nouvelle dénomination absente", code).contains("PARACOSME HOLDING");
        assertThat(text).as("%s : ancienne dénomination encore présente", code)
                .doesNotContain("PARACOSME SARL");
        assertThat(text).as("%s : nouvelle ville (transfert siège) absente", code).contains("CASABLANCA");
        // Nouveau capital en chiffres (séparateur de milliers insécable normalisé) ET en lettres.
        assertThat(normalizedDigits).as("%s : nouveau capital (chiffres) absent", code).contains("300000");
        String capLettres = String.valueOf(vars.get("CAPITAL_LETTRES"));
        assertThat(capLettres.toLowerCase(Locale.ROOT)).contains("mille");
        assertThat(text).as("%s : capital en lettres absent du rendu", code).contains(capLettres);

        // (2) VALEURS NON MODIFIÉES conservées depuis la fiche.
        assertThat(text).as("%s : objet social (non modifié) absent", code)
                .contains("conseil juridique et fiscal");
        assertThat(text).as("%s : valeur nominale (non modifiée) absente", code).contains("100");
        assertThat(text).as("%s : gérant (non modifié) absent", code).contains("BENNANI");

        // (3) 0 MARQUEUR RÉSIDUEL.
        assertThat(BARE_VAR.matcher(text).find()).as("%s : marqueur $VAR résiduel", code).isFalse();
        assertThat(text).as("%s : marqueur {{ }} résiduel", code).doesNotContain("{{").doesNotContain("}}");
        for (String marker : new String[]{"◇", "◆", "▼", "▲", "VALEUR MANQUANTE"}) {
            assertThat(text).as("%s : marqueur résiduel « %s »", code, marker).doesNotContain(marker);
        }
        return text;
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static String extractText(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                sb.append('\n');
            }
            doc.getTables().forEach(t -> t.getRows().forEach(row -> row.getTableCells().forEach(c ->
                    c.getParagraphs().forEach(p -> {
                        for (XWPFRun r : p.getRuns()) {
                            String tx = r.text();
                            if (tx != null) sb.append(tx);
                        }
                        sb.append('\n');
                    }))));
            return sb.toString();
        }
    }
}

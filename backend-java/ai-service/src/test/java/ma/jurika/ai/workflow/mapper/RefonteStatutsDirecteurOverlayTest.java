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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 (2026-08-10) — Preuve que la refonte des statuts v2 applique l'overlay des
 * NOUVELLES valeurs saisies par la <b>voie directeur</b> (clé {@code resolutions} :
 * {@code type} snake_case + champs {@code RES_SPECS} + {@code nouvelAssocie}), et pas
 * seulement la voie LEGACY ({@code modifications} UPPERCASE, couverte par
 * {@link RefonteStatutsRenderTest}).
 *
 * <p>Vérifie notamment l'INSERTION d'un nouvel associé (identité OCR) à l'article
 * « associés » — augmentation de capital ET cession — avec cohérence du total des parts.
 * Chaîne de rendu réelle ({@link ModificationMapper#map} → {@link DocxTemplateEngine}),
 * 0 marqueur résiduel.
 */
class RefonteStatutsDirecteurOverlayTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");

    private final ModificationMapper mapper = new ModificationMapper();
    private final DocxTemplateEngine engine = buildEngine();

    private static DocxTemplateEngine buildEngine() {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        return new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
    }

    /** Fiche SARL pluri courante : 2 associés (BENNANI 600 / IDRISSI 400), 100 000 MAD. */
    private static Map<String, Object> fiche() {
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

    @Test
    @DisplayName("Refonte directeur : dénomination + siège + augmentation avec NOUVEL associé (OCR)")
    void refonte_directeur_augmentation_nouvel_associe() throws Exception {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ficheStructuree", ma.jurika.ai.workflow.FichesCompletes.completer(fiche()));
        p.put("resolutions", List.of(
                Map.of("type", "modification_denomination", "nouvelleDenomination", "PARACOSME HOLDING"),
                Map.of("type", "transfert_siege", "nouveauSiege", "45 BOULEVARD ANFA, CASABLANCA"),
                new LinkedHashMap<>(Map.of(
                        "type", "augmentation_capital_numeraire",
                        "augcapNouveauCapital", 300000L,
                        "augcapNbPartsNouvelles", 2000L,
                        "valeurNominalePart", 100L,
                        "nouvelAssocie", ma.jurika.ai.workflow.FichesCompletes.personne(Map.of(
                                "nom", "TAZI", "prenom", "Nadia", "cin", "T998877",
                                "nationalite", "marocaine", "adresse", "Casablanca"))))));

        // Lot L3 : date de l'assemblee et lieu de signature (« Fait a ..., le ... »).
        p.put("seance", Map.of("date", "2026-06-15"));
        p.put("convocation", Map.of("lieuSignature", "Casablanca"));
        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", p);
        String text = render("STATUTS_REFONDUS_SARL", vars);
        String digits = text.replaceAll("[\\s\\u00A0\\u202F]", "");

        // Overlay gagne : nouvelle dénomination + siège + capital.
        assertThat(vars.get("DENOMINATION")).isEqualTo("PARACOSME HOLDING");
        assertThat(text).contains("PARACOSME HOLDING").doesNotContain("PARACOSME SARL");
        assertThat(text).contains("CASABLANCA");
        assertThat(digits).contains("300000");
        // Nouvel associé inséré à l'article « associés ».
        assertThat(text).as("nouvel associé absent du statut refondu").contains("TAZI");
        assertThat(text).contains("Nadia");
        // Valeurs non modifiées conservées.
        assertThat(text).contains("conseil juridique et fiscal").contains("BENNANI");
        assertNoResidual(text);
    }

    @Test
    @DisplayName("Refonte directeur : cession — cédant débité, cessionnaire (OCR) inséré, total cohérent")
    void refonte_directeur_cession_nouvel_associe() throws Exception {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ficheStructuree", ma.jurika.ai.workflow.FichesCompletes.completer(fiche()));
        p.put("resolutions", List.of(new LinkedHashMap<>(Map.of(
                "type", "agrement_cession",
                "cedantNom", "IDRISSI",
                "cessionnaireNom", "TAZI",
                "cessionNbParts", 400L,
                "nouvelAssocie", ma.jurika.ai.workflow.FichesCompletes.personne(Map.of(
                        "nom", "TAZI", "prenom", "Nadia", "cin", "T998877",
                        "nationalite", "marocaine"))))));

        // Lot L3 : date de l'assemblee et lieu de signature (« Fait a ..., le ... »).
        p.put("seance", Map.of("date", "2026-06-15"));
        p.put("convocation", Map.of("lieuSignature", "Casablanca"));
        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", p);
        String text = render("STATUTS_REFONDUS_SARL", vars);
        String digits = text.replaceAll("[\\s\\u00A0\\u202F]", "");

        // Cédant intégralement cédé -> retiré ; cessionnaire présent.
        assertThat(text).as("cessionnaire OCR absent").contains("TAZI");
        assertThat(text).as("cédant totalement cédé encore présent").doesNotContain("Salma");
        // Capital inchangé (cession = pas de parts créées).
        assertThat(digits).contains("100000");
        assertThat(text).contains("BENNANI");
        assertNoResidual(text);
    }

    // ------------------------------------------------------------------
    private String render(String code, Map<String, Object> vars) throws Exception {
        DocumentResult res = engine.generate(code, vars);
        assertThat(res.templateFound()).as("%s : template introuvable", code).isTrue();
        return extractText(res.bytes());
    }

    private static void assertNoResidual(String text) {
        assertThat(BARE_VAR.matcher(text).find()).as("marqueur $VAR résiduel").isFalse();
        assertThat(text).doesNotContain("{{").doesNotContain("}}");
        for (String marker : new String[]{"◇", "◆", "▼", "▲", "VALEUR MANQUANTE"}) {
            assertThat(text).as("marqueur résiduel « %s »", marker).doesNotContain(marker);
        }
    }

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

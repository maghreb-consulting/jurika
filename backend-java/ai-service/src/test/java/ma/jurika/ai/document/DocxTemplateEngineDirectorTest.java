package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la Partie A (2026-08 — moteur aligne sur les modeles directeur).
 *
 * <p>Chaque fixture .docx est construite a la volee via POI (une ligne = un paragraphe,
 * chaque marqueur sur sa propre ligne, comme dans les modeles directeur), passee au
 * moteur via {@link DocxTemplateEngine#render(byte[], String, Map)} (package-private),
 * puis le texte rendu est re-extrait et compare.
 *
 * <p>Couvre : variables nues {@code $NU}, boucles {@code ▼…▲}, conditions
 * {@code SI/SINON SI/SINON/FIN SI} (egalite, existence, ET, parenthese-SINON,
 * imbrication boucle↔condition, insensibilite casse/accents) ET la non-regression
 * des mecanismes legacy ({@code ${}}, {@code {{}}}, {@code ▶}, {@code ◇FLAG}).
 */
class DocxTemplateEngineDirectorTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    // ------------------------------------------------------------------
    // Helpers fixtures
    // ------------------------------------------------------------------

    private static byte[] docx(String... lines) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : lines) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Extrait le texte rendu : un paragraphe par ligne, marqueurs residuels inclus. */
    private String render(byte[] template, Map<String, Object> vars) {
        DocxTemplateEngine.DocumentResult res = engine.render(template, "TEST.docx", vars);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                StringBuilder sb = new StringBuilder();
                for (XWPFRun run : p.getRuns()) {
                    String t = run.text();
                    if (t != null) sb.append(t);
                }
                lines.add(sb.toString());
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> item(Object... kv) {
        return vars(kv);
    }

    // ------------------------------------------------------------------
    // A.1 — Variables nues $NU
    // ------------------------------------------------------------------

    @Test
    void bareVariableIsSubstituted() {
        byte[] t = docx("Denomination : $DENOMINATION au capital de $CAPITAL_CHIFFRES dirhams.");
        String out = render(t, vars("DENOMINATION", "ACME", "CAPITAL_CHIFFRES", "100 000"));
        assertEquals("Denomination : ACME au capital de 100 000 dirhams.", out);
    }

    @Test
    void bareVariableDoesNotSwallowDollarBrace() {
        // ${VAR} et $VAR coexistent sur la meme ligne, pas de double substitution.
        // (les variables nues exigent >= 3 caracteres : cf. [A-Z][A-Z0-9_]{2,})
        byte[] t = docx("${DENOM} et $CAPITAL");
        String out = render(t, vars("DENOM", "un", "CAPITAL", "deux"));
        assertEquals("un et deux", out);
    }

    // ------------------------------------------------------------------
    // Non-regression legacy ${} / {{}}
    // ------------------------------------------------------------------

    @Test
    void legacyPlaceholdersStillWork() {
        byte[] t = docx("${SIEGE_SOCIAL} - {{denomination}}");
        String out = render(t, vars("SIEGE_SOCIAL", "Casablanca", "denomination", "ACME"));
        assertEquals("Casablanca - ACME", out);
    }

    // ------------------------------------------------------------------
    // A.3 — Conditions egalite SI / SINON
    // ------------------------------------------------------------------

    @Test
    void ifElseValueEquality() {
        byte[] t = docx(
                "◇ SI : $ASSOCIE_UNIQUE = « oui »",
                "SARL AU",
                "◇ SINON",
                "SARL",
                "◆ FIN SI");
        assertEquals("SARL AU", render(t, vars("ASSOCIE_UNIQUE", "oui")));
        assertEquals("SARL", render(t, vars("ASSOCIE_UNIQUE", "non")));
    }

    @Test
    void elseIfChainSelectsFirstTrueBranch() {
        byte[] t = docx(
                "◇ SI : $APPORT_TYPE = « numéraire »",
                "NUM",
                "◇ SINON SI : $APPORT_TYPE = « nature »",
                "NAT",
                "◇ SINON (: $APPORT_TYPE = « industrie »)",
                "IND",
                "◆ FIN SI");
        assertEquals("NUM", render(t, vars("APPORT_TYPE", "numéraire")));
        assertEquals("NAT", render(t, vars("APPORT_TYPE", "nature")));
        assertEquals("IND", render(t, vars("APPORT_TYPE", "industrie")));
    }

    @Test
    void comparisonIsCaseAndAccentInsensitive() {
        byte[] t = docx(
                "◇ SI : $MODE_SIGNATURE = « séparée avec plafond »",
                "PLAFOND",
                "◇ SINON",
                "AUTRE",
                "◆ FIN SI");
        // valeur fournie sans accents + casse differente
        assertEquals("PLAFOND", render(t, vars("MODE_SIGNATURE", "SEPAREE AVEC PLAFOND")));
    }

    // ------------------------------------------------------------------
    // A.3 — Existence
    // ------------------------------------------------------------------

    @Test
    void existencePredicate() {
        byte[] t = docx(
                "◇ SI : $LIMITATION_POUVOIRS existe",
                "Limite : $LIMITATION_POUVOIRS",
                "◆ FIN SI",
                "FIN");
        assertEquals("Limite : cautions\nFIN", render(t, vars("LIMITATION_POUVOIRS", "cautions")));
        assertEquals("FIN", render(t, vars())); // absente -> bloc supprime
        assertEquals("FIN", render(t, vars("LIMITATION_POUVOIRS", ""))); // vide = falsy
    }

    // ------------------------------------------------------------------
    // A.3 — Condition composee ET
    // ------------------------------------------------------------------

    @Test
    void compoundAndCondition() {
        byte[] t = docx(
                "◇ SI : $GERANT_MODE_DESIGNATION = « statutaire » ET $ASSOCIE_EST_GERANT = « non »",
                "ACCEPTATION",
                "◆ FIN SI",
                "Z");
        assertEquals("ACCEPTATION\nZ",
                render(t, vars("GERANT_MODE_DESIGNATION", "statutaire", "ASSOCIE_EST_GERANT", "non")));
        assertEquals("Z",
                render(t, vars("GERANT_MODE_DESIGNATION", "statutaire", "ASSOCIE_EST_GERANT", "oui")));
        assertEquals("Z",
                render(t, vars("GERANT_MODE_DESIGNATION", "non statutaire", "ASSOCIE_EST_GERANT", "non")));
    }

    // ------------------------------------------------------------------
    // A.2 — Boucles ▼…▲
    // ------------------------------------------------------------------

    @Test
    void loopExpandsPerItem() {
        byte[] t = docx(
                "Liste :",
                "▼ DÉBUT BOUCLE — ASSOCIES",
                "$ASSOCIE_NOM : $ASSOCIE_NOMBRE_PARTS parts",
                "▲ FIN BOUCLE — ASSOCIES",
                "Fin.");
        List<Map<String, Object>> assoc = List.of(
                item("ASSOCIE_NOM", "Alice", "ASSOCIE_NOMBRE_PARTS", "60"),
                item("ASSOCIE_NOM", "Bob", "ASSOCIE_NOMBRE_PARTS", "40"));
        String out = render(t, vars("ASSOCIES", assoc));
        assertEquals("Liste :\nAlice : 60 parts\nBob : 40 parts\nFin.", out);
    }

    @Test
    void emptyLoopRemovesBody() {
        byte[] t = docx(
                "A",
                "▼ DÉBUT BOUCLE — ASSOCIES",
                "$ASSOCIE_NOM",
                "▲ FIN BOUCLE — ASSOCIES",
                "B");
        String out = render(t, vars("ASSOCIES", List.of()));
        assertEquals("A\nB", out);
    }

    // ------------------------------------------------------------------
    // Imbrication : condition DANS une boucle (scope par item)
    // ------------------------------------------------------------------

    @Test
    void conditionInsideLoopEvaluatedPerItem() {
        byte[] t = docx(
                "▼ DÉBUT BOUCLE — ASSOCIES",
                "◇ SI : $ASSOCIE_TYPE = « personne physique »",
                "PP: $ASSOCIE_NOM",
                "◇ SINON (: $ASSOCIE_TYPE = « personne morale »)",
                "PM: $ASSOCIE_DENOMINATION",
                "◆ FIN SI",
                "▲ FIN BOUCLE — ASSOCIES");
        List<Map<String, Object>> assoc = List.of(
                item("ASSOCIE_TYPE", "personne physique", "ASSOCIE_NOM", "Alice"),
                item("ASSOCIE_TYPE", "personne morale", "ASSOCIE_DENOMINATION", "ACME SARL"));
        String out = render(t, vars("ASSOCIES", assoc));
        assertEquals("PP: Alice\nPM: ACME SARL", out);
    }

    // ------------------------------------------------------------------
    // Imbrication : boucle DANS une condition
    // ------------------------------------------------------------------

    @Test
    void loopInsideConditionKeptOrRemoved() {
        byte[] t = docx(
                "◇ SI : $GERANT_MODE_DESIGNATION = « statutaire »",
                "Gérants nommés :",
                "▼ DÉBUT BOUCLE — GERANTS",
                "$GERANT_NOM",
                "▲ FIN BOUCLE — GERANTS",
                "◆ FIN SI",
                "SUITE");
        List<Map<String, Object>> ger = List.of(item("GERANT_NOM", "Karim"), item("GERANT_NOM", "Sara"));
        // statutaire -> bloc + boucle conserves et expanses
        assertEquals("Gérants nommés :\nKarim\nSara\nSUITE",
                render(t, vars("GERANT_MODE_DESIGNATION", "statutaire", "GERANTS", ger)));
        // non statutaire -> tout le bloc (y compris la boucle) supprime, pas d'expansion
        assertEquals("SUITE",
                render(t, vars("GERANT_MODE_DESIGNATION", "non statutaire", "GERANTS", ger)));
    }

    // ------------------------------------------------------------------
    // Imbrication profonde : SI > boucle > SI (per item)
    // ------------------------------------------------------------------

    @Test
    void conditionLoopConditionDeepNesting() {
        byte[] t = docx(
                "◇ SI : $GERANT_MODE_DESIGNATION = « statutaire »",
                "Acceptation :",
                "▼ DÉBUT BOUCLE — ASSOCIES",
                "◇ SI : $ASSOCIE_EST_GERANT = « non »",
                "$ASSOCIE_NOM",
                "◆ FIN SI",
                "▲ FIN BOUCLE — ASSOCIES",
                "◆ FIN SI",
                "END");
        List<Map<String, Object>> assoc = List.of(
                item("ASSOCIE_NOM", "Alice", "ASSOCIE_EST_GERANT", "non"),
                item("ASSOCIE_NOM", "Bob", "ASSOCIE_EST_GERANT", "oui"),
                item("ASSOCIE_NOM", "Cléo", "ASSOCIE_EST_GERANT", "non"));
        String out = render(t, vars("GERANT_MODE_DESIGNATION", "statutaire", "ASSOCIES", assoc));
        assertEquals("Acceptation :\nAlice\nCléo\nEND", out);
    }

    // ------------------------------------------------------------------
    // Non-regression legacy ▶ (blocs repetables) et ◇FLAG (drapeau truthy)
    // ------------------------------------------------------------------

    @Test
    void legacyRepeatableBlockStillWorks() {
        byte[] t = docx("▶ ITEMS ${VALUE}");
        List<Map<String, Object>> items = List.of(item("VALUE", "x"), item("VALUE", "y"));
        String out = render(t, vars("ITEMS", items));
        assertEquals("x\ny", out);
    }

    @Test
    void legacyFlagBlockStillWorks() {
        byte[] t = docx("◇ MONTRER Texte visible ◆ MONTRER", "APRES");
        assertEquals("Texte visible\nAPRES", render(t, vars("MONTRER", "oui")));
        assertEquals("APRES", render(t, vars("MONTRER", "non")));
    }
}

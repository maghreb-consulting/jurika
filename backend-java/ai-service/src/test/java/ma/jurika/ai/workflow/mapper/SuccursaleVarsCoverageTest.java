package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D — COUVERTURE des variables des 6 modèles Succursale. Extrait tous les {@code $VAR}
 * de chaque modèle intégré et vérifie que le mapper correspondant les produit <b>tous</b>
 * (0 variable manquante → 0 marqueur résiduel au rendu). Pour les modèles SARL AU, on unit
 * les deux variantes d'associé unique (personne physique / personne morale) afin de couvrir
 * les deux branches {@code ◇ SI $ASSOCIE_TYPE = …}.
 */
class SuccursaleVarsCoverageTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$([A-Z][A-Z0-9_]{2,})");
    private static final Set<String> NON_VARS = Set.of("SI", "SINON", "FIN", "DEBUT", "BOUCLE", "NOM");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SuccursaleMaMapper ma = new SuccursaleMaMapper();
    private final SuccursaleEtrMapper etr = new SuccursaleEtrMapper();
    private final FermetureSuccursaleMapper ferm = new FermetureSuccursaleMapper();

    @Test
    void creation_maroc_couverte() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_ma.json");
        assertCovered("PV_CREATION_SUCCURSALE_MAROC_SARL",
                ma.map("PV_CREATION_SUCCURSALE_MAROC_SARL",
                        fx.get("PV_CREATION_SUCCURSALE_MAROC_SARL")));
        // AU : union physique + morale.
        Map<String, Object> au = fx.get("PV_CREATION_SUCCURSALE_MAROC_SARL_AU");
        assertCovered("PV_CREATION_SUCCURSALE_MAROC_SARL_AU",
                ma.map("PV_CREATION_SUCCURSALE_MAROC_SARL_AU", au),
                ma.map("PV_CREATION_SUCCURSALE_MAROC_SARL_AU", withMoraleUnique(au)));
    }

    @Test
    void creation_etrangere_couverte() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("succursale_etr.json");
        assertCovered("PV_CREATION_SUCCURSALE_ETRANGERE_SARL",
                etr.map("PV_CREATION_SUCCURSALE_ETRANGERE_SARL",
                        fx.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL")));
        assertCovered("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                etr.map("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU",
                        fx.get("PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU")));
    }

    @Test
    void fermeture_couverte() throws Exception {
        Map<String, Map<String, Object>> fx = fixtures("fermeture_succursale.json");
        assertCovered("PV_FERMETURE_SUCCURSALE_SARL",
                ferm.map("PV_FERMETURE_SUCCURSALE_SARL",
                        fx.get("PV_FERMETURE_SUCCURSALE_SARL")));
        Map<String, Object> au = fx.get("PV_FERMETURE_SUCCURSALE_SARL_AU");
        assertCovered("PV_FERMETURE_SUCCURSALE_SARL_AU",
                ferm.map("PV_FERMETURE_SUCCURSALE_SARL_AU", au),
                ferm.map("PV_FERMETURE_SUCCURSALE_SARL_AU", withMoraleUnique(au)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @SafeVarargs
    private void assertCovered(String code, Map<String, Object>... produced) {
        Set<String> needed = extractVars(code + ".docx");
        Set<String> have = new HashSet<>();
        for (Map<String, Object> p : produced) have.addAll(producedKeys(p));
        Set<String> missing = new TreeSet<>();
        for (String v : needed) {
            if (!NON_VARS.contains(v) && !have.contains(v)) missing.add(v);
        }
        assertTrue(missing.isEmpty(),
                code + " : variables NON produites par le mapper = " + missing);
    }

    /** Remplace l'associé unique physique par une personne morale (couvre la 2e branche). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withMoraleUnique(Map<String, Object> base) {
        Map<String, Object> p = new HashMap<>(base);
        Map<String, Object> morale = new HashMap<>();
        morale.put("typePersonne", "MORALE");
        morale.put("denomination", "GROUPE MAGHREB");
        morale.put("forme", "SA");
        morale.put("capital", "1 000 000 DH");
        morale.put("siege", "Rabat");
        morale.put("rcVille", "Rabat");
        morale.put("rcNumero", "98765");
        morale.put("representantNom", "M. Alaoui");
        morale.put("representantQualite", "Président");
        morale.put("nombreParts", 1000);
        morale.put("presence", "présent");
        p.put("associes", List.of(morale));
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> fixtures(String file) throws Exception {
        try (InputStream is = SuccursaleVarsCoverageTest.class.getResourceAsStream(
                "/workflow-fixtures/" + file)) {
            return JSON.readValue(is, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> producedKeys(Map<String, Object> vars) {
        Set<String> keys = new HashSet<>(vars.keySet());
        for (Object v : vars.values()) {
            if (v instanceof List<?> list) {
                for (Object it : list) {
                    if (it instanceof Map<?, ?> m) keys.addAll(((Map<String, Object>) m).keySet());
                }
            }
        }
        return keys;
    }

    private static Set<String> extractVars(String fn) {
        Set<String> vars = new HashSet<>();
        try (InputStream in = SuccursaleVarsCoverageTest.class.getClassLoader()
                .getResourceAsStream("templates/docx/" + fn);
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                StringBuilder sb = new StringBuilder();
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                Matcher m = BARE_VAR.matcher(sb.toString());
                while (m.find()) vars.add(m.group(1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Extraction impossible : " + fn, e);
        }
        return vars;
    }
}

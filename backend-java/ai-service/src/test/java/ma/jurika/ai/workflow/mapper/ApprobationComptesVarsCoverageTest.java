package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.manifest.DictionaryManifest;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase B — COUVERTURE des variables des 2 PV d'approbation des comptes. Extrait tous les
 * {@code $VAR} des modèles {@code PV_APPROBATION_COMPTES_SARL(/_AU)} et vérifie DEUX
 * garanties :
 * <ol>
 *   <li><b>0 manquante</b> : {@link ApprobationComptesMapper} (noyau séance +
 *       variables d'approbation) produit TOUTES les variables référencées ;</li>
 *   <li><b>0 hors dictionnaire</b> : chaque variable du modèle est déclarée dans
 *       {@code dictionary.json} (liste {@code variables} ∪ variables de blocs ∪
 *       {@code fill_later}).</li>
 * </ol>
 */
class ApprobationComptesVarsCoverageTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$([A-Z][A-Z0-9_]{2,})");
    // Marqueurs de balisage (jamais des variables à produire) + variable de convention.
    private static final Set<String> NON_VARS =
            Set.of("SI", "SINON", "FIN", "DEBUT", "BOUCLE", "NOM", "NOM_EN_MAJUSCULES");

    private final ApprobationComptesMapper mapper =
            new ApprobationComptesMapper(new AnnualReportMapper());

    @Test
    void pv_approbation_sarl_couvert() {
        assertCovered("PV_APPROBATION_COMPTES_SARL",
                mapper.map("PV_APPROBATION_COMPTES_SARL", payloadSarl()));
    }

    @Test
    void pv_approbation_sarl_au_couvert() {
        // Le modèle AU référence les DEUX branches associé (physique / morale) : on unit
        // les deux variantes de payload pour couvrir toutes les variables possibles.
        assertCovered("PV_APPROBATION_COMPTES_SARL_AU",
                mapper.map("PV_APPROBATION_COMPTES_SARL_AU", payloadAuPhysique()),
                mapper.map("PV_APPROBATION_COMPTES_SARL_AU", payloadAuMorale()));
    }

    @Test
    void variables_des_modeles_toutes_dans_le_dictionnaire() {
        Set<String> dict = dictionaryNames();
        for (String code : new String[]{"PV_APPROBATION_COMPTES_SARL", "PV_APPROBATION_COMPTES_SARL_AU"}) {
            Set<String> horsDict = new TreeSet<>();
            for (String v : extractVars(code + ".docx")) {
                if (!NON_VARS.contains(v) && !dict.contains(v)) horsDict.add(v);
            }
            assertTrue(horsDict.isEmpty(),
                    code + " : variables absentes du dictionnaire = " + horsDict);
        }
    }

    // ------------------------------------------------------------------

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
                code + " : variables NON produites par le mapper = " + missing
                        + "\n  produites = " + new TreeSet<>(have));
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

    private static Set<String> dictionaryNames() {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        DictionaryManifest dict = loader.dictionary();
        Set<String> names = new HashSet<>();
        if (dict == null) return names;
        if (dict.variables() != null) {
            for (DictionaryManifest.VariableDef d : dict.variables()) names.add(d.name());
        }
        if (dict.blocks() != null) {
            for (TemplateManifest.BlockDef b : dict.blocks()) {
                if (b.variables() != null) names.addAll(b.variables());
            }
        }
        // Lot L3 : fill_later retire ; les variables externes (classement) sont connues.
        ma.jurika.ai.document.ClassementVariables c = ma.jurika.ai.document.ClassementVariables.charger();
        names.addAll(c.externesCorpus());
        names.addAll(c.externesHorsCorpus());
        return names;
    }

    private static Set<String> extractVars(String fn) {
        Set<String> vars = new HashSet<>();
        try (InputStream in = ApprobationComptesVarsCoverageTest.class.getClassLoader()
                .getResourceAsStream("templates/docx/" + fn);
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                Matcher m = BARE_VAR.matcher(paraText(p));
                while (m.find()) vars.add(m.group(1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Extraction impossible : " + fn, e);
        }
        return vars;
    }

    private static String paraText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Payloads
    // ------------------------------------------------------------------

    @SafeVarargs
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static Map<String, Object> commonBase() {
        return m(
                "societe", m("denomination", "PARACOSME", "capitalChiffres", 100000L,
                        "siegeSocial", "101 bd Zerktouni, Casablanca", "nombreParts", 1000L,
                        "rcNumero", "123456", "villeGreffe", "Casablanca"),
                "seance", m("type", "ordinaire", "date", "2026-06-15", "heure", "10 heures",
                        "lieu", "au siège social", "heureCloture", "12 heures",
                        "presidentNom", "M. Yassine BENANI", "presidentQualite", "gérant",
                        "secretairePresent", "oui", "secretaireNom", "Mme Salma IDRISSI"),
                "convocation", m("auteur", "la gérance", "date", "2026-05-30",
                        "mode", "lettre recommandée avec accusé de réception"),
                "ordreDuJour", List.of("Approbation des comptes", "Affectation du résultat"),
                "resolutions", List.of(m("intitule", "Approbation", "texte", "Texte.",
                        "voixPour", "1000", "voixContre", "0", "abstentions", "0",
                        "resultat", "adoptée")),
                "gerants", List.of(m("civilite", "M", "prenom", "Yassine", "nom", "BENANI")),
                "approbation", m(
                        "exerciceClosDate", "2025-12-31",
                        "commissairePresent", "oui", "commissaireNom", "M. Le CAC",
                        "resultatType", "bénéfice", "resultatNet", 250000L,
                        "affectations", List.of(
                                m("libelle", "Réserve légale", "montant", 12500L),
                                m("libelle", "Report à nouveau", "montant", 87500L)),
                        "dividendeDistribue", "oui", "dividendeParPart", 150L,
                        "dividendeMiseEnPaiementDate", "2026-07-01"));
    }

    private static Map<String, Object> payloadSarl() {
        Map<String, Object> p = commonBase();
        p.put("associes", List.of(
                m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom", "BENANI",
                        "adresse", "5 rue A", "nombreParts", 600L, "presence", "présent"),
                m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom", "IDRISSI",
                        "adresse", "8 rue B", "nombreParts", 400L, "presence", "représenté",
                        "mandataireNom", "M. BENANI")));
        return p;
    }

    private static Map<String, Object> payloadAuPhysique() {
        Map<String, Object> p = commonBase();
        p.put("associeUnique", true);
        p.put("associes", List.of(m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Omar",
                "nom", "CHERKAOUI", "adresse", "12 rue X", "nombreParts", 1000L, "presence", "présent",
                "nationalite", "marocaine", "pieceType", "CIN", "pieceNumero", "BK12345")));
        return p;
    }

    private static Map<String, Object> payloadAuMorale() {
        Map<String, Object> p = commonBase();
        p.put("associeUnique", true);
        p.put("associes", List.of(m("typePersonne", "MORALE", "denomination", "GROUPE MAGHREB",
                "forme", "SA", "capital", "1 000 000 DH", "siege", "Rabat", "rcVille", "Rabat",
                "rcNumero", "98765", "representantNom", "M. Alaoui", "representantQualite", "Président",
                "nombreParts", 1000L, "presence", "présent")));
        return p;
    }
}

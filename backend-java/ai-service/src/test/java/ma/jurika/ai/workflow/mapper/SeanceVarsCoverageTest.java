package ma.jurika.ai.workflow.mapper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
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
 * Phase A — COUVERTURE des variables de séance. Extrait tous les {@code $VAR} des 6
 * modèles de séance (4 PV incidents + Convocation + Feuille) et vérifie que le builder
 * partagé {@link SeancePvVarsBuilder} les produit <b>tous</b> — dans les DEUX variantes
 * de nommage — de sorte qu'aucune variable de séance ne reste manquante (0 résiduel).
 *
 * <p>Les variables sont cherchées dans les clés scalaires du contrat PLUS les clés de
 * chaque item de boucle produit (ASSOCIES, POINTS_ODJ, ORDRE_DU_JOUR, RESOLUTIONS,
 * DOCUMENTS_JOINTS, GERANTS).
 */
class SeanceVarsCoverageTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$([A-Z][A-Z0-9_]{2,})");

    // Marqueurs de balisage : ce ne sont pas des variables à produire.
    private static final Set<String> NON_VARS = Set.of("SI", "SINON", "FIN", "DEBUT", "BOUCLE", "NOM");

    private final IncidentSeanceMapper incident = new IncidentSeanceMapper();
    private final SeanceDocumentMapper seance = new SeanceDocumentMapper();

    @Test
    void tous_les_pv_incidents_sont_couverts() {
        // Un modèle référence les DEUX branches associé (physique / morale) : on unit les
        // deux variantes de payload pour couvrir toutes les variables possibles.
        assertCovered("PV_DEFAUT_QUORUM_SARL",
                incident.map("PV_DEFAUT_QUORUM_SARL", payloadSarl()));
        assertCovered("PV_DEFAUT_QUORUM_SARL_AU",
                incident.map("PV_DEFAUT_QUORUM_SARL_AU", payloadAu()),
                incident.map("PV_DEFAUT_QUORUM_SARL_AU", payloadAuMorale()));
        assertCovered("PV_IRREGULARITE_CONVOCATION_SARL",
                incident.map("PV_IRREGULARITE_CONVOCATION_SARL", payloadSarl()));
        assertCovered("PV_IRREGULARITE_CONVOCATION_SARL_AU",
                incident.map("PV_IRREGULARITE_CONVOCATION_SARL_AU", payloadAu()),
                incident.map("PV_IRREGULARITE_CONVOCATION_SARL_AU", payloadAuMorale()));
    }

    @Test
    void convocation_et_feuille_sont_couvertes() {
        assertCovered("CONVOCATION_AG",
                seance.map("CONVOCATION_AG", payloadSarl()),
                seance.map("CONVOCATION_AG", payloadAu()));
        assertCovered("FEUILLE_PRESENCE_AG",
                seance.map("FEUILLE_PRESENCE_AG", payloadSarl()),
                seance.map("FEUILLE_PRESENCE_AG", payloadAu()));
    }

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
                code + " : variables de séance NON produites par le builder = " + missing
                        + "\n  produites = " + new TreeSet<>(have));
    }

    /** Clés produites = scalaires racine ∪ clés de tous les items de toutes les boucles. */
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
        try (InputStream in = SeanceVarsCoverageTest.class.getClassLoader()
                .getResourceAsStream("templates/docx/" + fn);
             XWPFDocument doc = new XWPFDocument(in)) {
            List<String> texts = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) texts.add(paraText(p));
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell c : row.getTableCells()) {
                        for (XWPFParagraph p : c.getParagraphs()) texts.add(paraText(p));
                    }
                }
            }
            for (String s : texts) {
                Matcher m = BARE_VAR.matcher(s);
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

    @SafeVarargs
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static Map<String, Object> commonBase() {
        Map<String, Object> p = m(
                "societe", m("denomination", "PARACOSME", "capitalChiffres", 100000L,
                        "siegeSocial", "101 bd Zerktouni, Casablanca", "nombreParts", 1000L,
                        "rcNumero", "123456", "villeGreffe", "Casablanca"),
                "seance", m("type", "ordinaire", "date", "2026-03-15", "heure", "10 heures",
                        "lieu", "au siège social", "heureCloture", "11 heures",
                        "presidentNom", "M. Yassine BENANI", "presidentQualite", "gérant",
                        "secretairePresent", "oui", "secretaireNom", "Mme Salma IDRISSI"),
                "convocation", m("auteur", "la gérance", "date", "2026-03-01",
                        "mode", "lettre recommandée avec accusé de réception", "rang", "deuxième",
                        "dateAgPremiere", "2026-02-28", "lieuSignature", "Casablanca",
                        "irregulariteNature", "convocation hors délai"),
                "secondeAssemblee", m("date", "2026-03-30", "heure", "10 heures", "lieu", "au siège"),
                "suiteAssemblee", "renvoi",
                "gerants", List.of(m("civilite", "M", "prenom", "Yassine", "nom", "BENANI")),
                "ordreDuJour", List.of("Approbation des comptes", "Affectation du résultat"),
                "documentsJoints", List.of("Rapport de la gérance", "Formule de pouvoir"),
                "resolutions", List.of(m("intitule", "Approbation", "texte", "Texte.",
                        "voixPour", "1000", "voixContre", "0", "abstentions", "0",
                        "resultat", "adoptée", "type", "approbation_comptes")));
        return p;
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

    private static Map<String, Object> payloadAu() {
        Map<String, Object> p = commonBase();
        p.put("associeUnique", true);
        p.put("associes", List.of(m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Omar",
                "nom", "CHERKAOUI", "adresse", "12 rue X", "nombreParts", 1000L, "presence", "présent",
                "nationalite", "marocaine", "pieceType", "CIN", "pieceNumero", "BK12345")));
        return p;
    }

    /** Associé unique personne MORALE — couvre la branche $ASSOCIE_DENOMINATION/FORME/… du modèle. */
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

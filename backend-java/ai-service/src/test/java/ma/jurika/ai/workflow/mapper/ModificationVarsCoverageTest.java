package ma.jurika.ai.workflow.mapper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
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
 * Phase E1 — COUVERTURE des variables des 2 modèles « PV de Modification » à résolutions typées.
 * Extrait tous les {@code $VAR} de chaque modèle intégré et vérifie que
 * {@link ModificationDirecteurMapper} les produit <b>tous</b> (0 variable manquante → 0 marqueur
 * résiduel au rendu), pour un PV contenant <b>une résolution de chaque type</b> (32 SARL / 28 AU)
 * et les 3 boucles imbriquées (AFFECTATIONS / GERANTS / BENEFICIAIRES_DPS). Pour le SARL AU, on
 * unit les deux variantes d'associé unique (personne physique / personne morale) afin de couvrir
 * les deux branches {@code ◇ SI : $ASSOCIE_TYPE = …}.
 */
class ModificationVarsCoverageTest {

    private static final Pattern BARE_VAR = Pattern.compile("\\$([A-Z][A-Z0-9_]{2,})");
    private static final Set<String> NON_VARS = Set.of("SI", "SINON", "FIN", "DEBUT", "BOUCLE", "NOM");

    /** 32 types du modèle SARL (ordre du modèle directeur). */
    private static final List<String> SARL_TYPES = List.of(
            "approbation_comptes", "affectation_resultat", "distribution_dividendes",
            "distribution_reserves", "acompte_dividendes", "nomination_gerant",
            "renouvellement_gerant", "revocation_gerant", "remuneration_gerant",
            "conventions_reglementees", "commissaire_comptes", "ratification_actes_formation",
            "autorisation_gerance", "augmentation_capital_numeraire", "augmentation_capital_nature",
            "augmentation_capital_incorporation", "reduction_capital", "modification_denomination",
            "modification_objet", "transfert_siege", "prorogation_duree", "modification_exercice",
            "mise_harmonie_statuts", "modification_statuts_autre", "agrement_cession",
            "agrement_transmission", "nantissement_parts", "transformation",
            "designation_commissaire_transformation", "capitaux_propres_art86",
            "operation_restructuration", "pouvoirs_formalites");

    /** 28 types du modèle SARL AU (mêmes valeurs, sans agrément/nantissement/restructuration/
     * désignation-commissaire, + cession_parts_pluripersonnelle). */
    private static final List<String> AU_TYPES = List.of(
            "approbation_comptes", "affectation_resultat", "distribution_dividendes",
            "distribution_reserves", "acompte_dividendes", "nomination_gerant",
            "renouvellement_gerant", "revocation_gerant", "remuneration_gerant",
            "conventions_reglementees", "commissaire_comptes", "ratification_actes_formation",
            "autorisation_gerance", "augmentation_capital_numeraire", "augmentation_capital_nature",
            "augmentation_capital_incorporation", "reduction_capital", "modification_denomination",
            "modification_objet", "transfert_siege", "prorogation_duree", "modification_exercice",
            "mise_harmonie_statuts", "modification_statuts_autre", "transformation",
            "cession_parts_pluripersonnelle", "capitaux_propres_art86", "pouvoirs_formalites");

    @Test
    void modification_sarl_couverte() {
        Map<String, Object> payload = payloadSarl(SARL_TYPES);
        assertCovered("PV_MODIFICATION_SARL",
                ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL", payload));
    }

    @Test
    void modification_sarl_au_couverte() {
        Map<String, Object> phys = payloadAu(AU_TYPES, false);
        Map<String, Object> morale = payloadAu(AU_TYPES, true);
        assertCovered("PV_MODIFICATION_SARL_AU",
                ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL_AU", phys),
                ModificationDirecteurMapper.pvVars("PV_MODIFICATION_SARL_AU", morale));
    }

    // ── Payloads ──────────────────────────────────────────────────────────────

    private static Map<String, Object> baseSociete() {
        Map<String, Object> s = new HashMap<>();
        s.put("denomination", "PARACOSME");
        s.put("capitalChiffres", 100_000);
        s.put("siegeSocial", "12 RUE DES FOULES, CASABLANCA");
        s.put("nombreParts", 1_000);
        s.put("rcNumero", "123456");
        s.put("villeGreffe", "CASABLANCA");
        return s;
    }

    private static Map<String, Object> baseSeance() {
        Map<String, Object> s = new HashMap<>();
        s.put("type", "extraordinaire");
        s.put("date", "2026-05-15");
        s.put("heure", "10H00");
        s.put("lieu", "SIEGE SOCIAL");
        s.put("heureCloture", "12H00");
        s.put("presidentNom", "M. Ahmed Alaoui");
        s.put("presidentQualite", "gérant");
        return s;
    }

    /** Une résolution par type + données des boucles imbriquées sur les types concernés. */
    private static List<Map<String, Object>> resolutions(List<String> types) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (String t : types) {
            Map<String, Object> r = new HashMap<>();
            r.put("type", t);
            if ("affectation_resultat".equals(t)) {
                r.put("affectations", List.of(
                        Map.of("poste", "Réserve légale", "montant", 5_000),
                        Map.of("poste", "Report à nouveau", "montant", 45_000)));
            }
            if ("nomination_gerant".equals(t)) {
                r.put("gerants", List.of(Map.of("civilite", "M.", "prenom", "Karim",
                        "nom", "Bennani", "nationalite", "marocaine", "dateNaissance", "1985-03-12",
                        "adresse", "Casablanca", "pieceType", "CIN", "pieceNumero", "BE998877")));
            }
            if ("augmentation_capital_numeraire".equals(t)) {
                r.put("beneficiairesDps", List.of(Map.of("nom", "M. Idrissi", "nbParts", 100)));
            }
            res.add(r);
        }
        return res;
    }

    private static Map<String, Object> payloadSarl(List<String> types) {
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", "SARL");
        p.put("societe", baseSociete());
        p.put("seance", baseSeance());
        p.put("convocation", Map.of("mode", "lettre recommandée", "date", "2026-04-30"));
        p.put("associes", List.of(
                Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                        "nombreParts", 600, "presence", "présent"),
                Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "nombreParts", 400, "presence", "présent")));
        p.put("ordreDuJour", List.of("Résolutions diverses"));
        p.put("resolutions", resolutions(types));
        return p;
    }

    private static Map<String, Object> payloadAu(List<String> types, boolean morale) {
        Map<String, Object> p = new HashMap<>();
        p.put("formeJuridique", "SARL_AU");
        p.put("associeUnique", true);
        p.put("societe", baseSociete());
        p.put("seance", baseSeance());
        Map<String, Object> unique = new HashMap<>();
        if (morale) {
            unique.put("typePersonne", "MORALE");
            unique.put("denomination", "GROUPE MAGHREB");
            unique.put("forme", "SA");
            unique.put("capital", "1 000 000 DH");
            unique.put("siege", "Rabat");
            unique.put("rcVille", "Rabat");
            unique.put("rcNumero", "98765");
            unique.put("representantNom", "M. Alaoui");
            unique.put("representantQualite", "Président");
            unique.put("nombreParts", 1_000);
        } else {
            unique.put("typePersonne", "PHYSIQUE");
            unique.put("civilite", "M.");
            unique.put("prenom", "Ahmed");
            unique.put("nom", "Alaoui");
            unique.put("nationalite", "marocaine");
            unique.put("adresse", "Casablanca");
            unique.put("pieceType", "CIN");
            unique.put("pieceNumero", "BE123456");
            unique.put("nombreParts", 1_000);
        }
        p.put("associes", List.of(unique));
        p.put("ordreDuJour", List.of("Décisions diverses"));
        p.put("resolutions", resolutions(types));
        return p;
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
        try (InputStream in = ModificationVarsCoverageTest.class.getClassLoader()
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

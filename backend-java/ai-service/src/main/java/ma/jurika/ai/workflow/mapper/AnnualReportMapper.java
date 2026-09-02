package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.FrenchDateToLetters;
import ma.jurika.ai.document.format.FrenchNumberToLetters;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mapper L4b du workflow {@code ANNUAL_REPORT} (rapport de gestion annuel du gérant).
 *
 * <p>Couvre :
 * <ul>
 *     <li>{@code RAPPORT_GESTION} — rapport de gestion annuel.</li>
 * </ul>
 *
 * <p>Mapper minimal — complémentaire du PV AGO annuel ({@code ApprobationComptesMapper},
 * qui délègue ce template au présent mapper). Pas de bloc, scalaires uniquement.
 */
@Component
public class AnnualReportMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "ANNUAL_REPORT";
    static final String TPL = "RAPPORT_GESTION";
    private static final Set<String> TEMPLATES = Set.of(TPL);

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRANCE);

    @Override
    public String workflowCode() {
        return WORKFLOW;
    }

    @Override
    public Set<String> supportedTemplates() {
        return TEMPLATES;
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par AnnualReportMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        Map<String, Object> out = new HashMap<>();
        putAll(out, mapSocieteHeader(asMap(safe.get("societe"))));
        putAll(out, mapExercice(asMap(safe.get("exercice"))));

        // Gérant (RAPPORT_GESTION référence GERANT_NOM).
        Map<String, Object> gerant = asMap(safe.get("gerant"));
        if (gerant != null) {
            String gNom = asString(gerant.get("nom"));
            if (gNom != null) {
                out.put("GERANT_NOM", gNom);
            }
        }

        // LIEU_DATE_EMISSION passthrough (signature du gérant).
        Map<String, Object> signature = asMap(safe.get("signature"));
        if (signature != null) {
            String lde = asString(signature.get("lieuDateEmission"));
            if (lde != null) {
                out.put("LIEU_DATE_EMISSION", lde);
            }
        }
        return out;
    }

    private Map<String, Object> mapSocieteHeader(Map<String, Object> societe) {
        Map<String, Object> m = new HashMap<>();
        if (societe == null || societe.isEmpty()) {
            return m;
        }
        m.put("DENOMINATION", asString(societe.get("denomination")));
        m.put("FORME_JURIDIQUE", asString(societe.get("formeJuridique")));
        m.put("ADRESSE_SIEGE", asString(societe.get("adresseSiege")));
        m.put("RC_NUMERO", asString(societe.get("rcNumero")));
        m.put("RC_VILLE", asString(societe.get("rcVille")));
        m.put("IF_NUMERO", asString(societe.get("ifNumero")));
        m.put("ICE_NUMERO", asString(societe.get("iceNumero")));
        m.put("ACTIVITE_SOCIETE", asString(societe.get("activiteSociete")));

        Long capital = asLong(societe.get("capitalChiffres"));
        if (capital != null) {
            m.put("CAPITAL_CHIFFRES", capital);
            m.put("CAPITAL_LETTRES", FrenchNumberToLetters.madToLetters(capital));
        }
        return m;
    }

    private Map<String, Object> mapExercice(Map<String, Object> exercice) {
        Map<String, Object> m = new HashMap<>();
        if (exercice == null || exercice.isEmpty()) {
            return m;
        }
        LocalDate dateCloture = asLocalDate(exercice.get("dateCloture"));
        if (dateCloture != null) {
            m.put("DATE_CLOTURE", dateCloture.format(DATE_FR));
            m.put("ANNEE_LETTRES", FrenchDateToLetters.yearToLetters(dateCloture.getYear()));
        }
        Long resultatNet = asLong(exercice.get("resultatNet"));
        if (resultatNet != null) {
            m.put("RESULTAT_MONTANT", resultatNet);
        }
        Long ca = asLong(exercice.get("chiffreAffaires"));
        if (ca != null) {
            m.put("CHIFFRE_AFFAIRES", ca);
        }
        Long reserveLegale = asLong(exercice.get("reserveLegale"));
        if (reserveLegale != null) {
            m.put("RESERVE_LEGALE", reserveLegale);
        }
        Long dividendes = asLong(exercice.get("dividendes"));
        if (dividendes != null) {
            m.put("DIVIDENDES", dividendes);
        }
        Long reportANouveau = asLong(exercice.get("reportANouveau"));
        if (reportANouveau != null) {
            m.put("REPORT_A_NOUVEAU", reportANouveau);
        }
        String commentaire = asString(exercice.get("commentaireActivite"));
        if (commentaire != null) {
            m.put("COMMENTAIRE_ACTIVITE", commentaire);
        }
        String evenements = asString(exercice.get("evenementsPerspectives"));
        if (evenements != null) {
            m.put("EVENEMENTS_PERSPECTIVES", evenements);
        }
        return m;
    }

    // ── Helpers ──

    private static void putAll(Map<String, Object> target, Map<String, Object> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s.replace(" ", "").replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate asLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}

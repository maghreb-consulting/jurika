package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Helpers partages par les workflows (Strategy Pattern).
 */
public final class WorkflowSteps {

    /**
     * Delai minimum, en JOURS CALENDAIRES, entre la convocation et la tenue de
     * l'assemblee (RG-M / RG-DI — regle des 16 jours). Partage par les workflows
     * MODIFICATION et DISSOLUTION.
     */
    public static final int CONVOCATION_DELAI_JOURS = 16;

    /**
     * Delai minimum, en JOURS CALENDAIRES, entre la <b>dissolution</b> d'une societe et la
     * <b>cloture de sa liquidation</b> (RG-LI03). Une societe ne peut etre liquidee que
     * 15 jours au minimum apres sa dissolution.
     *
     * <p>Regle DURE (bloquante) depuis le lot « Liquidation 4 etapes » (2026-08-13) :
     * elle n'existait auparavant qu'au front, sous forme d'avertissement non bloquant,
     * avec un seuil errone (16) et un calcul en millisecondes compare a « aujourd'hui ».
     */
    public static final int LIQUIDATION_DELAI_JOURS = 15;

    private WorkflowSteps() {}

    /**
     * Regle DURE des 15 jours entre la dissolution et la cloture de la liquidation.
     *
     * <p>Le calcul se fait sur des {@link LocalDate} : ce sont des jours CALENDAIRES,
     * insensibles au fuseau horaire et aux changements d'heure. Le delai separe la
     * <b>date de dissolution</b> et la <b>date de l'AGE de cloture</b> — jamais
     * « aujourd'hui », qui n'a aucune portee juridique ici.
     *
     * <p>Date de dissolution inconnue (dossier ancien) = pas de controle : on ne peut pas
     * opposer un delai dont on ignore le point de depart.
     *
     * @param dateDissolution date d'effet de la dissolution (peut etre {@code null})
     * @param dateCloture     date de l'AGE de cloture de la liquidation
     * @return le message d'erreur FR a remonter, ou {@code null} si la regle est respectee
     */
    public static String liquidationDelaiError(LocalDate dateDissolution, LocalDate dateCloture) {
        if (dateDissolution == null || dateCloture == null) return null;
        if (dateCloture.isBefore(dateDissolution)) {
            return "La date de cl\u00f4ture de la liquidation (" + dateCloture + ") ne peut pas "
                    + "pr\u00e9c\u00e9der la date de dissolution (" + dateDissolution + ").";
        }
        long jours = ChronoUnit.DAYS.between(dateDissolution, dateCloture);
        if (jours < LIQUIDATION_DELAI_JOURS) {
            return "D\u00e9lai l\u00e9gal insuffisant : " + jours + " jour(s) entre la dissolution ("
                    + dateDissolution + ") et la cl\u00f4ture de la liquidation (" + dateCloture
                    + "). Une soci\u00e9t\u00e9 ne peut \u00eatre liquid\u00e9e que " + LIQUIDATION_DELAI_JOURS
                    + " jours au minimum apr\u00e8s sa dissolution.";
        }
        return null;
    }

    /**
     * Regle DURE des 16 jours. Le calcul se fait sur des {@link LocalDate} : ce sont des
     * jours CALENDAIRES, insensibles au fuseau horaire et aux changements d'heure.
     *
     * <p>Convocation absente ou sans date = pas de controle (la convocation est OPTIONNELLE).
     *
     * @param convocation bloc {@code convocation} du payload (peut etre {@code null})
     * @param dateAssemblee date du PV / de l'assemblee (deja validee par l'appelant)
     * @return le message d'erreur FR a remonter, ou {@code null} si la regle est respectee
     */
    public static String convocationDelaiError(Object convocation, LocalDate dateAssemblee) {
        if (!(convocation instanceof Map<?, ?> cm) || dateAssemblee == null) return null;
        Object rawDate = cm.get("date");
        String dateConvStr = rawDate == null ? null : rawDate.toString().trim();
        if (dateConvStr == null || dateConvStr.isEmpty()) return null;
        LocalDate dateConv;
        try {
            dateConv = LocalDate.parse(dateConvStr);
        } catch (DateTimeParseException ex) {
            return "La date de convocation est invalide. Format attendu : AAAA-MM-JJ.";
        }
        if (!dateConv.isBefore(dateAssemblee)) {
            return "La date de convocation doit pr\u00e9c\u00e9der la date du PV / de l\u2019assembl\u00e9e.";
        }
        long jours = ChronoUnit.DAYS.between(dateConv, dateAssemblee);
        if (jours < CONVOCATION_DELAI_JOURS) {
            return "D\u00e9lai de convocation insuffisant : " + jours + " jour(s) entre la "
                    + "convocation (" + dateConvStr + ") et l\u2019assembl\u00e9e (" + dateAssemblee
                    + "). Un minimum de " + CONVOCATION_DELAI_JOURS + " jours est requis.";
        }
        return null;
    }

    public static void require(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new ValidationException("Champ requis manquant : " + k);
            }
        }
    }

    public static boolean isTrue(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    public static List<?> asList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof List<?> l) ? l : List.of();
    }

    /**
     * Lot L4d (E5) : le liquidateur est une personne physique (identifiee par son nom) ou
     * une personne morale (forme = "morale", identifiee par sa denomination).
     */
    public static boolean liquidateurPersonneMorale(java.util.Map<String, Object> liquidateur) {
        Object f = liquidateur == null ? null : liquidateur.get("forme");
        return f != null && "morale".equalsIgnoreCase(String.valueOf(f).trim());
    }

    /** Identite du liquidateur (nom, ou denomination d'une personne morale), ou null. */
    public static String identiteLiquidateur(java.util.Map<String, Object> liquidateur) {
        if (liquidateur == null) return null;
        Object v = liquidateurPersonneMorale(liquidateur) ? liquidateur.get("denomination") : liquidateur.get("nom");
        String s = v == null ? null : String.valueOf(v).trim();
        return s == null || s.isEmpty() ? null : s;
    }
}

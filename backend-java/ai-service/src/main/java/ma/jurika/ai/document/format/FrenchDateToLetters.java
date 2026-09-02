package ma.jurika.ai.document.format;

import java.time.LocalDate;

/**
 * Convertisseur de dates en lettres françaises (convention Maroc).
 *
 * <p>Sortie en MAJUSCULES. Exemple : {@code 2026-06-03} →
 * {@code TROIS JUIN DEUX MILLE VINGT-SIX}. Le 1er du mois s'écrit {@code PREMIER}.
 */
public final class FrenchDateToLetters {

    private FrenchDateToLetters() {
        // utility class
    }

    private static final String[] MONTHS = {
            null,           // index 0 inutilisé
            "JANVIER",
            "FÉVRIER",
            "MARS",
            "AVRIL",
            "MAI",
            "JUIN",
            "JUILLET",
            "AOÛT",
            "SEPTEMBRE",
            "OCTOBRE",
            "NOVEMBRE",
            "DÉCEMBRE"
    };

    /**
     * Convertit une date en lettres : {@code <jour> <mois> <année>}.
     *
     * @param d date à convertir (non nulle)
     * @return p. ex. {@code "TROIS JUIN DEUX MILLE VINGT-SIX"}
     */
    public static String dateToLetters(LocalDate d) {
        if (d == null) {
            throw new IllegalArgumentException("dateToLetters : date null");
        }
        int day = d.getDayOfMonth();
        int monthIdx = d.getMonthValue();
        int year = d.getYear();

        String dayWord = (day == 1) ? "PREMIER" : FrenchNumberToLetters.numberToLetters(day);
        String monthWord = MONTHS[monthIdx];
        String yearWord = yearToLetters(year);

        return dayWord + " " + monthWord + " " + yearWord;
    }

    /**
     * Convertit une année en lettres.
     *
     * @param year année (0..999_999_999)
     * @return p. ex. {@code "DEUX MILLE VINGT-SIX"} pour {@code 2026}
     */
    public static String yearToLetters(int year) {
        return FrenchNumberToLetters.numberToLetters(year);
    }
}

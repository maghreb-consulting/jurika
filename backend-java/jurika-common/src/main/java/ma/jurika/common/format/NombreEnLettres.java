package ma.jurika.common.format;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Nombres et montants en toutes lettres, en francais (convention Maroc), en MAJUSCULES.
 *
 * <p>Lot L5 (E6) : l'algorithme est celui de {@code ma.jurika.ai.document.format.FrenchNumberToLetters}
 * (ai-service), remonte ici pour servir aussi l'etat des debours (ticket-service) ; la classe
 * de l'ai-service delegue desormais a celle-ci (une seule source).
 *
 * <p>Regles : pas de septante ni nonante ; {@code QUATRE-VINGTS} et {@code CENTS} prennent un
 * {@code S} seulement en fin de nombre ; {@code MILLE} invariable ; {@code MILLION} prend un
 * {@code S} au pluriel. Plage : 0 a 999 999 999.
 */
public final class NombreEnLettres {

    private NombreEnLettres() {}

    private static final String[] UNITS = {
            "", "UN", "DEUX", "TROIS", "QUATRE", "CINQ",
            "SIX", "SEPT", "HUIT", "NEUF", "DIX",
            "ONZE", "DOUZE", "TREIZE", "QUATORZE", "QUINZE",
            "SEIZE", "DIX-SEPT", "DIX-HUIT", "DIX-NEUF"
    };

    private static final String[] TENS = {
            "", "", "VINGT", "TRENTE", "QUARANTE", "CINQUANTE",
            "SOIXANTE", "SOIXANTE", "QUATRE-VINGT", "QUATRE-VINGT"
    };

    /** Entier 0..999 999 999 en lettres majuscules ({@code 0} donne {@code ZERO} accentue). */
    public static String entier(long n) {
        if (n < 0 || n > 999_999_999L) {
            throw new IllegalArgumentException(
                    "Nombre en lettres : plage 0..999 999 999 (recu : " + n + ")");
        }
        if (n == 0) {
            return "Z\u00c9RO";
        }
        long millions = n / 1_000_000L;
        long remainder = n % 1_000_000L;
        long thousands = remainder / 1_000L;
        long units = remainder % 1_000L;

        StringBuilder sb = new StringBuilder();
        if (millions > 0) {
            sb.append(threeDigits(millions, false)).append(" MILLION");
            if (millions > 1) sb.append("S");
        }
        if (thousands > 0) {
            if (sb.length() > 0) sb.append(" ");
            if (thousands == 1) {
                sb.append("MILLE");
            } else {
                sb.append(threeDigits(thousands, true)).append(" MILLE");
            }
        }
        if (units > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(threeDigits(units, false));
        }
        return sb.toString();
    }

    /**
     * Montant en dirhams et centimes, en lettres : {@code 1234.50} donne
     * {@code MILLE DEUX CENT TRENTE-QUATRE DIRHAMS ET CINQUANTE CENTIMES}. Singulier pour un
     * dirham ou un centime ; {@code DE DIRHAMS} apres un nombre rond de millions ; aucun
     * centime n'est ecrit quand il n'y en a pas. Le montant est arrondi au centime.
     */
    public static String montantDirhams(BigDecimal montant) {
        if (montant == null) {
            throw new IllegalArgumentException("Montant en lettres : montant absent");
        }
        if (montant.signum() < 0) {
            throw new IllegalArgumentException("Montant en lettres : montant negatif (" + montant + ")");
        }
        BigDecimal arrondi = montant.setScale(2, RoundingMode.HALF_UP);
        long dirhams = arrondi.longValue();
        int centimes = arrondi.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();

        StringBuilder sb = new StringBuilder(entier(dirhams));
        if (dirhams >= 1_000_000L && dirhams % 1_000_000L == 0) {
            sb.append(" DE");
        }
        sb.append(dirhams > 1 ? " DIRHAMS" : " DIRHAM");
        if (centimes > 0) {
            sb.append(" ET ").append(entier(centimes)).append(centimes > 1 ? " CENTIMES" : " CENTIME");
        }
        return sb.toString();
    }

    /** 1..999 ; {@code followedByMore} : suivi de MILLE (CENT et QUATRE-VINGT perdent le S). */
    private static String threeDigits(long n, boolean followedByMore) {
        StringBuilder sb = new StringBuilder();
        long hundreds = n / 100;
        long rest = n % 100;
        if (hundreds > 0) {
            if (hundreds == 1) {
                sb.append("CENT");
            } else {
                sb.append(UNITS[(int) hundreds]).append(" CENT");
                if (rest == 0 && !followedByMore) sb.append("S");
            }
        }
        if (rest > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(twoDigits(rest, followedByMore));
        }
        return sb.toString();
    }

    /** 1..99. */
    private static String twoDigits(long n, boolean followedByMore) {
        if (n < 20) return UNITS[(int) n];
        long tens = n / 10;
        long units = n % 10;
        if (tens == 7) {
            if (units == 1) return "SOIXANTE ET " + UNITS[11];
            return "SOIXANTE-" + UNITS[(int) (10 + units)];
        }
        if (tens == 9) {
            return "QUATRE-VINGT-" + UNITS[(int) (10 + units)];
        }
        if (tens == 8) {
            if (units == 0) return followedByMore ? "QUATRE-VINGT" : "QUATRE-VINGTS";
            return "QUATRE-VINGT-" + UNITS[(int) units];
        }
        String t = TENS[(int) tens];
        if (units == 0) return t;
        if (units == 1) return t + " ET UN";
        return t + "-" + UNITS[(int) units];
    }
}

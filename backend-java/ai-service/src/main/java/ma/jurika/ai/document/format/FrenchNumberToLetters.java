package ma.jurika.ai.document.format;

/**
 * Convertisseur de nombres entiers en lettres françaises (convention Maroc).
 *
 * <p>Couvre 0..999_999_999. Convention française métropolitaine/Maroc :
 * pas de {@code septante}/{@code nonante}, on utilise {@code soixante-dix}
 * et {@code quatre-vingt-dix}. {@code quatre-vingts} prend un {@code s}
 * sauf si suivi d'un autre numéral.
 *
 * <p>Sortie en MAJUSCULES (convention modèles directeur).
 *
 * <p>Cas particuliers :
 * <ul>
 *     <li>{@code 0} → {@code ZÉRO}</li>
 *     <li>{@code 80} → {@code QUATRE-VINGTS} (avec {@code s})</li>
 *     <li>{@code 80_001} → {@code QUATRE-VINGT MILLE UN} (sans {@code s})</li>
 *     <li>{@code mille} invariable</li>
 *     <li>{@code million}/{@code milliard} prennent un {@code s} au pluriel</li>
 * </ul>
 */
public final class FrenchNumberToLetters {

    private FrenchNumberToLetters() {
        // utility class
    }

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

    /**
     * Convertit un entier {@code n} (0..999_999_999) en lettres françaises majuscules.
     *
     * @param n entier positif ou nul, &lt;= 999_999_999
     * @return représentation littérale en MAJUSCULES
     * @throws IllegalArgumentException si {@code n} hors plage
     */
    public static String numberToLetters(long n) {
        if (n < 0 || n > 999_999_999L) {
            throw new IllegalArgumentException(
                    "numberToLetters supporte 0..999_999_999 (reçu : " + n + ")");
        }
        if (n == 0) {
            return "ZÉRO";
        }

        long billions = n / 1_000_000_000L; // toujours 0 ici (cap à 999_999_999)
        long remainder = n % 1_000_000_000L;
        long millions = remainder / 1_000_000L;
        remainder = remainder % 1_000_000L;
        long thousands = remainder / 1_000L;
        long units = remainder % 1_000L;

        StringBuilder sb = new StringBuilder();

        if (billions > 0) {
            sb.append(threeDigitsToLetters(billions, false));
            sb.append(" MILLIARD");
            if (billions > 1) {
                sb.append("S");
            }
        }

        if (millions > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(threeDigitsToLetters(millions, false));
            sb.append(" MILLION");
            if (millions > 1) {
                sb.append("S");
            }
        }

        if (thousands > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (thousands == 1) {
                sb.append("MILLE");
            } else {
                // devant "MILLE", quatre-vingt n'a pas de s (forme adjectivale)
                sb.append(threeDigitsToLetters(thousands, true));
                sb.append(" MILLE");
            }
        }

        if (units > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(threeDigitsToLetters(units, false));
        }

        return sb.toString();
    }

    /**
     * Convertit un entier {@code n} (0..999_999_999) en lettres françaises, suffixé
     * par {@code " DIRHAMS"}.
     *
     * @param n montant entier positif ou nul
     * @return p. ex. {@code "CENT MILLE DIRHAMS"} pour {@code 100_000}
     */
    public static String madToLetters(long n) {
        return numberToLetters(n) + " DIRHAMS";
    }

    /**
     * Convertit 1..999 en lettres. {@code followedByMore} indique si le bloc
     * sera suivi d'autre chose (mille/million/etc.) — auquel cas {@code quatre-vingts}
     * et {@code cents} perdent leur {@code s} final.
     */
    private static String threeDigitsToLetters(long n, boolean followedByMore) {
        if (n <= 0 || n >= 1000) {
            throw new IllegalArgumentException("threeDigits attend 1..999 (reçu : " + n + ")");
        }

        StringBuilder sb = new StringBuilder();
        long hundreds = n / 100;
        long rest = n % 100;

        if (hundreds > 0) {
            if (hundreds == 1) {
                sb.append("CENT");
            } else {
                sb.append(UNITS[(int) hundreds]).append(" CENT");
                // "cents" pluriel uniquement si rien ne suit dans le bloc
                // ET pas suivi d'un autre numéral (mille/million)
                if (rest == 0 && !followedByMore) {
                    sb.append("S");
                }
            }
        }

        if (rest > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(twoDigitsToLetters(rest, followedByMore));
        }

        return sb.toString();
    }

    /**
     * Convertit 1..99 en lettres françaises.
     */
    private static String twoDigitsToLetters(long n, boolean followedByMore) {
        if (n <= 0 || n >= 100) {
            throw new IllegalArgumentException("twoDigits attend 1..99 (reçu : " + n + ")");
        }

        if (n < 20) {
            return UNITS[(int) n];
        }

        long tens = n / 10;
        long units = n % 10;

        // Cas 70..79 : SOIXANTE + (10..19)
        if (tens == 7) {
            long sub = 10 + units;
            // SOIXANTE ET ONZE (71) — sinon trait d'union
            if (units == 1) {
                return "SOIXANTE ET " + UNITS[(int) sub];
            }
            return "SOIXANTE-" + UNITS[(int) sub];
        }

        // Cas 90..99 : QUATRE-VINGT + (10..19)
        if (tens == 9) {
            long sub = 10 + units;
            return "QUATRE-VINGT-" + UNITS[(int) sub];
        }

        // Cas 80..89 : QUATRE-VINGT(S)
        if (tens == 8) {
            if (units == 0) {
                // 80 seul → "QUATRE-VINGTS" sauf si suivi d'un numéral (mille/million)
                return followedByMore ? "QUATRE-VINGT" : "QUATRE-VINGTS";
            }
            return "QUATRE-VINGT-" + UNITS[(int) units];
        }

        // Cas généraux 20..69
        String tensWord = TENS[(int) tens];
        if (units == 0) {
            return tensWord;
        }
        if (units == 1) {
            // VINGT ET UN, TRENTE ET UN, ..., SOIXANTE ET UN (déjà traité 71 plus haut)
            return tensWord + " ET UN";
        }
        return tensWord + "-" + UNITS[(int) units];
    }
}

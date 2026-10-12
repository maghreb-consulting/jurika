package ma.jurika.ai.document.format;

import ma.jurika.common.format.NombreEnLettres;

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
 * <p>Lot L5 (E6) : l'algorithme est remonte dans {@link NombreEnLettres} (jurika-common),
 * qui sert aussi l'etat des debours du ticket-service ; cette classe y delegue.
 */
public final class FrenchNumberToLetters {

    private FrenchNumberToLetters() {
        // utility class
    }

    /**
     * Convertit un entier {@code n} (0..999_999_999) en lettres françaises majuscules.
     *
     * @param n entier positif ou nul, &lt;= 999_999_999
     * @return représentation littérale en MAJUSCULES
     * @throws IllegalArgumentException si {@code n} hors plage
     */
    public static String numberToLetters(long n) {
        return NombreEnLettres.entier(n);
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
}

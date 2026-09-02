package ma.jurika.ai.document;

/**
 * Formatter ordinal francais feminin pour RESOLUTION_RANG.
 *
 * <p>Couvre 1..20 en feminin (PREMIERE, DEUXIEME, ..., VINGTIEME), fallback {@code n+EME}
 * au-dela. Classe pure (pas de Spring), instanciable par {@link DocxTemplateEngine}.
 *
 * <p>Utilisee par le moteur de templates pour injecter automatiquement la cle
 * {@code RESOLUTION_RANG} dans chaque item d'un bloc repetable nomme {@code RESOLUTIONS}
 * lorsque l'application n'a pas explicitement fourni la valeur.
 */
public class OrdinalFrenchFormatter {

    private static final String[] ORDINALS = {
            null,            // index 0 inutilise
            "PREMIÈRE",      // 1
            "DEUXIÈME",      // 2
            "TROISIÈME",     // 3
            "QUATRIÈME",     // 4
            "CINQUIÈME",     // 5
            "SIXIÈME",       // 6
            "SEPTIÈME",      // 7
            "HUITIÈME",      // 8
            "NEUVIÈME",      // 9
            "DIXIÈME",       // 10
            "ONZIÈME",       // 11
            "DOUZIÈME",      // 12
            "TREIZIÈME",     // 13
            "QUATORZIÈME",   // 14
            "QUINZIÈME",     // 15
            "SEIZIÈME",      // 16
            "DIX-SEPTIÈME",  // 17
            "DIX-HUITIÈME",  // 18
            "DIX-NEUVIÈME",  // 19
            "VINGTIÈME"      // 20
    };

    /**
     * Retourne l'ordinal feminin du rang {@code n} (1-based).
     *
     * @param n rang (>= 1)
     * @return PREMIERE..VINGTIEME pour 1..20, sinon "{n}EME"
     * @throws IllegalArgumentException si {@code n < 1}
     */
    public String ordinal(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("ordinal n>=1 (recu : " + n + ")");
        }
        if (n <= 20) {
            return ORDINALS[n];
        }
        return n + "ÈME"; // 21+ -> "21EME"
    }

    /**
     * Version statique pour usage utilitaire.
     */
    public static String feminineOrdinal(int n) {
        return new OrdinalFrenchFormatter().ordinal(n);
    }
}

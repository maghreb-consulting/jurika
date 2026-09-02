package ma.jurika.ai.llm;

/**
 * Résultat d'un appel au moteur OCR rapide CPU (cf. {@link FastOcrPort}).
 * Immuable. Aucune méthode n'expose d'exception : l'absence de texte est représentée par
 * un payload dégradé.
 *
 * @param text       Texte brut concaténé (lignes séparées par {@code \n}). Vide si dégradé.
 * @param engine     Identifiant du moteur ({@code doctr}, {@code paddle}, {@code noop}).
 * @param confidence Confiance moyenne pondérée par longueur (0..1). 0 si dégradé.
 * @param ms         Temps d'inférence côté microservice (ms).
 * @param degraded   True si l'OCR n'a pas pu être exécuté ou n'a rien renvoyé d'exploitable.
 * @param warning    Message lisible expliquant la dégradation (null sinon).
 */
public record FastOcrResult(
        String text,
        String engine,
        double confidence,
        int ms,
        boolean degraded,
        String warning
) {

    public FastOcrResult {
        text = text == null ? "" : text;
        engine = engine == null ? "unknown" : engine;
        if (confidence < 0.0d) confidence = 0.0d;
        if (confidence > 1.0d) confidence = 1.0d;
        if (ms < 0) ms = 0;
    }

    /** Vrai si le texte renvoyé est exploitable par l'étage LLM (au moins 3 chars). */
    public boolean hasText() {
        return !degraded && text != null && text.trim().length() >= 3;
    }

    public static FastOcrResult degraded(String engine, String warning) {
        return new FastOcrResult("", engine == null ? "noop" : engine,
                0.0d, 0, true,
                warning == null ? "OCR rapide indisponible" : warning);
    }
}

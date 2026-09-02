package ma.jurika.ai.llm;

import java.util.Map;

/**
 * Résultat d'une extraction structurée par LLM.
 * <p>
 * Immuable. Tous les champs ont des valeurs par défaut sûres (Maps vides plutôt que null).
 *
 * @param fields     Champs extraits (clé = nom du champ du schéma, valeur = String / Number / null).
 * @param confidence Score de confiance par champ (0.0 .. 1.0). Map vide si non fournie par le LLM.
 * @param provider   Identifiant du provider LLM ("groq", "openai", "ollama", "noop", ...).
 * @param model      Modèle utilisé (ex "llama-3.1-70b-versatile").
 * @param degraded   True si l'extraction a échoué / n'a pas été tentée (NoopProvider, network error, etc.).
 * @param warning    Message lisible expliquant la dégradation (null si pas dégradé).
 */
public record LlmExtractionResult(
        Map<String, Object> fields,
        Map<String, Double> confidence,
        String provider,
        String model,
        boolean degraded,
        String warning
) {

    public LlmExtractionResult {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        confidence = confidence == null ? Map.of() : Map.copyOf(confidence);
        provider = provider == null ? "unknown" : provider;
        model = model == null ? "" : model;
    }

    /**
     * Construit un résultat dégradé (champs vides, warning expliqué) — utilisé par
     * {@link NoopLlmProvider} ou par {@link OpenAiCompatibleLlmProvider} en cas d'erreur réseau / 5xx.
     */
    public static LlmExtractionResult degraded(String provider, String model, String warning) {
        return new LlmExtractionResult(
                Map.of(),
                Map.of(),
                provider == null ? "noop" : provider,
                model == null ? "" : model,
                true,
                warning == null ? "Extraction LLM indisponible" : warning
        );
    }
}

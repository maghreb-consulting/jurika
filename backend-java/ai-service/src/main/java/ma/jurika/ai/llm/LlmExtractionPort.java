package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;

/**
 * Port abstrait pour l'extraction structurée par LLM.
 * <p>
 * Deux implémentations Spring fournies :
 * <ul>
 *   <li>{@link OpenAiCompatibleLlmProvider} — appel HTTP /v1/chat/completions sur n'importe quelle
 *       API OpenAI-compatible (Groq, OpenAI, Ollama, vLLM, LM Studio, ...). Activé par
 *       {@code jurika.llm.enabled=true}.</li>
 *   <li>{@link NoopLlmProvider} — retourne toujours {@link LlmExtractionResult#degraded}. Activé par
 *       défaut ({@code jurika.llm.enabled=false} ou absent) pour permettre au service de démarrer
 *       sans clé API.</li>
 * </ul>
 */
public interface LlmExtractionPort {

    /**
     * Extrait les champs définis par le schéma à partir du texte brut.
     * <p>
     * L'implémentation ne doit JAMAIS lever d'exception non gérée : en cas d'erreur réseau / 5xx /
     * parsing JSON, retourner un résultat {@link LlmExtractionResult#degraded(String, String, String)}.
     *
     * @param text   Texte brut extrait par OCR (peut être long, plusieurs milliers de caractères).
     * @param schema Schéma cible du document.
     * @return Résultat structuré (jamais null).
     */
    LlmExtractionResult extract(String text, DocumentSchema schema);
}

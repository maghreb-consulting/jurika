package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implémentation no-op de {@link LlmExtractionPort} : retourne toujours un résultat dégradé.
 * <p>
 * Activé par défaut quand {@code jurika.llm.enabled} est absent ou explicitement à {@code false}.
 * Permet à l'application de démarrer et de servir un payload OCR-seul même sans configuration LLM.
 * <p>
 * Le frontend reçoit alors {@code degraded=true} + un warning explicite "LLM_API_KEY non configurée"
 * et bascule en saisie 100% manuelle (le rendu existant {@code OcrSuggestionsPanel} est compatible).
 */
@Component
@ConditionalOnProperty(name = "jurika.llm.enabled", havingValue = "false", matchIfMissing = true)
public class NoopLlmProvider implements LlmExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(NoopLlmProvider.class);

    public NoopLlmProvider() {
        log.info("NoopLlmProvider actif — extraction LLM désactivée (jurika.llm.enabled=false). Saisie manuelle obligatoire côté UI.");
    }

    @Override
    public LlmExtractionResult extract(String text, DocumentSchema schema) {
        return LlmExtractionResult.degraded(
                "noop",
                "",
                "LLM_API_KEY non configurée — saisie manuelle requise"
        );
    }
}

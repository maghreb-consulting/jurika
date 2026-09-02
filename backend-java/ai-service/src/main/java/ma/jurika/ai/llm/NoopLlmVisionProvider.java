package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback {@link LlmVisionExtractionPort} : retourne toujours un résultat dégradé.
 * <p>
 * Enregistré comme {@code @Bean} fallback via {@link LlmVisionAutoConfiguration} pour ne pas
 * dépendre de l'ordre d'évaluation des conditions Spring sur les implémentations utilisateur
 * (pattern identique à {@code NoopDataroomEventPublisher} dans dataroom-service).
 * <p>
 * Reste inerte si une vraie implémentation vision est présente (le bean prod est {@code @Primary}).
 */
@Configuration
public class NoopLlmVisionProvider implements LlmVisionExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(NoopLlmVisionProvider.class);

    public NoopLlmVisionProvider() {
        log.info("NoopLlmVisionProvider actif — extraction VISION désactivée (LLM_VISION_MODEL absent).");
    }

    @Override
    public LlmExtractionResult extractFromImage(byte[] imageBytes, String mimeType, DocumentSchema schema) {
        return LlmExtractionResult.degraded(
                "noop-vision",
                "",
                "LLM_VISION_MODEL absent — extraction vision indisponible, fallback texte"
        );
    }

    @Override
    public boolean isOperational() {
        return false;
    }
}

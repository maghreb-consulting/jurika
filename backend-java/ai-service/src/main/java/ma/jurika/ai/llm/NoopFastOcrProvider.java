package ma.jurika.ai.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implémentation no-op de {@link FastOcrPort}. Activée quand la voie OCR rapide n'est pas
 * configurée ({@code jurika.ocr.fast.enabled} absent ou {@code false}).
 * <p>
 * {@link GenericExtractionService} traite alors la voie comme indisponible et passe
 * directement à la chaîne vision → Tesseract → manuel, exactement comme avant cette branche.
 */
@Component
@ConditionalOnProperty(name = "jurika.ocr.fast.enabled", havingValue = "false", matchIfMissing = true)
public class NoopFastOcrProvider implements FastOcrPort {

    private static final Logger log = LoggerFactory.getLogger(NoopFastOcrProvider.class);

    public NoopFastOcrProvider() {
        log.info("NoopFastOcrProvider actif — OCR rapide désactivé (jurika.ocr.fast.enabled=false). Chaîne vision/Tesseract conservée.");
    }

    @Override
    public boolean isOperational() {
        return false;
    }

    @Override
    public FastOcrResult extract(byte[] imageBytes, String filename, String mimeType) {
        return FastOcrResult.degraded("noop", "OCR rapide non configuré");
    }
}

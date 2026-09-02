package ma.jurika.ai.infrastructure.ocr;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import ma.jurika.ai.domain.ocr.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Implémentation par défaut de {@link OcrService} — saisie manuelle requise.
 * <p>
 * Activée automatiquement via {@link ConditionalOnMissingBean} dès lors qu'aucun
 * autre adapter OCR (ex : {@link TesseractOcrService}) n'est présent dans le contexte.
 * <p>
 * Ce service est volontairement passif : il ne tente AUCUNE extraction et renvoie
 * immédiatement un résultat avec {@code requiresManualEntry=true}. Le frontend doit
 * alors présenter à l'utilisateur un formulaire de saisie manuelle des champs.
 * <p>
 * Garantit zéro régression : aucune dépendance native, aucun appel réseau, aucun blocage.
 */
@Service
@ConditionalOnMissingBean(value = OcrService.class, ignored = ManualEntryFallbackOcrService.class)
public class ManualEntryFallbackOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(ManualEntryFallbackOcrService.class);

    private static final String FALLBACK_REASON =
            "OCR non configuré (jurika.ocr.provider absent) — saisie manuelle requise";

    @Override
    public OcrExtractionResult extract(byte[] image, String filename, OcrDocumentType type) {
        log.debug("OCR fallback manuel invoqué : filename='{}', type={}, bytes={}",
                filename, type, image == null ? 0 : image.length);
        return OcrExtractionResult.manualFallback(FALLBACK_REASON);
    }
}

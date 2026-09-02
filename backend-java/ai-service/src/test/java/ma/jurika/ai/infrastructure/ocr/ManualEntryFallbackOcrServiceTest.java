package ma.jurika.ai.infrastructure.ocr;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManualEntryFallbackOcrServiceTest {

    private final ManualEntryFallbackOcrService service = new ManualEntryFallbackOcrService();

    @Test
    void extract_alwaysReturnsManualFallback() {
        OcrExtractionResult result = service.extract(new byte[]{1, 2, 3}, "cin.jpg", OcrDocumentType.CIN_RECTO);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
        assertThat(result.fields()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0d);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("OCR non configuré");
    }

    @Test
    void extract_withNullImage_returnsManualFallback() {
        OcrExtractionResult result = service.extract(null, "missing.jpg", OcrDocumentType.OTHER);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
    }

    @Test
    void extractCin_legacyApi_returnsMapWithManualFallbackMarkers() {
        Map<String, Object> map = service.extractCin(new byte[]{10, 20});

        assertThat(map).containsKey("source");
        assertThat(map.get("source")).isEqualTo("OCR_MANUAL_FALLBACK");
        assertThat(map.get("requiresManualEntry")).isEqualTo(true);
        assertThat(map.get("imageSize")).isEqualTo(2);
        assertThat(map.get("confidence")).isEqualTo(0.0d);
    }

    @Test
    void extractCertificatNegatif_legacyApi_returnsMapWithManualFallbackMarkers() {
        Map<String, Object> map = service.extractCertificatNegatif(new byte[]{1, 2, 3, 4, 5});

        assertThat(map).containsKey("source");
        assertThat(map.get("source")).isEqualTo("OCR_MANUAL_FALLBACK");
        assertThat(map.get("requiresManualEntry")).isEqualTo(true);
        assertThat(map.get("filenameSize")).isEqualTo(5);
    }
}

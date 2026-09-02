package ma.jurika.ai.domain.ocr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcrExtractionResultTest {

    @Test
    void manualFallback_setsRequiresManualEntryAndProvider() {
        OcrExtractionResult result = OcrExtractionResult.manualFallback("OCR indisponible");

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
        assertThat(result.fields()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0d);
        assertThat(result.warnings()).containsExactly("OCR indisponible");
        assertThat(result.rawText()).isEmpty();
    }

    @Test
    void manualFallback_withNullReason_usesDefaultMessage() {
        OcrExtractionResult result = OcrExtractionResult.manualFallback(null);

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).isEqualTo("Saisie manuelle requise");
    }

    @Test
    void compactConstructor_clampsConfidenceBelowZero() {
        OcrExtractionResult result = new OcrExtractionResult(
                Map.of("CIN", "AB123456"), -0.5d, false, List.of(), "raw", "tesseract"
        );
        assertThat(result.confidence()).isEqualTo(0.0d);
    }

    @Test
    void compactConstructor_clampsConfidenceAboveOne() {
        OcrExtractionResult result = new OcrExtractionResult(
                Map.of("CIN", "AB123456"), 1.5d, false, List.of(), "raw", "tesseract"
        );
        assertThat(result.confidence()).isEqualTo(1.0d);
    }

    @Test
    void compactConstructor_acceptsNullCollections() {
        OcrExtractionResult result = new OcrExtractionResult(
                null, 0.5d, false, null, "raw", "tesseract"
        );
        assertThat(result.fields()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void compactConstructor_copiesFieldsToImmutableMap() {
        java.util.Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("NOM", "BENALI");
        OcrExtractionResult result = new OcrExtractionResult(
                mutable, 0.5d, false, List.of(), "raw", "tesseract"
        );
        assertThatThrownBy(() -> result.fields().put("AUTRE", "X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

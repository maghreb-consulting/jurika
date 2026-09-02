package ma.jurika.ai.llm;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import ma.jurika.ai.domain.ocr.OcrService;
import ma.jurika.ai.llm.schema.DocumentSchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenericExtractionServiceTest {

    private OcrService ocrService;
    private LlmExtractionPort llmPort;
    private LlmVisionExtractionPort visionPort;
    private FastOcrPort fastOcrPort;
    private DocumentSchemaRegistry registry;
    private GenericExtractionService service;

    /** Mini magic bytes %PDF pour faire croire aux helpers PDFBox que c'est un PDF, sans qu'il soit lisible. */
    private static final byte[] FAKE_PDF_HEADER = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    /** Bytes JPEG simples (magic FFD8FF) — utilisés pour les tests image. */
    private static final byte[] FAKE_JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @BeforeEach
    void setUp() {
        ocrService = mock(OcrService.class);
        llmPort = mock(LlmExtractionPort.class);
        visionPort = mock(LlmVisionExtractionPort.class);
        fastOcrPort = mock(FastOcrPort.class);
        when(fastOcrPort.isOperational()).thenReturn(false);
        registry = new DocumentSchemaRegistry();
        service = new GenericExtractionService(
                ocrService, llmPort, visionPort, fastOcrPort, registry, defaultProps(false, ""));
    }

    private static LlmProperties defaultProps(boolean visionEnabled, String visionModel) {
        return new LlmProperties(
                true, "ollama", "http://localhost:11434/v1", "",
                "qwen2.5:7b", 30, 0.0,
                visionEnabled ? visionModel : "",
                120, 220, 60, 1600
        );
    }

    private void rebuildService(LlmProperties props) {
        service = new GenericExtractionService(
                ocrService, llmPort, visionPort, fastOcrPort, registry, props);
    }

    @Test
    void extract_unknownType_throws() {
        assertThatThrownBy(() -> service.extract(new byte[]{1, 2, 3}, "f.pdf", "BIDON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type inconnu");
    }

    @Test
    void extract_emptyBytes_returnsDegradedWithoutCallingOcrOrLlm() {
        Map<String, Object> out = service.extract(new byte[0], "doc.pdf", "CIN");

        assertThat(out).containsEntry("degraded", true);
        assertThat(out).containsEntry("extractionMode", "EMPTY_INPUT");
        verify(llmPort, never()).extract(anyString(), any());
        verify(visionPort, never()).extractFromImage(any(), anyString(), any());
        verify(ocrService, never()).extract(any(), anyString(), any());
    }

    @Test
    void extract_ocrEmpty_returnsDegradedWithoutCallingLlm() {
        // Pas de PDF (image JPEG) + vision off → tombe sur fallback Tesseract.
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER)))
                .thenReturn(OcrExtractionResult.manualFallback("OCR pas configuré"));

        Map<String, Object> out = service.extract(FAKE_JPEG, "doc.jpg", "CIN");

        assertThat(out).containsEntry("type", "CIN");
        assertThat(out).containsEntry("degraded", true);
        assertThat(out.get("source")).isEqualTo("OCR_EMPTY");
        verify(llmPort, never()).extract(anyString(), any());
    }

    @Test
    void extract_imageTesseractFallback_callsTesseractPipeline() {
        // Vision OFF + image → route TESSERACT_IMAGE.
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "TEXTE BRUT EXTRAIT", "tesseract"
        );
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(eq("TEXTE BRUT EXTRAIT"), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK", "prenom", "Oussama", "cinNumero", "AB123456"),
                        Map.of(), "groq", "llama-3.1-70b-versatile", false, null
                ));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("type", "CIN");
        assertThat(out).containsEntry("degraded", false);
        assertThat(out).containsEntry("provider", "groq");
        assertThat(out).containsEntry("extractionMode", "TESSERACT_IMAGE");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) out.get("fields");
        assertThat(fields).containsEntry("nom", "BENATIK");
        assertThat(fields).containsKey("dateNaissance");
        assertThat(fields.get("dateExpiration")).isNull();
    }

    @Test
    void extract_llmDegraded_returnsWarningAndEmptyFields() {
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "TEXT", "tesseract"
        );
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(anyString(), any()))
                .thenReturn(LlmExtractionResult.degraded("noop", "",
                        "LLM_API_KEY non configurée — saisie manuelle requise"));

        Map<String, Object> out = service.extract(FAKE_JPEG, "doc.jpg", "STATUTS_SARL");

        assertThat(out).containsEntry("degraded", true);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) out.get("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("LLM_API_KEY"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) out.get("fields");
        assertThat(fields).containsKeys("raisonSociale", "capitalSocial", "siegeSocial");
        assertThat(fields.values()).allMatch(v -> v == null);
    }

    @Test
    void extract_passesRawTextOcrToLlm() {
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "PAYLOAD OCR", "tesseract"
        );
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(anyString(), any()))
                .thenReturn(new LlmExtractionResult(Map.of(), Map.of(), "groq", "m", false, null));

        service.extract(FAKE_JPEG, "ras.jpg", "CERTIFICAT_NEGATIF");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).extract(textCaptor.capture(), any());
        assertThat(textCaptor.getValue()).isEqualTo("PAYLOAD OCR");
    }

    @Test
    void registry_supportsExpectedSixTypes() {
        assertThat(registry.supportedTypes()).contains(
                "CIN", "CERTIFICAT_NEGATIF", "STATUTS_SARL",
                "RC_IMMATRICULATION", "IF_DECLARATION", "JUSTIFICATIF_SIEGE"
        );
    }

    // ------------------------------------------------------------------------
    //  Tests VISION (2026-06-05) — routage intelligent
    // ------------------------------------------------------------------------

    @Test
    void extract_imageWithVisionEnabled_routesToVisionNotTesseract() {
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(visionPort.isOperational()).thenReturn(true);
        when(visionPort.extractFromImage(any(), anyString(), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK", "prenom", "Oussama", "cinNumero", "AB123456"),
                        Map.of(), "ollama-vision", "qwen3-vl:2b", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("degraded", false);
        assertThat(out).containsEntry("extractionMode", "IMAGE_VISION");
        assertThat(out.get("source")).isEqualTo("LLM_OLLAMA_VISION");
        // Tesseract NE doit PAS être appelé.
        verify(ocrService, never()).extract(any(), anyString(), any());
        verify(llmPort, never()).extract(anyString(), any());
        verify(visionPort).extractFromImage(any(), eq("image/jpeg"), any());
    }

    @Test
    void extract_visionDegraded_fallsBackToTesseractPipeline() {
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(visionPort.isOperational()).thenReturn(true);
        when(visionPort.extractFromImage(any(), anyString(), any()))
                .thenReturn(LlmExtractionResult.degraded("ollama-vision", "qwen3-vl:2b",
                        "Erreur réseau VISION — fallback texte"));
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER)))
                .thenReturn(new OcrExtractionResult(
                        Map.of(), 0.6, false, List.of(), "TXT FALLBACK", "tesseract"));
        when(llmPort.extract(eq("TXT FALLBACK"), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "X"), Map.of(),
                        "groq", "llama-3.1-70b-versatile", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("degraded", false);
        // Mode bascule sur Tesseract après échec vision (résilience pipeline).
        assertThat(out).containsEntry("extractionMode", "TESSERACT_IMAGE");
        verify(visionPort).extractFromImage(any(), anyString(), any());
        verify(ocrService).extract(any(), anyString(), any());
        verify(llmPort).extract(eq("TXT FALLBACK"), any());
    }

    @Test
    void extract_visionOff_neverCallsVisionEvenForImage() {
        // Default props : visionEnabled=false. Vision port ne doit jamais être appelé.
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "X", "tesseract");
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(anyString(), any()))
                .thenReturn(new LlmExtractionResult(Map.of(), Map.of(), "groq", "m", false, null));

        service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        verify(visionPort, never()).extractFromImage(any(), anyString(), any());
    }

    @Test
    void extract_visionPortNotOperational_skipsVisionEvenWhenConfigured() {
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(visionPort.isOperational()).thenReturn(false);
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "X", "tesseract");
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(anyString(), any()))
                .thenReturn(new LlmExtractionResult(Map.of(), Map.of(), "groq", "m", false, null));

        service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        verify(visionPort, never()).extractFromImage(any(), anyString(), any());
        verify(ocrService).extract(any(), anyString(), any());
    }

    // ------------------------------------------------------------------------
    //  Tests FAST OCR (2026-06-09 — branche feat/ocr-paddle-cpu)
    //  Voie image -> OCR rapide CPU (PaddleOCR/docTR) -> LLM texte.
    //  Prend la priorité sur la voie vision quand activée.
    // ------------------------------------------------------------------------

    @Test
    void extract_imageWithFastOcrEnabled_routesToFastOcrNotVision() {
        // Fast OCR ACTIVE + vision active : la voie rapide DOIT être préférée.
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(fastOcrPort.isOperational()).thenReturn(true);
        when(fastOcrPort.extract(any(), anyString(), anyString()))
                .thenReturn(new FastOcrResult(
                        "BENATIK\nOUSSAMA\nAB123456",
                        "doctr", 0.93, 1840, false, null));
        when(llmPort.extract(eq("BENATIK\nOUSSAMA\nAB123456"), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK", "prenom", "OUSSAMA", "cinNumero", "AB123456"),
                        Map.of(), "groq", "llama-3.1-70b-versatile", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("degraded", false);
        assertThat(out).containsEntry("extractionMode", "IMAGE_FAST_OCR_DOCTR");
        assertThat(out).containsEntry("provider", "groq");
        verify(fastOcrPort).extract(any(), eq("cin.jpg"), eq("image/jpeg"));
        verify(llmPort).extract(eq("BENATIK\nOUSSAMA\nAB123456"), any());
        // Vision NE doit PAS être appelé : la voie rapide a réussi.
        verify(visionPort, never()).extractFromImage(any(), anyString(), any());
        verify(ocrService, never()).extract(any(), anyString(), any());
    }

    @Test
    void extract_fastOcrDegraded_fallsBackToVision() {
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(fastOcrPort.isOperational()).thenReturn(true);
        when(fastOcrPort.extract(any(), anyString(), anyString()))
                .thenReturn(FastOcrResult.degraded("doctr", "Timeout"));
        when(visionPort.isOperational()).thenReturn(true);
        when(visionPort.extractFromImage(any(), anyString(), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK"), Map.of(),
                        "ollama-vision", "qwen3-vl:2b", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("degraded", false);
        assertThat(out).containsEntry("extractionMode", "IMAGE_VISION");
        verify(fastOcrPort).extract(any(), anyString(), anyString());
        verify(visionPort).extractFromImage(any(), anyString(), any());
    }

    @Test
    void extract_fastOcrLlmDegraded_fallsBackToVision() {
        // OCR rapide OK mais LLM texte dégradé -> fallback vision.
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(fastOcrPort.isOperational()).thenReturn(true);
        when(fastOcrPort.extract(any(), anyString(), anyString()))
                .thenReturn(new FastOcrResult("BENATIK", "doctr", 0.9, 1200, false, null));
        when(llmPort.extract(eq("BENATIK"), any()))
                .thenReturn(LlmExtractionResult.degraded("noop", "", "LLM_API_KEY non configurée"));
        when(visionPort.isOperational()).thenReturn(true);
        when(visionPort.extractFromImage(any(), anyString(), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK"), Map.of(),
                        "ollama-vision", "qwen3-vl:2b", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("extractionMode", "IMAGE_VISION");
        verify(fastOcrPort).extract(any(), anyString(), anyString());
        verify(llmPort).extract(eq("BENATIK"), any());
        verify(visionPort).extractFromImage(any(), anyString(), any());
    }

    @Test
    void extract_fastOcrOff_visionOff_fallsBackToTesseract() {
        // Fast OCR off + vision off -> Tesseract historique inchangé.
        when(fastOcrPort.isOperational()).thenReturn(false);
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.5, false, List.of(), "X", "tesseract");
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(anyString(), any()))
                .thenReturn(new LlmExtractionResult(Map.of(), Map.of(), "groq", "m", false, null));

        service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        verify(fastOcrPort, never()).extract(any(), anyString(), anyString());
        verify(visionPort, never()).extractFromImage(any(), anyString(), any());
        verify(ocrService).extract(any(), anyString(), any());
    }

    @Test
    void extract_emptyText_fromFastOcr_isTreatedAsDegraded() {
        // OCR rapide renvoie 2 chars seulement -> hasText()=false -> fallback.
        rebuildService(defaultProps(true, "qwen3-vl:2b"));
        when(fastOcrPort.isOperational()).thenReturn(true);
        when(fastOcrPort.extract(any(), anyString(), anyString()))
                .thenReturn(new FastOcrResult("ab", "doctr", 0.5, 1500, false, null));
        when(visionPort.isOperational()).thenReturn(true);
        when(visionPort.extractFromImage(any(), anyString(), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("nom", "BENATIK"), Map.of(),
                        "ollama-vision", "qwen3-vl:2b", false, null));

        Map<String, Object> out = service.extract(FAKE_JPEG, "cin.jpg", "CIN");

        assertThat(out).containsEntry("extractionMode", "IMAGE_VISION");
        verify(llmPort, never()).extract(anyString(), any());
    }

    @Test
    void extract_pdfWithoutTextLayer_visionOff_fallsBackToTesseract() {
        // FAKE_PDF_HEADER ne contient pas de couche texte lisible → on doit aller vers Tesseract.
        OcrExtractionResult ocrResult = new OcrExtractionResult(
                Map.of(), 0.6, false, List.of(), "FROM TESSERACT PDF RENDER", "tesseract");
        when(ocrService.extract(any(), anyString(), eq(OcrDocumentType.OTHER))).thenReturn(ocrResult);
        when(llmPort.extract(eq("FROM TESSERACT PDF RENDER"), any()))
                .thenReturn(new LlmExtractionResult(
                        Map.of("ice", "001122334455667"),
                        Map.of(), "groq", "llama-3.1-70b-versatile", false, null));

        Map<String, Object> out = service.extract(FAKE_PDF_HEADER, "scan.pdf", "CERTIFICAT_NEGATIF");

        assertThat(out).containsEntry("degraded", false);
        assertThat(out).containsEntry("extractionMode", "PDFBOX_RENDER_TESSERACT");
    }
}

package ma.jurika.ai.llm;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import ma.jurika.ai.domain.ocr.OcrService;
import ma.jurika.ai.llm.schema.DocumentSchema;
import ma.jurika.ai.llm.schema.DocumentSchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pipeline générique d'extraction documentaire à <b>routage intelligent</b>.
 * <p>
 * Le routage choisit la voie la plus précise selon le type de fichier (PDF numérique vs
 * image / PDF scanné) <i>et</i> la disponibilité du LLM vision local. Tout reste local (Ollama)
 * pour respecter la CNDP (loi 09-08).
 *
 * <h2>Pipeline (2026-06-05 — branche feat/ocr-vision-precision)</h2>
 * <pre>
 *           ┌─ PDF avec couche texte ≥ MIN_CHARS
 *           │     → PDFBox text + LLM texte                    (mode PDFBOX_TEXT)
 *           │
 *  bytes ───┼─ PDF scanné OU image
 *           │   ├─ Vision activé (LLM_VISION_MODEL)
 *           │   │     → raster 300 dpi (PDF) ou direct (image)
 *           │   │     → LLM VISION (qwen3-vl)                  (mode PDFBOX_RENDER_VISION | IMAGE_VISION)
 *           │   └─ Vision absent → Tesseract + LLM texte       (mode PDFBOX_RENDER_TESSERACT | TESSERACT_IMAGE)
 *           │
 *           └─ Tout échoue → Noop (saisie manuelle)            (mode FALLBACK_MANUAL)
 * </pre>
 *
 * <h3>Comportement gracieux</h3>
 * <ul>
 *   <li>Type inconnu → {@link IllegalArgumentException}.</li>
 *   <li>VISION dégradé / erreur → fallback automatique sur Tesseract+LLM (jamais bloquant).</li>
 *   <li>OCR vide → payload dégradé {@code degraded=true}.</li>
 *   <li>LLM dégradé / absent → payload avec champs vides + warning explicite.</li>
 * </ul>
 */
@Service
public class GenericExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GenericExtractionService.class);

    private final OcrService ocrService;
    private final LlmExtractionPort llmPort;
    private final LlmVisionExtractionPort visionPort;
    private final FastOcrPort fastOcrPort;
    private final DocumentSchemaRegistry registry;
    private final LlmProperties props;

    public GenericExtractionService(OcrService ocrService,
                                    LlmExtractionPort llmPort,
                                    LlmVisionExtractionPort visionPort,
                                    FastOcrPort fastOcrPort,
                                    DocumentSchemaRegistry registry,
                                    LlmProperties props) {
        this.ocrService = ocrService;
        this.llmPort = llmPort;
        this.visionPort = visionPort;
        this.fastOcrPort = fastOcrPort;
        this.registry = registry;
        this.props = props;
        log.info("GenericExtractionService initialisé — fastOcr={} vision={} (model={})",
                fastOcrPort.isOperational() ? "ACTIVE" : "OFF",
                props.visionEnabled() ? "ACTIVE" : "OFF",
                props.visionEnabled() ? props.visionModel() : "-");
    }

    /**
     * Lance le pipeline avec routage intelligent.
     *
     * @param fileBytes    Bytes du document (PDF ou image).
     * @param filename     Nom de fichier original (logs + détection magic bytes).
     * @param docTypeCode  Code du type (voir {@link DocumentSchemaRegistry#supportedTypes()}).
     * @return Payload prêt à sérialiser en JSON pour le frontend.
     * @throws IllegalArgumentException si le type est inconnu du registre.
     */
    public Map<String, Object> extract(byte[] fileBytes, String filename, String docTypeCode) {
        DocumentSchema schema = registry.find(docTypeCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Type inconnu: " + docTypeCode
                                + " — types supportés: " + registry.supportedTypes()));

        int len = fileBytes == null ? 0 : fileBytes.length;
        // EX1 2026-06-09 — Mesure end-to-end + par voie pour visibilite operationnelle.
        long tStart = System.nanoTime();
        log.info("Extraction START — type={} filename={} bytes={} visionEnabled={}",
                docTypeCode, filename, len, props.visionEnabled());

        if (fileBytes == null || fileBytes.length == 0) {
            return buildPayload(schema, Map.of(),
                    "OCR_EMPTY", "noop", "", true,
                    "EMPTY_INPUT",
                    List.of("Document vide — saisie manuelle requise"));
        }

        boolean isPdf = PdfDocumentInspector.looksLikePdf(fileBytes, filename);

        // -------- Voie 1 : PDF numérique avec couche texte exploitable ----------
        if (isPdf) {
            long tText = System.nanoTime();
            String textLayer = PdfDocumentInspector.extractTextLayer(fileBytes);
            int textLen = textLayer == null ? 0 : textLayer.trim().length();
            if (textLen >= props.minPdfTextLayerChars()) {
                log.info("Routage PDFBOX_TEXT (PDF numérique, {} chars, extract {} ms)",
                        textLen, msSince(tText));
                LlmExtractionResult llm = llmPort.extract(textLayer, schema);
                logTotal(docTypeCode, "PDFBOX_TEXT", tStart);
                return resultFromLlm(schema, llm, "PDFBOX_TEXT");
            }
            log.info("PDF sans couche texte ({} chars, en {} ms) — bascule OCR rapide/vision/scanné",
                    textLen, msSince(tText));

            // -------- Voie 2 : PDF scanné -> raster -> OCR rapide CPU -> LLM texte ------
            // (PADDLE_OCR_TEXT 2026-06-09 — branche feat/ocr-paddle-cpu)
            // L'OCR rapide CPU (PaddleOCR / docTR) renvoie du texte propre en quelques
            // secondes alors que la voie vision multimodale prend 100-120s sur CPU host.
            // Si l'OCR rapide est dégradé OU si le texte est insuffisant -> fallback vision.
            byte[] rasterPng = null;
            if (fastOcrPort.isOperational()) {
                long tRender = System.nanoTime();
                rasterPng = PdfDocumentInspector.renderFirstPageToPng(
                        fileBytes, props.pdfRenderDpi());
                if (rasterPng != null) {
                    log.info("Routage PDFBOX_RENDER_FAST_OCR (PDF scanné, dpi={}, {} kB, render {} ms)",
                            props.pdfRenderDpi(), rasterPng.length / 1024, msSince(tRender));
                    long tOcr = System.nanoTime();
                    FastOcrResult ocrFast = fastOcrPort.extract(rasterPng, "page-1.png", "image/png");
                    log.info("FAST OCR ({}) terminee en {} ms (degraded={} chars={} conf={})",
                            ocrFast.engine(), msSince(tOcr), ocrFast.degraded(),
                            ocrFast.text().length(), String.format("%.2f", ocrFast.confidence()));
                    if (ocrFast.hasText()) {
                        long tLlmFast = System.nanoTime();
                        LlmExtractionResult llmFast = llmPort.extract(ocrFast.text(), schema);
                        log.info("LLM texte terminee en {} ms (degraded={})",
                                msSince(tLlmFast), llmFast.degraded());
                        String fastMode = "PDFBOX_RENDER_FAST_OCR_" + ocrFast.engine().toUpperCase(Locale.ROOT);
                        if (!llmFast.degraded()) {
                            logTotal(docTypeCode, fastMode, tStart);
                            return resultFromLlm(schema, llmFast, fastMode);
                        }
                        log.warn("LLM texte dégradé après FAST OCR — fallback vision : {}", llmFast.warning());
                    } else {
                        log.warn("FAST OCR dégradé sur PDF scanné — fallback vision : {}", ocrFast.warning());
                    }
                }
            }

            // -------- Voie 2bis : PDF scanné -> raster -> VISION (fallback) -------------
            if (props.visionEnabled() && visionPort.isOperational()) {
                long tRender = System.nanoTime();
                byte[] png = rasterPng != null ? rasterPng
                        : PdfDocumentInspector.renderFirstPageToPng(fileBytes, props.pdfRenderDpi());
                if (png != null) {
                    log.info("Routage PDFBOX_RENDER_VISION (PDF scanné, dpi={}, {} kB, render {} ms)",
                            props.pdfRenderDpi(), png.length / 1024, msSince(tRender));
                    long tVis = System.nanoTime();
                    LlmExtractionResult vis = visionPort.extractFromImage(png, "image/png", schema);
                    log.info("VISION terminee en {} ms (degraded={})", msSince(tVis), vis.degraded());
                    if (!vis.degraded()) {
                        logTotal(docTypeCode, "PDFBOX_RENDER_VISION", tStart);
                        return resultFromLlm(schema, vis, "PDFBOX_RENDER_VISION");
                    }
                    log.warn("VISION dégradé sur PDF scanné — fallback Tesseract+LLM : {}", vis.warning());
                }
            }
        }
        // -------- Voie 3 : Image directe -> OCR rapide CPU -> LLM texte -------------
        // (PADDLE_OCR_TEXT 2026-06-09 — voie principale pour les CIN scannées en JPG/PNG)
        else if (fastOcrPort.isOperational()) {
            String mime = PdfDocumentInspector.guessImageMimeType(filename);
            log.info("Routage IMAGE_FAST_OCR (image directe mime={})", mime);
            long tOcr = System.nanoTime();
            FastOcrResult ocrFast = fastOcrPort.extract(fileBytes, filename, mime);
            log.info("FAST OCR ({}) terminee en {} ms (degraded={} chars={} conf={})",
                    ocrFast.engine(), msSince(tOcr), ocrFast.degraded(),
                    ocrFast.text().length(), String.format("%.2f", ocrFast.confidence()));
            if (ocrFast.hasText()) {
                long tLlmFast = System.nanoTime();
                LlmExtractionResult llmFast = llmPort.extract(ocrFast.text(), schema);
                log.info("LLM texte terminee en {} ms (degraded={})",
                        msSince(tLlmFast), llmFast.degraded());
                String fastMode = "IMAGE_FAST_OCR_" + ocrFast.engine().toUpperCase(Locale.ROOT);
                if (!llmFast.degraded()) {
                    logTotal(docTypeCode, fastMode, tStart);
                    return resultFromLlm(schema, llmFast, fastMode);
                }
                log.warn("LLM texte dégradé après FAST OCR — fallback vision : {}", llmFast.warning());
            } else {
                log.warn("FAST OCR dégradé sur image — fallback vision : {}", ocrFast.warning());
            }

            // Si vision dispo : on tente après l'échec du FAST OCR.
            if (props.visionEnabled() && visionPort.isOperational()) {
                log.info("Routage IMAGE_VISION (fallback après FAST OCR)");
                long tVis = System.nanoTime();
                LlmExtractionResult vis = visionPort.extractFromImage(fileBytes, mime, schema);
                log.info("VISION terminee en {} ms (degraded={})", msSince(tVis), vis.degraded());
                if (!vis.degraded()) {
                    logTotal(docTypeCode, "IMAGE_VISION", tStart);
                    return resultFromLlm(schema, vis, "IMAGE_VISION");
                }
                log.warn("VISION dégradé sur image — fallback Tesseract+LLM : {}", vis.warning());
            }
        }
        // -------- Voie 3bis : Image -> VISION direct (FAST OCR off) ----------------
        else if (props.visionEnabled() && visionPort.isOperational()) {
            String mime = PdfDocumentInspector.guessImageMimeType(filename);
            log.info("Routage IMAGE_VISION (image directe mime={})", mime);
            long tVis = System.nanoTime();
            LlmExtractionResult vis = visionPort.extractFromImage(fileBytes, mime, schema);
            log.info("VISION terminee en {} ms (degraded={})", msSince(tVis), vis.degraded());
            if (!vis.degraded()) {
                logTotal(docTypeCode, "IMAGE_VISION", tStart);
                return resultFromLlm(schema, vis, "IMAGE_VISION");
            }
            log.warn("VISION dégradé sur image — fallback Tesseract+LLM : {}", vis.warning());
        }

        // -------- Voie 3 : Tesseract (texte OCR) -> LLM texte (legacy) ---------
        log.info("Routage TESSERACT (fallback {}{}", isPdf ? "PDF" : "image",
                props.visionEnabled() ? ", vision indisponible)" : ")");
        long tTess = System.nanoTime();
        OcrExtractionResult ocr = ocrService.extract(
                fileBytes,
                filename == null ? "document.pdf" : filename,
                OcrDocumentType.OTHER);
        log.info("Tesseract terminee en {} ms ({} chars)",
                msSince(tTess), ocr.rawText() == null ? 0 : ocr.rawText().length());

        List<String> warnings = new ArrayList<>(ocr.warnings());
        String rawText = ocr.rawText();

        if (rawText == null || rawText.isBlank()) {
            log.warn("OCR vide — abort LLM");
            String mode = isPdf ? "PDFBOX_RENDER_TESSERACT_EMPTY" : "TESSERACT_IMAGE_EMPTY";
            logTotal(docTypeCode, mode, tStart);
            return buildPayload(
                    schema, Map.of(),
                    "OCR_EMPTY", "ocr", "", true, mode,
                    appendWarning(warnings,
                            "OCR n'a extrait aucun texte exploitable — saisie manuelle requise"));
        }

        long tLlm = System.nanoTime();
        LlmExtractionResult llm = llmPort.extract(rawText, schema);
        log.info("LLM texte terminee en {} ms (degraded={})", msSince(tLlm), llm.degraded());
        String mode = isPdf ? "PDFBOX_RENDER_TESSERACT" : "TESSERACT_IMAGE";

        if (llm.degraded()) {
            warnings = appendWarning(warnings,
                    llm.warning() == null
                            ? "LLM_API_KEY non configurée — saisie manuelle requise"
                            : llm.warning());
            logTotal(docTypeCode, mode + "_FALLBACK_MANUAL", tStart);
            return buildPayload(
                    schema, Map.of(),
                    sourceLabel(llm.provider(), true),
                    llm.provider(), llm.model(), true,
                    mode + "_FALLBACK_MANUAL",
                    warnings);
        }

        logTotal(docTypeCode, mode, tStart);
        return buildPayload(
                schema, llm.fields(),
                sourceLabel(llm.provider(), false),
                llm.provider(), llm.model(), false,
                mode,
                warnings);
    }

    /** EX1 2026-06-09 — Helper millisecondes ecoulees depuis un nanoTime. */
    private static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** EX1 2026-06-09 — Trace finale end-to-end : type + voie + duree totale. */
    private static void logTotal(String docTypeCode, String mode, long startNanos) {
        log.info("Extraction END — type={} mode={} totalMs={}",
                docTypeCode, mode, msSince(startNanos));
    }

    // ------------------------------------------------------------------------

    private Map<String, Object> resultFromLlm(DocumentSchema schema,
                                              LlmExtractionResult llm,
                                              String mode) {
        if (llm.degraded()) {
            return buildPayload(
                    schema, Map.of(),
                    sourceLabel(llm.provider(), true),
                    llm.provider(), llm.model(), true,
                    mode + "_FALLBACK_MANUAL",
                    List.of(llm.warning() == null
                            ? "Extraction LLM indisponible — saisie manuelle requise"
                            : llm.warning()));
        }
        return buildPayload(
                schema, llm.fields(),
                sourceLabel(llm.provider(), false),
                llm.provider(), llm.model(), false,
                mode,
                List.of());
    }

    private Map<String, Object> buildPayload(DocumentSchema schema,
                                             Map<String, Object> fields,
                                             String source,
                                             String provider,
                                             String model,
                                             boolean degraded,
                                             String extractionMode,
                                             List<String> warnings) {
        Map<String, Object> normalized = new HashMap<>();
        for (DocumentSchema.FieldDef f : schema.fields()) {
            normalized.put(f.name(), fields.get(f.name()));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("type", schema.typeCode());
        out.put("fields", normalized);
        out.put("confidence", Map.of());
        out.put("source", source);
        out.put("provider", provider);
        out.put("model", model);
        out.put("degraded", degraded);
        out.put("extractionMode", extractionMode);
        out.put("warnings", warnings);
        return out;
    }

    private static List<String> appendWarning(List<String> warnings, String add) {
        List<String> out = new ArrayList<>(warnings);
        if (add != null && !add.isBlank() && !out.contains(add)) {
            out.add(add);
        }
        return out;
    }

    private static String sourceLabel(String provider, boolean degraded) {
        if (degraded) return "LLM_DEGRADED";
        String p = provider == null ? "UNKNOWN" : provider.toUpperCase(Locale.ROOT).replace('-', '_');
        return "LLM_" + p;
    }
}

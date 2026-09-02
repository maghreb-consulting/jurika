package ma.jurika.ai.api;

import ma.jurika.ai.domain.factory.DocumentFactory;
import ma.jurika.ai.domain.factory.DocumentFactory.DocumentType;
import ma.jurika.ai.domain.ocr.OcrService;
import ma.jurika.ai.llm.GenericExtractionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final OcrService ocr;
    private final DocumentFactory documentFactory;
    private final GenericExtractionService genericExtraction;

    public AiController(OcrService ocr,
                        DocumentFactory documentFactory,
                        GenericExtractionService genericExtraction) {
        this.ocr = ocr;
        this.documentFactory = documentFactory;
        this.genericExtraction = genericExtraction;
    }

    @PostMapping("/extract-cn")
    public Map<String, Object> extractCn(@RequestParam("file") MultipartFile file) throws IOException {
        return ocr.extractCertificatNegatif(file.getBytes());
    }

    @PostMapping("/extract-cin")
    public Map<String, Object> extractCin(@RequestParam("file") MultipartFile file) throws IOException {
        return ocr.extractCin(file.getBytes());
    }

    /**
     * P4 — Endpoint générique d'extraction par LLM pour TOUS les types de documents
     * supportés (cf. {@link ma.jurika.ai.llm.schema.DocumentSchemaRegistry}).
     * <p>
     * Pipeline 2 étages : OCR (PDFBox/Tesseract) → LLM (provider OpenAI-compatible).
     * <p>
     * Réponse : {@code {type, fields:{...}, confidence:{}, source, provider, model, degraded, extractionMode, warnings}}.
     */
    @PostMapping("/extract")
    public Map<String, Object> extractGeneric(@RequestParam("type") String type,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        return genericExtraction.extract(file.getBytes(), file.getOriginalFilename(), type);
    }

    @PostMapping("/generate-document")
    public Map<String, Object> generate(@RequestParam("type") DocumentType type,
                                         @RequestBody Map<String, Object> data) {
        return documentFactory.create(type, data);
    }
}

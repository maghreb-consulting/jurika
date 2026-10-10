package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ma.jurika.ai.document.DocumentTypes;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.TemplateExtractor;
import ma.jurika.ai.document.TemplateExtractor.ExtractionResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/ai/documents")
public class DocumentController {

    private final DocxTemplateEngine engine;
    private final TemplateExtractor extractor;

    public DocumentController(DocxTemplateEngine engine, TemplateExtractor extractor) {
        this.engine = engine;
        this.extractor = extractor;
    }

    /**
     * Convertit un .docx d'exemple en template avec placeholders {{var}}.
     * Renvoie un JSON contenant le template (base64) + la liste des variables detectees.
     */
    @PostMapping(value = "/extract-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public Map<String, Object> extractTemplate(@RequestPart("file") MultipartFile file,
                                                 @RequestParam("name") String name) throws IOException {
        ExtractionResult result = extractor.extract(file.getBytes(), name);
        return Map.of(
                "templateName", result.templateName(),
                "extractedAt", result.extractedAt(),
                "templateBase64", java.util.Base64.getEncoder().encodeToString(result.templateBytes()),
                "variables", result.detectedVariables());
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public Set<String> listTypes() {
        return DocumentTypes.ALL;
    }

    /**
     * Genere un document a partir d'un template DOCX + variables.
     * Lot L2 : gabarit introuvable -> 404 (GabaritIntrouvableException) ; plus de
     * document de remplacement.
     */
    @PostMapping("/generate/{templateCode}")
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    public ResponseEntity<byte[]> generate(@PathVariable @NotBlank String templateCode,
                                            @Valid @RequestBody Map<String, Object> variables) {
        if (!DocumentTypes.ALL.contains(templateCode)) {
            return ResponseEntity.badRequest().build();
        }
        DocumentResult result = engine.generate(templateCode, variables);
        // Lot L3 : meme regle que la generation par parcours (donnee interne manquante : refus nomme).
        ma.jurika.ai.document.ControleCompletude.verifier(templateCode, result, engine.dictionnaire());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Template-Found", String.valueOf(result.templateFound()))
                .header("X-Missing-Variables", String.join(",", result.missingVariables()))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.bytes());
    }
}

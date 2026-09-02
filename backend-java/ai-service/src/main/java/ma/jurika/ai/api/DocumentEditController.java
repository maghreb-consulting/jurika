package ma.jurika.ai.api;

import ma.jurika.ai.document.DocxToPdfConverter;
import ma.jurika.ai.document.HtmlToDocxConverter;
import ma.jurika.ai.document.HtmlToPdfConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints pour l'éditeur in-app TipTap (Sprint 2026-06-12).
 *
 * <p>Le frontend ouvre le DOCX généré, le convertit en HTML via Mammoth.js,
 * laisse l'utilisateur éditer dans TipTap, puis poste le HTML édité pour :
 * <ul>
 *   <li>{@code POST /api/v1/ai/document-edit/html-to-docx} — télécharger en DOCX</li>
 *   <li>{@code POST /api/v1/ai/document-edit/html-to-pdf} — télécharger en PDF</li>
 * </ul>
 *
 * <p>Le dépôt en dataroom (écraser la version courante) est porté par
 * {@code dataroom-service}, l'ai-service ne fait que la conversion.
 */
@RestController
@RequestMapping("/api/v1/ai/document-edit")
public class DocumentEditController {

    private final HtmlToDocxConverter docxConverter;
    private final HtmlToPdfConverter pdfConverter;

    public DocumentEditController(HtmlToDocxConverter docxConverter,
                                    HtmlToPdfConverter pdfConverter) {
        this.docxConverter = docxConverter;
        this.pdfConverter = pdfConverter;
    }

    public record EditRequest(String html, String filename, String title) {}

    @PostMapping("/html-to-docx")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> htmlToDocx(@RequestBody EditRequest req) {
        if (req == null || req.html() == null) return ResponseEntity.badRequest().build();
        byte[] bytes = docxConverter.convert(req.html());
        String filename = sanitize(req.filename(), "document.docx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @PostMapping("/html-to-pdf")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> htmlToPdf(@RequestBody EditRequest req) {
        if (req == null || req.html() == null) return ResponseEntity.badRequest().build();
        byte[] bytes;
        try {
            bytes = pdfConverter.convert(req.html(), req.title());
        } catch (DocxToPdfConverter.LibreOfficeUnavailableException ex) {
            // Migration 2026-07-14 : rendu PDF via LibreOffice. Absent -> 503, le
            // front bascule sur l'export DOCX (html-to-docx, pur POI).
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String filename = sanitize(req.filename(), "document.pdf");
        if (!filename.toLowerCase().endsWith(".pdf")) {
            filename = filename.replaceAll("\\.[^.]+$", "") + ".pdf";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private String sanitize(String requested, String fallback) {
        if (requested == null || requested.isBlank()) return fallback;
        // Strip path separators et controlchars (en-tete HTTP).
        return requested.replaceAll("[\\\\/\\r\\n\"]", "_");
    }
}

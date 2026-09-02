package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ma.jurika.ai.document.DocumentTypes;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.DocxToPdfConverter;
import ma.jurika.ai.document.DocxToPdfConverter.DocxToPdfConversionException;
import ma.jurika.ai.document.DocxToPdfConverter.LibreOfficeUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Rendu PDF FIDELE (Sprint 2026-06-19).
 *
 * <p>Le pipeline historique passait par Mammoth.js (DOCX -> HTML) puis iText
 * (HTML -> PDF) avec un CSS Times New Roman 11pt figé : tout le PDF ressortait
 * uniformement sans la hierarchie ni la police du gabarit Word.
 *
 * <p>Ce controller expose deux endpoints qui passent le .docx style (genere
 * par {@link DocxTemplateEngine} + {@link ma.jurika.ai.document.StatutsHierarchyApplier})
 * directement a LibreOffice headless via {@link DocxToPdfConverter} : le PDF
 * resultant respecte 100 % les styles du gabarit (Calibri, JurikaTitreArticle,
 * JurikaSousTitre, variables rouges, marges).
 *
 * <p>Si LibreOffice est absent du serveur, on retourne 503 + en-tete
 * {@code X-LibreOffice-Unavailable: true} pour permettre au front de basculer
 * sur le telechargement du .docx natif (fallback transparent pour l'utilisateur).
 */
@RestController
@RequestMapping("/api/v1/ai/document-render")
public class DocumentRenderController {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderController.class);

    private static final String HDR_TEMPLATE_FOUND = "X-Template-Found";
    private static final String HDR_MISSING_VARS = "X-Missing-Variables";
    private static final String HDR_LO_UNAVAILABLE = "X-LibreOffice-Unavailable";

    private final DocxTemplateEngine engine;
    private final DocxToPdfConverter pdfConverter;

    public DocumentRenderController(DocxTemplateEngine engine, DocxToPdfConverter pdfConverter) {
        this.engine = engine;
        this.pdfConverter = pdfConverter;
    }

    /**
     * Genere le .docx style depuis le template puis le convertit en PDF FIDELE
     * via LibreOffice. C'est l'endpoint utilise par l'etape 7 pour le bouton
     * "Telecharger PDF" (remplace l'ancien chemin Mammoth + html-to-pdf).
     */
    @PostMapping("/template-to-pdf/{templateCode}")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> templateToPdf(
            @PathVariable @NotBlank String templateCode,
            @Valid @RequestBody Map<String, Object> variables) {
        if (!DocumentTypes.ALL.contains(templateCode)) {
            return ResponseEntity.badRequest().build();
        }
        if (!pdfConverter.isAvailable()) {
            return libreOfficeUnavailable();
        }
        DocumentResult docx = engine.generate(templateCode, variables);
        byte[] pdfBytes;
        try {
            pdfBytes = pdfConverter.convert(docx.bytes(), templateCode);
        } catch (LibreOfficeUnavailableException ex) {
            return libreOfficeUnavailable();
        } catch (DocxToPdfConversionException ex) {
            log.error("Conversion DOCX->PDF echouee pour {} : {}", templateCode, ex.getMessage());
            return ResponseEntity.status(500)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(("Conversion PDF echouee : " + ex.getMessage()).getBytes());
        }
        String filename = templateCode + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HDR_TEMPLATE_FOUND, String.valueOf(docx.templateFound()))
                .header(HDR_MISSING_VARS, String.join(",", docx.missingVariables()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Convertit un .docx arbitraire (upload multipart) en PDF FIDELE via LibreOffice.
     * Utile pour l'editeur in-app si on veut le PDF final fidele apres re-generation
     * du .docx style, ou pour tout outil tiers qui poste un .docx.
     */
    @PostMapping(value = "/docx-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> docxToPdf(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!pdfConverter.isAvailable()) {
            return libreOfficeUnavailable();
        }
        String original = file.getOriginalFilename();
        String basename = original == null ? "document"
                : original.replaceAll("\\.[a-zA-Z0-9]{1,5}$", "");
        byte[] pdfBytes;
        try {
            pdfBytes = pdfConverter.convert(file.getBytes(), basename);
        } catch (LibreOfficeUnavailableException ex) {
            return libreOfficeUnavailable();
        } catch (DocxToPdfConversionException ex) {
            log.error("Conversion DOCX->PDF echouee pour upload {} : {}", original, ex.getMessage());
            return ResponseEntity.status(500)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(("Conversion PDF echouee : " + ex.getMessage()).getBytes());
        }
        String filename = basename + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Sonde l'etat du moteur de conversion. Le front interroge cet endpoint
     * pour decider s'il propose le bouton "Telecharger PDF" ou s'il indique
     * "PDF indisponible".
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> status() {
        return Map.of(
                "available", pdfConverter.isAvailable(),
                "engine", "libreoffice-headless",
                "sofficePath", pdfConverter.sofficePath() == null ? "" : pdfConverter.sofficePath(),
                "version", pdfConverter.detectedVersion() == null ? "" : pdfConverter.detectedVersion());
    }

    private ResponseEntity<byte[]> libreOfficeUnavailable() {
        String msg = "LibreOffice non disponible sur le serveur — installer LibreOffice "
                + "ou definir jurika.docx-to-pdf.soffice-path. Le front doit basculer sur "
                + "le telechargement .docx.";
        return ResponseEntity.status(503)
                .header(HDR_LO_UNAVAILABLE, "true")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(msg.getBytes());
    }
}

package ma.jurika.ai.api;

import ma.jurika.ai.document.DocxToPdfConverter;
import ma.jurika.ai.document.DocxToPdfConverter.DocxToPdfConversionException;
import ma.jurika.ai.document.DocxToPdfConverter.LibreOfficeUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lot AB (2026-07-05) — Endpoint INTERNE service-to-service de conversion
 * bureautique -> PDF (LibreOffice headless via {@link DocxToPdfConverter}).
 *
 * <p>Consomme par dataroom-service pour l'apercu inline des Word/Excel (le
 * navigateur ne rend inline que PDF + images). Le download reste servi tel quel.
 *
 * <p><b>Securite</b> : endpoint {@code /internal/**} — permitAll dans
 * {@code SecurityConfig} MAIS <b>non route par la gateway</b> (celle-ci n'expose
 * que {@code /api/v1/ai/**} et {@code /api/v1/chatbot/**} vers ai-service). Il
 * n'est donc atteignable que depuis le reseau interne docker (service-to-service),
 * jamais depuis l'exterieur. Meme posture que les autres endpoints {@code /internal}
 * du projet.
 *
 * <p>Si LibreOffice est absent -> 503 (le client dataroom retombe sur l'original).
 */
@RestController
public class InternalDocumentConversionController {

    private static final Logger log = LoggerFactory.getLogger(InternalDocumentConversionController.class);
    private static final String HDR_LO_UNAVAILABLE = "X-LibreOffice-Unavailable";

    private final DocxToPdfConverter converter;

    public InternalDocumentConversionController(DocxToPdfConverter converter) {
        this.converter = converter;
    }

    /**
     * Convertit un .docx/.xlsx (corps binaire brut) en PDF.
     *
     * @param body     octets du document source
     * @param filename nom d'origine (sert a deriver l'extension source docx/xlsx)
     */
    @PostMapping(value = "/internal/documents/convert-to-pdf",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> convertToPdf(@RequestBody byte[] body,
                                               @RequestParam(defaultValue = "document") String filename) {
        if (body == null || body.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (!converter.isAvailable()) {
            return libreOfficeUnavailable();
        }
        String basename = stripExtension(filename);
        String ext = extensionOf(filename);
        try {
            byte[] pdf = converter.convert(body, basename, ext);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + basename + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (LibreOfficeUnavailableException ex) {
            return libreOfficeUnavailable();
        } catch (DocxToPdfConversionException ex) {
            log.warn("convert-to-pdf : conversion echouee pour {} : {}", filename, ex.getMessage());
            return ResponseEntity.status(415)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(("Conversion PDF impossible : " + ex.getMessage()).getBytes());
        }
    }

    private ResponseEntity<byte[]> libreOfficeUnavailable() {
        String msg = "LibreOffice indisponible sur ce serveur — apercu Word/Excel converti "
                + "impossible. Le client doit retomber sur le document original.";
        return ResponseEntity.status(503)
                .header(HDR_LO_UNAVAILABLE, "true")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(msg.getBytes());
    }

    private static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isBlank() ? "document" : base;
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "docx";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "docx";
        return filename.substring(dot + 1);
    }
}

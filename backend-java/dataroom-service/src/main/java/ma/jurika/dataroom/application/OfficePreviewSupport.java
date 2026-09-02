package ma.jurika.dataroom.application;

import ma.jurika.dataroom.domain.port.AiPdfConversionClient;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.domain.port.ObjectStorage.DownloadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lot AB (2026-07-05) — Support commun d'apercu : convertit a la volee les
 * documents Word/Excel en PDF (via ai-service / LibreOffice) pour qu'ils
 * s'affichent inline dans {@code PdfPreviewModal} (le navigateur ne rend inline
 * que PDF + images). Les PDF/images passent inchanges.
 *
 * <p><b>Cache retenu : MinIO, cle derivee {@code {objectKey}.preview.pdf}.</b>
 * Simple + robuste + persistant (survit aux restarts). Invalidation naturelle :
 * une nouvelle version d'un document = un nouvel {@code objectKey} -> nouvelle
 * cle de cache (l'ancien PDF cache devient orphelin, sans impact fonctionnel).
 *
 * <p><b>Fallback</b> : si la conversion echoue ou que LibreOffice est absent, on
 * renvoie l'ORIGINAL tel quel (le front proposera alors le telechargement).
 * L'apercu ne casse jamais et ne contourne aucune permission (la conversion se
 * fait APRES le controle d'acces, sur les memes octets deja autorises).
 */
@Component
public class OfficePreviewSupport {

    private static final Logger log = LoggerFactory.getLogger(OfficePreviewSupport.class);

    /** Suffixe de la cle de cache MinIO pour le PDF rendu. */
    static final String PREVIEW_SUFFIX = ".preview.pdf";

    private final ObjectStorage storage;
    private final AiPdfConversionClient ai;

    public OfficePreviewSupport(ObjectStorage storage, AiPdfConversionClient ai) {
        this.storage = storage;
        this.ai = ai;
    }

    /** Flux pret a streamer (deja convertit si necessaire) + entetes a poser. */
    public record Rendered(InputStream stream, long size, String contentType, String filename) {}

    /** Vrai si le document est un Word/Excel (par extension du filename, sinon content-type). */
    public boolean isOffice(String contentType, String filename) {
        if (filename != null) {
            String f = filename.toLowerCase();
            if (f.endsWith(".docx") || f.endsWith(".doc")
                    || f.endsWith(".xlsx") || f.endsWith(".xls")
                    || f.endsWith(".odt") || f.endsWith(".ods")) {
                return true;
            }
        }
        if (contentType != null) {
            String c = contentType.toLowerCase();
            return c.contains("wordprocessingml") || c.contains("spreadsheetml")
                    || c.contains("msword") || c.contains("ms-excel")
                    || c.contains("opendocument.text") || c.contains("opendocument.spreadsheet");
        }
        return false;
    }

    /**
     * Charge le document {@code objectKey} pour l'apercu :
     * <ul>
     *   <li>non Office -> flux original inchange ;</li>
     *   <li>Office + cache PDF present -> PDF cache ;</li>
     *   <li>Office sinon -> conversion ai-service, mise en cache, PDF ;</li>
     *   <li>Office + conversion KO -> ORIGINAL en fallback (warn logue).</li>
     * </ul>
     */
    public Rendered render(String objectKey, String filename, String fallbackContentType) {
        if (!isOffice(fallbackContentType, filename)) {
            DownloadResult r = storage.download(objectKey);
            return new Rendered(r.stream(), r.size(), effectiveCt(fallbackContentType, r.contentType()), filename);
        }

        // Office : cache PDF ?
        String cacheKey = objectKey + PREVIEW_SUFFIX;
        byte[] cached = tryReadCache(cacheKey);
        if (cached != null) {
            return pdf(cached, filename);
        }

        // Charger l'original pour conversion.
        byte[] src;
        try (InputStream in = storage.download(objectKey).stream()) {
            src = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            // Lecture impossible : on re-tente un stream brut (comportement historique).
            log.warn("Apercu : lecture original impossible pour {} : {}", objectKey, e.getMessage());
            DownloadResult r = storage.download(objectKey);
            return new Rendered(r.stream(), r.size(), effectiveCt(fallbackContentType, r.contentType()), filename);
        }

        try {
            byte[] out = ai.convertToPdf(src, filename);
            cacheQuietly(cacheKey, out);
            return pdf(out, filename);
        } catch (RuntimeException ex) {
            // LibreOffice absent / ai injoignable / conversion KO -> fallback original.
            log.warn("Apercu : conversion PDF indisponible pour {} ({}), fallback original.",
                    objectKey, ex.getMessage());
            return new Rendered(new ByteArrayInputStream(src), src.length,
                    effectiveCt(fallbackContentType, null), filename);
        }
    }

    private Rendered pdf(byte[] bytes, String filename) {
        return new Rendered(new ByteArrayInputStream(bytes), bytes.length,
                "application/pdf", toPdfName(filename));
    }

    private byte[] tryReadCache(String cacheKey) {
        try (InputStream in = storage.download(cacheKey).stream()) {
            return in.readAllBytes();
        } catch (Exception miss) {
            return null; // cache absent ou illisible -> on convertira
        }
    }

    private void cacheQuietly(String cacheKey, byte[] pdf) {
        try {
            storage.upload(cacheKey, new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        } catch (Exception e) {
            log.debug("Apercu : mise en cache PDF ignoree pour {} : {}", cacheKey, e.getMessage());
        }
    }

    private static String effectiveCt(String preferred, String fromStorage) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fromStorage != null && !fromStorage.isBlank()) return fromStorage;
        return "application/octet-stream";
    }

    private static String toPdfName(String filename) {
        if (filename == null || filename.isBlank()) return "document.pdf";
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base + ".pdf";
    }
}

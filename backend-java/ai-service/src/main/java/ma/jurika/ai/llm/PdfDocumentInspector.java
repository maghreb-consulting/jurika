package ma.jurika.ai.llm;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Helper PDFBox 3.x partagé entre {@link GenericExtractionService} et l'OCR Tesseract.
 * <p>
 * Trois capacités principales :
 * <ul>
 *   <li>{@link #looksLikePdf(byte[], String)} — détection robuste via magic bytes %PDF + fallback extension.</li>
 *   <li>{@link #extractTextLayer(byte[])} — extrait la couche texte d'un PDF numérique (PDFBox PDFTextStripper).
 *       Retourne {@code null} si le PDF est illisible.</li>
 *   <li>{@link #renderFirstPageToPng(byte[], int)} — rasterise la 1ère page en PNG (bytes prêts à envoyer
 *       à un modèle vision). Retourne {@code null} si rendu impossible.</li>
 * </ul>
 * <p>
 * Tout le code est sans état → bean Spring inutile, méthodes statiques utilisées directement.
 */
public final class PdfDocumentInspector {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentInspector.class);

    private PdfDocumentInspector() {
        // utilitaire
    }

    /** Détection robuste : magic bytes "%PDF" (0x25 0x50 0x44 0x46) OU extension .pdf. */
    public static boolean looksLikePdf(byte[] data, String filename) {
        if (data != null && data.length >= 4
                && data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
            return true;
        }
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Extrait la couche texte du PDF via PDFBox.
     *
     * @param pdfBytes bytes du PDF (non null)
     * @return texte brut (peut être vide si PDF scanné) ou {@code null} si lecture impossible.
     */
    public static String extractTextLayer(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) return null;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException | RuntimeException e) {
            log.warn("PDFBox extractTextLayer échec : {}", e.getMessage());
            return null;
        }
    }

    /**
     * Rasterise la 1ère page du PDF en PNG, prêt à être envoyé à un modèle vision.
     *
     * @param pdfBytes bytes du PDF (non null)
     * @param dpi      résolution rendu (recommandé 200-300)
     * @return bytes PNG ou {@code null} si rendu impossible / PDF vide.
     */
    public static byte[] renderFirstPageToPng(byte[] pdfBytes, int dpi) {
        BufferedImage img = renderFirstPage(pdfBytes, dpi);
        if (img == null) return null;
        return toPngBytes(img);
    }

    /**
     * Rasterise la 1ère page du PDF en {@link BufferedImage} (utile pour le pipeline Tesseract aussi).
     */
    public static BufferedImage renderFirstPage(byte[] pdfBytes, int dpi) {
        if (pdfBytes == null || pdfBytes.length == 0) return null;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) return null;
            PDFRenderer renderer = new PDFRenderer(doc);
            // ImageType.RGB pour les modèles vision (Tesseract préfère GRAY mais on raster ici pour vision).
            return renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
        } catch (IOException | RuntimeException e) {
            log.warn("PDFBox renderFirstPage échec : {}", e.getMessage());
            return null;
        }
    }

    /** Encode une {@link BufferedImage} en PNG. Retourne {@code null} si encodage impossible. */
    public static byte[] toPngBytes(BufferedImage img) {
        if (img == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(img, "png", baos)) {
                log.warn("PDFBox toPngBytes — pas de writer PNG disponible");
                return null;
            }
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("PDFBox toPngBytes échec : {}", e.getMessage());
            return null;
        }
    }

    /** Devine le MIME d'une image à partir du nom de fichier (best-effort). */
    public static String guessImageMimeType(String filename) {
        if (filename == null) return "image/png";
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".webp")) return "image/webp";
        if (f.endsWith(".gif")) return "image/gif";
        if (f.endsWith(".tif") || f.endsWith(".tiff")) return "image/tiff";
        return "image/png";
    }
}

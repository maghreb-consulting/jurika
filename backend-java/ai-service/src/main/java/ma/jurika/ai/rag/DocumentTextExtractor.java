package ma.jurika.ai.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Extrait le texte brut d'un document source uploade (PDF / DOCX / TXT) pour
 * alimentation du corpus RAG. Reutilise PDFBox (deja present pour l'OCR) et
 * Apache POI (deja present pour la generation DOCX).
 *
 * <p>Le format est detecte d'abord par le content-type, sinon par l'extension
 * du nom de fichier. Les .txt / .md / format inconnu sont lus en UTF-8.
 */
@Component
public class DocumentTextExtractor {

    /** Type logique resolu pour un upload. */
    public enum Kind { PDF, DOCX, TEXT }

    public String extract(byte[] bytes, String filename, String contentType) {
        if (bytes == null || bytes.length == 0) return "";
        Kind kind = detect(filename, contentType);
        try {
            return switch (kind) {
                case PDF -> extractPdf(bytes);
                case DOCX -> extractDocx(bytes);
                case TEXT -> new String(bytes, StandardCharsets.UTF_8);
            };
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Impossible d'extraire le texte du document (" + kind + ") : " + ex.getMessage(), ex);
        }
    }

    public Kind detect(String filename, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);

        if (ct.contains("pdf") || name.endsWith(".pdf")) return Kind.PDF;
        if (ct.contains("wordprocessingml") || ct.contains("msword")
                || name.endsWith(".docx") || name.endsWith(".doc")) return Kind.DOCX;
        // .txt, .md, text/plain, ou tout autre : lecture texte UTF-8.
        return Kind.TEXT;
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}

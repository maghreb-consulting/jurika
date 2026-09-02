package ma.jurika.ai.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Convertit du HTML (produit par l'éditeur TipTap) en PDF.
 *
 * <p>Migration 2026-07-14 : iText html2pdf (AGPL) retiré. Le rendu passe
 * désormais par LibreOffice headless via {@link DocxToPdfConverter}, qui accepte
 * un format d'entrée arbitraire ({@code soffice --convert-to pdf}) — ici du HTML.
 * LibreOffice est un processus externe : aucune contrainte de licence, aucune
 * dépendance Java ajoutée.
 *
 * <p>Le HTML est enveloppé dans une coque {@code <html>} avec un CSS « cabinet
 * juridique » (Times New Roman, marges, titres colorés) que LibreOffice honore à
 * l'import. Si LibreOffice est absent, {@link DocxToPdfConverter} lève
 * {@link DocxToPdfConverter.LibreOfficeUnavailableException} (mappée en 503 par
 * le contrôleur), et le front bascule sur l'export DOCX.
 */
@Component
public class HtmlToPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(HtmlToPdfConverter.class);

    /** CSS embarqué — qualité dépot RC. Aligné sur MarkdownToPdfConverter. */
    static final String CABINET_CSS = """
            body { font-family: 'Times New Roman', Times, serif; font-size: 11pt; line-height: 1.45; color: #1a1a1a; margin: 2.2cm 2cm; }
            h1 { font-size: 18pt; text-align: center; font-weight: bold; margin: 0 0 0.8cm 0; letter-spacing: 1px; text-transform: uppercase; }
            h2 { font-size: 13pt; color: #1F4E79; font-weight: bold; border-bottom: 1pt solid #1F4E79; padding-bottom: 4pt; margin: 0.6cm 0 0.3cm 0; text-transform: uppercase; }
            h3 { font-size: 11pt; color: #2E75B6; font-weight: bold; font-style: italic; margin: 0.3cm 0 0.15cm 0; }
            h4 { font-size: 10pt; color: #2E75B6; font-weight: bold; margin: 0.2cm 0 0.1cm 0; }
            p { text-align: justify; margin: 0 0 0.25cm 0; }
            ul, ol { margin: 0.15cm 0 0.25cm 0.6cm; padding: 0; }
            li { margin: 0 0 0.1cm 0; }
            blockquote { margin: 0.3cm 0.5cm; padding: 0.2cm 0.4cm; border-left: 3pt solid #b3b3b3; color: #595959; font-style: italic; }
            hr { border: none; border-top: 1pt solid #b3b3b3; margin: 0.4cm 0; }
            table { border-collapse: collapse; width: 100%; margin: 0.3cm 0; }
            th, td { border: 0.5pt solid #999; padding: 0.15cm 0.25cm; vertical-align: top; }
            th { background: #eef2f7; font-weight: bold; }
            strong, b { font-weight: bold; }
            em, i { font-style: italic; }
            u { text-decoration: underline; }
            s, strike, del { text-decoration: line-through; }
            a { color: #1F4E79; text-decoration: underline; }
            """;

    private final DocxToPdfConverter officeConverter;

    public HtmlToPdfConverter(DocxToPdfConverter officeConverter) {
        this.officeConverter = officeConverter;
    }

    /** true si LibreOffice est disponible (donc si l'export PDF est possible). */
    public boolean isAvailable() {
        return officeConverter.isAvailable();
    }

    public byte[] convert(String html, String titreDocument) {
        if (html == null) html = "";
        String title = titreDocument == null ? "Document" : titreDocument;
        String wrapped = wrap(html, title);
        byte[] out = officeConverter.convert(
                wrapped.getBytes(StandardCharsets.UTF_8), title, "html");
        log.info("HtmlToPdfConverter (LibreOffice) : {} chars HTML -> {} bytes PDF",
                html.length(), out.length);
        return out;
    }

    private String wrap(String bodyContent, String title) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>"
                + escapeHtml(title) + "</title><style>" + CABINET_CSS
                + "</style></head><body>" + bodyContent + "</body></html>";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}

package ma.jurika.dataroom.infrastructure.identity;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Fusionne recto + verso (ou recto seul) d'une pièce d'identité en un PDF
 * unique, 1 image par page A4 portrait, centrée et redimensionnée pour tenir
 * dans la zone utile.
 *
 * <p>Migration 2026-07-14 : Apache PDFBox (Apache 2.0) — remplace iText (AGPL).
 */
public final class IdentityPdfBuilder {

    private static final float MARGIN = 36f;

    private IdentityPdfBuilder() {}

    public static byte[] build(byte[] rectoBytes, byte[] versoBytes) {
        if (rectoBytes == null || rectoBytes.length == 0) {
            throw new IllegalArgumentException("Recto manquant");
        }
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            addImagePage(doc, rectoBytes, "recto");
            if (versoBytes != null && versoBytes.length > 0) {
                addImagePage(doc, versoBytes, "verso");
            }
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Echec construction PDF identite : " + ex.getMessage(), ex);
        }
    }

    private static void addImagePage(PDDocument doc, byte[] imageBytes, String name) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageBytes, name);

        float maxWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        float maxHeight = PDRectangle.A4.getHeight() - 2 * MARGIN;
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        if (scale > 1f) scale = 1f; // ne pas agrandir au-dela de la taille native
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float x = (PDRectangle.A4.getWidth() - w) / 2f;
        float y = (PDRectangle.A4.getHeight() - h) / 2f;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, x, y, w, h);
        }
    }
}

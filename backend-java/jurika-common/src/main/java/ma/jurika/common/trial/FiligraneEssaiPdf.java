package ma.jurika.common.trial;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Lot L7 (E4), RG-ESS-06 : filigrane "VERSION D'EVALUATION -- SANS VALEUR
 * JURIDIQUE" sur CHAQUE page d'un PDF affiche pendant l'essai (apercu des
 * actes de la Data Room).
 *
 * <p>Deterministe : memes octets d'entree, memes octets de sortie (identifiant
 * de document /ID derive de l'empreinte de l'entree, aucune date ajoutee).
 */
public final class FiligraneEssaiPdf {

    private FiligraneEssaiPdf() {}

    public static byte[] appliquer(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDExtendedGraphicsState transparence = new PDExtendedGraphicsState();
            transparence.setNonStrokingAlphaConstant(0.22f);
            for (PDPage page : doc.getPages()) {
                PDRectangle cadre = page.getCropBox();
                float largeur = cadre.getWidth();
                float hauteur = cadre.getHeight();
                float diagonale = (float) Math.hypot(largeur, hauteur);
                float corps = Math.max(14f, diagonale / 26f);
                float longueur = police.getStringWidth(EssaiRegles.MENTION_FILIGRANE) / 1000f * corps;
                double angle = Math.atan2(hauteur, largeur);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(transparence);
                    cs.setNonStrokingColor(0.70f, 0.10f, 0.10f);
                    cs.beginText();
                    cs.setFont(police, corps);
                    Matrix m = Matrix.getRotateInstance(angle, cadre.getLowerLeftX() + largeur / 2f,
                            cadre.getLowerLeftY() + hauteur / 2f);
                    m.translate(-longueur / 2f, -corps / 3f);
                    cs.setTextMatrix(m);
                    cs.showText(EssaiRegles.MENTION_FILIGRANE);
                    cs.endText();
                }
            }
            byte[] id = identifiant(pdf);
            COSArray ids = new COSArray();
            ids.add(new COSString(id));
            ids.add(new COSString(id));
            doc.getDocument().getTrailer().setItem(COSName.ID, ids);
            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 4096);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Filigrane de l'essai impossible a poser", e);
        }
    }

    private static byte[] identifiant(byte[] pdf) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(pdf), 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

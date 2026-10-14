package ma.jurika.ai.document;

import ma.jurika.common.docx.ZipHorodatage;
import ma.jurika.common.trial.EssaiRegles;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lot L7 (E4), RG-ESS-06 : tout acte affiche pendant l'essai gratuit porte la
 * mention "VERSION D'EVALUATION -- SANS VALEUR JURIDIQUE" sur chaque page.
 *
 * <p>La mention est ajoutee a CHAQUE en-tete et pied de page du document (ils
 * se repetent sur toutes les pages, y compris la premiere page et les pages
 * paires quand le modele les distingue) ; un document sans en-tete en recoit
 * un. Les en-tetes du cabinet sont conserves (aucun texte retire : modeles
 * intouchables, P8 ; seul le rendu de l'essai est marque).
 *
 * <p>Deterministe : memes octets d'entree, memes octets de sortie (horodatage
 * ZIP fige, comme le moteur).
 */
public final class FiligraneEssaiDocx {

    private FiligraneEssaiDocx() {}

    public static byte[] appliquer(byte[] docx) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFHeader> entetes = new ArrayList<>(doc.getHeaderList());
            List<XWPFFooter> pieds = new ArrayList<>(doc.getFooterList());
            if (entetes.isEmpty()) {
                entetes.add(doc.createHeader(HeaderFooterType.DEFAULT));
            }
            if (pieds.isEmpty()) {
                pieds.add(doc.createFooter(HeaderFooterType.DEFAULT));
            }
            for (XWPFHeader h : entetes) {
                marquer(h.createParagraph());
            }
            for (XWPFFooter f : pieds) {
                marquer(f.createParagraph());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(docx.length + 2048);
            doc.write(out);
            return ZipHorodatage.figer(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Mention de l'essai impossible a poser sur l'acte", e);
        }
    }

    private static void marquer(XWPFParagraph p) {
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setColor("B22222");
        r.setFontSize(10);
        r.setText(EssaiRegles.MENTION_FILIGRANE);
    }
}

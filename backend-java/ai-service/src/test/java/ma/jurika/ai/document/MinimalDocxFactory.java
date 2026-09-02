package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;

/** Helper test : produit un .docx minimal valide via POI a la volee. */
final class MinimalDocxFactory {

    private MinimalDocxFactory() {}

    static byte[] build(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text == null ? "" : text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}

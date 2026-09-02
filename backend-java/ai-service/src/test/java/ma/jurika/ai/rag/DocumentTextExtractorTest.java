package ma.jurika.ai.rag;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void detectsKindByContentTypeThenExtension() {
        assertThat(extractor.detect("x.pdf", null)).isEqualTo(DocumentTextExtractor.Kind.PDF);
        assertThat(extractor.detect("x", "application/pdf")).isEqualTo(DocumentTextExtractor.Kind.PDF);
        assertThat(extractor.detect("statuts.docx", null)).isEqualTo(DocumentTextExtractor.Kind.DOCX);
        assertThat(extractor.detect("note.txt", "text/plain")).isEqualTo(DocumentTextExtractor.Kind.TEXT);
        assertThat(extractor.detect(null, null)).isEqualTo(DocumentTextExtractor.Kind.TEXT);
    }

    @Test
    void extractsPlainTextUtf8() {
        byte[] bytes = "Capital libere a 25% — loi 5-96".getBytes(StandardCharsets.UTF_8);
        String text = extractor.extract(bytes, "note.txt", "text/plain");
        assertThat(text).isEqualTo("Capital libere a 25% — loi 5-96");
    }

    @Test
    void extractsDocxParagraphs() throws Exception {
        byte[] docx = buildDocx("Article 1 : la societe est une SARL.", "Article 2 : le capital est de 100000 MAD.");
        String text = extractor.extract(docx, "statuts.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(text).contains("Article 1").contains("SARL").contains("100000 MAD");
    }

    private byte[] buildDocx(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }
}

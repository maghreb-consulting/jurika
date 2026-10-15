package ma.jurika.ai.document;

import ma.jurika.common.trial.EssaiRegles;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L7 (E4), RG-ESS-06 : mention d'evaluation sur chaque page de l'acte, rendu deterministe. */
class FiligraneEssaiDocxTest {

    private static byte[] docx(boolean avecEntete) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Article 1 - Forme");
            if (avecEntete) {
                doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Cabinet Benali");
                doc.createHeader(HeaderFooterType.FIRST).createParagraph().createRun().setText("Premiere page");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void chaque_entete_et_pied_porte_la_mention_et_le_texte_du_cabinet_reste() throws Exception {
        byte[] marque = FiligraneEssaiDocx.appliquer(docx(true));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(marque))) {
            assertThat(doc.getHeaderList()).hasSize(2);
            for (XWPFHeader h : doc.getHeaderList()) {
                assertThat(h.getText()).contains(EssaiRegles.MENTION_FILIGRANE);
            }
            assertThat(doc.getHeaderList().stream().map(XWPFHeader::getText))
                    .anyMatch(t -> t.contains("Cabinet Benali"))
                    .anyMatch(t -> t.contains("Premiere page"));
            assertThat(doc.getFooterList()).isNotEmpty();
            for (XWPFFooter f : doc.getFooterList()) {
                assertThat(f.getText()).contains(EssaiRegles.MENTION_FILIGRANE);
            }
            assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("Article 1 - Forme");
        }
    }

    @Test
    void document_sans_entete_en_recoit_un() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(FiligraneEssaiDocx.appliquer(docx(false))))) {
            assertThat(doc.getHeaderList()).hasSize(1);
            assertThat(doc.getHeaderList().get(0).getText()).contains("SANS VALEUR JURIDIQUE");
        }
    }

    @Test
    void memes_octets_en_entree_memes_octets_en_sortie() throws Exception {
        byte[] source = docx(true);
        assertThat(FiligraneEssaiDocx.appliquer(source)).isEqualTo(FiligraneEssaiDocx.appliquer(source));
    }
}

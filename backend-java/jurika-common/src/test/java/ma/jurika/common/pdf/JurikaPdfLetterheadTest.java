package ma.jurika.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Papier à en-tête du thème PDF partagé : coordonnées en en-tête, mentions
 * légales en pied, robustesse aux champs vides et à un logo illisible.
 */
class JurikaPdfLetterheadTest {

    private String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private byte[] build(CabinetIdentity id) {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument();
        doc.header(id, "Document de test", "Sujet", null);
        doc.note("Corps du document.");
        return doc.finish();
    }

    @Test
    void en_tete_complet_rend_coordonnees_et_mentions_legales() throws Exception {
        CabinetIdentity id = CabinetIdentity.resolve(null, "Cabinet Alaoui",
                null, null, "12 rue des Consultants, Casablanca", "+212 5 22 00 00 00",
                "contact@cabinet.ma", "www.cabinet.ma", "002345678000089", "RC-45219", "12345678");
        String t = text(build(id));
        assertThat(t).contains("Cabinet Alaoui");
        assertThat(t).contains("12 rue des Consultants");
        assertThat(t).contains("contact@cabinet.ma");
        assertThat(t).contains("ICE : 002345678000089");
        assertThat(t).contains("RC : RC-45219");
        assertThat(t).contains("IF : 12345678");
    }

    @Test
    void tout_vide_ne_garde_que_le_nom_sans_casser() throws Exception {
        String t = text(build(CabinetIdentity.ofName(null, "Cabinet Solo")));
        assertThat(t).contains("Cabinet Solo");
        assertThat(t).contains("Document de test");
        // Aucune mention légale ni ligne de coordonnées.
        assertThat(t).doesNotContain("ICE :");
        assertThat(t).doesNotContain("RC :");
    }

    @Test
    void couche_texte_rend_exactement_les_ligatures() throws Exception {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument();
        doc.header(CabinetIdentity.ofName(null, "Cabinet"), "Document de test", null, null);
        // Titre serif (Playfair) contenant « fi » + corps sans (Inter) avec ff/fl/ffi.
        doc.sectionTitle("1", "Identifiants officiels");
        doc.note("Efficacité, fluidité et affluence des flux financiers.");
        String t = text(doc.finish());
        // Critère d'acceptation : extraction EXACTE des mots à ligature.
        assertThat(t).contains("Identifiants");
        assertThat(t).contains("Efficacité");
        assertThat(t).contains("fluidité");
        assertThat(t).contains("affluence");
        assertThat(t).contains("financiers");
        assertThat(t).doesNotContain("Identiants");
    }

    private int imageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int count = 0;
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null) continue;
                for (COSName name : res.getXObjectNames()) {
                    if (res.isImageXObject(name)) count++;
                }
            }
            return count;
        }
    }

    @Test
    void en_tete_sans_logo_cabinet_embarque_embleme_jurika_par_defaut() throws Exception {
        // Aucun logo cabinet -> l'embleme JURIKA par defaut est embarque dans le PDF.
        byte[] pdf = build(CabinetIdentity.ofName(null, "Cabinet Solo"));
        assertThat(imageCount(pdf)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void logo_illisible_ne_fait_pas_echouer_le_rendu() throws Exception {
        CabinetIdentity id = CabinetIdentity.resolve(null, "Cabinet X",
                new byte[]{1, 2, 3, 4}, "image/png", null, null, null, null, null, null, null);
        String t = text(build(id));
        assertThat(t).contains("Cabinet X"); // le bloc texte est rendu malgré le logo invalide
    }
}

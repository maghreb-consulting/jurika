package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E11c) : PV_AG_SARL du corpus (2026-10-11) se termine par une section de
 * documentation "ANNEXE -- TABLE DE CORRESPONDANCE DES NOMS DE VARIABLES" (anciens noms et
 * nouveaux noms des variables, liste des variables a ajouter au dictionnaire). Elle n'est
 * pas un contenu de l'acte : rendue, elle imprimait 116 variables "manquantes" dans le
 * proces-verbal. Retiree du titre a la fin du corps, comme le dictionnaire des variables ;
 * le gabarit n'est pas modifie.
 */
class TableCorrespondanceRetireeTest {

    @Test
    void la_table_de_correspondance_des_noms_de_variables_n_est_pas_rendue() throws Exception {
        byte[] gabarit;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Fait \u00e0 $ASSEMBLEE_LIEU.");
            doc.createParagraph().createRun().setText(
                    "ANNEXE \u2014 TABLE DE CORRESPONDANCE DES NOMS DE VARIABLES");
            doc.createParagraph().createRun().setText("| $DATE_AG | $ASSEMBLEE_DATE |");
            doc.createParagraph().createRun().setText("Variables nouvelles \u2014 \u00e0 ajouter au dictionnaire");
            doc.createParagraph().createRun().setText("$AG_TYPE \u2014 \u00ab ordinaire \u00bb.");
            doc.write(out);
            gabarit = out.toByteArray();
        }

        DocxTemplateEngine.DocumentResult r = new DocxTemplateEngine()
                .render(gabarit, "PV_AG_SARL.docx", Map.of("ASSEMBLEE_LIEU", "Casablanca"));

        StringBuilder texte = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) texte.append(p.getText()).append('\n');
        }
        assertThat(texte.toString()).contains("Fait \u00e0 Casablanca.")
                .doesNotContain("TABLE DE CORRESPONDANCE").doesNotContain("Variables nouvelles");
        assertThat(r.missingVariables()).isEmpty();
    }
}

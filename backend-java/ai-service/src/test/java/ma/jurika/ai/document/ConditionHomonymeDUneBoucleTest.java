package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E11e) : ecart mecanique du corpus (RAPPORT_GESTION_EXERCICE_SARL(_AU)) -- la
 * condition {@code $EVENEMENTS_POST_CLOTURE = "oui"} et la boucle
 * {@code EVENEMENTS_POST_CLOTURE} portent le meme nom. La valeur est alors la liste de la
 * boucle ; comparee comme texte ("[{...}]"), elle ne valait jamais "oui" et la branche
 * SINON ("Aucun evenement...") s'imprimait en silence, la liste saisie perdue. Une liste
 * vaut desormais "oui" quand elle a des elements, "non" quand elle est vide.
 */
class ConditionHomonymeDUneBoucleTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    private static byte[] gabarit() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String t : new String[] {
                    "\u25c7 SI : $EVENEMENTS = \u00ab oui \u00bb",
                    "Les \u00e9v\u00e9nements suivants sont intervenus :",
                    "\u25bc D\u00c9BUT BOUCLE \u2014 EVENEMENTS",
                    "- $EVENEMENT_LIBELLE",
                    "\u25b2 FIN BOUCLE \u2014 EVENEMENTS",
                    "\u25c7 SINON",
                    "Aucun \u00e9v\u00e9nement.",
                    "\u25c6 FIN SI"}) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(t);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private String rendre(Map<String, Object> vars) throws Exception {
        byte[] r = engine.render(gabarit(), "T.docx", vars).bytes();
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void une_liste_non_vide_vaut_oui_et_la_boucle_s_imprime() throws Exception {
        String t = rendre(Map.of("EVENEMENTS", List.of(Map.of("EVENEMENT_LIBELLE", "perte d'un client"))));
        assertThat(t).contains("Les \u00e9v\u00e9nements suivants").contains("- perte d'un client")
                .doesNotContain("Aucun \u00e9v\u00e9nement");
    }

    @Test
    void une_liste_vide_vaut_non() throws Exception {
        assertThat(rendre(Map.of("EVENEMENTS", List.of()))).contains("Aucun \u00e9v\u00e9nement")
                .doesNotContain("suivants");
    }

    @Test
    void un_texte_se_compare_comme_avant() throws Exception {
        assertThat(rendre(Map.of("EVENEMENTS", "non"))).contains("Aucun \u00e9v\u00e9nement");
    }
}

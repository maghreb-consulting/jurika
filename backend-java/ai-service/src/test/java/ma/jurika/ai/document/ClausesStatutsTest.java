package ma.jurika.ai.document;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E10), decision du directeur du 11 octobre 2026 : clause particuliere des statuts
 * placee apres l'article choisi ; numerotation et renvois internes mis a jour ; refus nomme
 * quand ce n'est pas sur ; rendu deterministe.
 */
class ClausesStatutsTest {

    private static byte[] statuts(String... paragraphes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String t : paragraphes) {
                XWPFParagraph p = doc.createParagraph();
                if (t.startsWith("ARTICLE")) p.setStyle("JurikaTitreArticle");
                if (t.startsWith("TITRE")) p.setStyle("JurikaTitreSection");
                p.createRun().setText(t);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static String texte(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private static final String[] MODELE = {
            "ARTICLE 1 \u2014 FORME", "Il est form\u00e9 une soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e.",
            "ARTICLE 2 \u2014 G\u00c9RANCE", "Par d\u00e9rogation \u00e0 l'article 3 ci-dessus, la g\u00e9rance ...",
            "ARTICLE 3 \u2014 SIGNATURE", "R\u00e9gie par l'article 2 des pr\u00e9sents statuts ; voir l'article 5 de la loi n\u00b0 5-96.",
            "TITRE II \u2014 DISPOSITIONS FINALES"};

    private static Map<String, Object> clause(String apres) {
        return Map.of("titre", "Clause de non-concurrence", "texte", "Les associ\u00e9s s'interdisent ...\nPendant deux ans.",
                "emplacement", "APRES_ARTICLE:" + apres);
    }

    @Test
    void la_clause_devient_un_article_et_les_renvois_suivent() throws Exception {
        byte[] r = ClausesStatuts.inserer("STATUTS_SARL", statuts(MODELE), List.of(clause("1")));
        String t = texte(r);
        assertThat(t).contains("ARTICLE 1 \u2014 FORME").contains("ARTICLE 2 \u2014 CLAUSE DE NON-CONCURRENCE")
                .contains("ARTICLE 3 \u2014 G\u00c9RANCE").contains("ARTICLE 4 \u2014 SIGNATURE")
                .contains("Les associ\u00e9s s'interdisent ...").contains("Pendant deux ans.");
        assertThat(t.indexOf("ARTICLE 2 \u2014 CLAUSE")).isLessThan(t.indexOf("ARTICLE 3 \u2014 G\u00c9RANCE"));
        // Renvois internes decales ; renvoi a un texte de loi inchange.
        assertThat(t).contains("Par d\u00e9rogation \u00e0 l'article 4 ci-dessus")
                .contains("R\u00e9gie par l'article 3 des pr\u00e9sents statuts")
                .contains("l'article 5 de la loi n\u00b0 5-96");
        assertThat(ClausesStatuts.articles(r)).extracting(ClausesStatuts.Article::numero).containsExactly(1, 2, 3, 4);
    }

    @Test
    void apres_le_dernier_article_avant_le_titre_suivant() throws Exception {
        String t = texte(ClausesStatuts.inserer("STATUTS_SARL", statuts(MODELE), List.of(clause("3"))));
        assertThat(t.indexOf("ARTICLE 4 \u2014 CLAUSE")).isGreaterThan(t.indexOf("ARTICLE 3 \u2014 SIGNATURE"))
                .isLessThan(t.indexOf("TITRE II"));
    }

    @Test
    void meme_document_memes_clauses_memes_octets() throws Exception {
        byte[] modele = statuts(MODELE);
        assertThat(ClausesStatuts.inserer("S", modele, List.of(clause("2"))))
                .isEqualTo(ClausesStatuts.inserer("S", modele, List.of(clause("2"))));
    }

    @Test
    void un_renvoi_ambigu_vers_un_article_decale_bloque_avec_message() throws Exception {
        byte[] ambigu = statuts("ARTICLE 1 \u2014 FORME", "x", "ARTICLE 2 \u2014 G\u00c9RANCE", "Voir l'article 2, alin\u00e9a premier.",
                "TITRE II \u2014 FIN");
        assertThatThrownBy(() -> ClausesStatuts.inserer("STATUTS_SARL", ambigu, List.of(clause("1"))))
                .isInstanceOf(ClauseLibreImpossibleException.class)
                .hasMessageContaining("ne dit pas s'il vise un article des statuts");
    }

    @Test
    void emplacement_hors_des_articles_bloque_avec_message() {
        assertThatThrownBy(() -> ClausesStatuts.inserer("STATUTS_SARL", statuts(MODELE), List.of(clause("7"))))
                .isInstanceOf(ClauseLibreImpossibleException.class)
                .hasMessageContaining("1 \u00e0 3");
    }

    /** Les gabarits du corpus du cabinet portent des articles numerotes reperables. */
    @Test
    void les_statuts_du_corpus_ont_des_articles_numerotes() throws Exception {
        Path racine = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
                System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
        Path statuts = racine.resolve("01_CREATION/GABARITS_WORD/STATUTS_SARL.docx");
        Assumptions.assumeTrue(Files.isRegularFile(statuts), "corpus reel absent");
        List<ClausesStatuts.Article> a = ClausesStatuts.articles(Files.readAllBytes(statuts));
        assertThat(a).isNotEmpty();
        assertThat(a.get(0)).isEqualTo(new ClausesStatuts.Article(1, "FORME"));
    }

    /** Sur le modele du cabinet : clause apres l'article 10, renvois internes decales sans refus. */
    @Test
    void sur_les_statuts_du_corpus_les_renvois_internes_suivent() throws Exception {
        Path racine = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
                System.getProperty("user.home") + "/corpus/CORPUS_2026-10-11"));
        Path statuts = racine.resolve("01_CREATION/GABARITS_WORD/STATUTS_SARL.docx");
        Assumptions.assumeTrue(Files.isRegularFile(statuts), "corpus reel absent");
        byte[] r = ClausesStatuts.inserer("STATUTS_SARL", Files.readAllBytes(statuts), List.of(clause("10")));
        // Document a relire (methode du depot : un defaut se voit en ouvrant le fichier).
        Path sortie = Path.of("target", "output", "clauses-statuts");
        Files.createDirectories(sortie);
        Files.write(sortie.resolve("STATUTS_SARL_clause_apres_article_10.docx"), r);
        String t = texte(r);
        assertThat(t).contains("ARTICLE 11 \u2014 CLAUSE DE NON-CONCURRENCE").contains("ARTICLE 40 \u2014 D\u00c9P\u00d4T L\u00c9GAL");
        assertThat(t).contains("Par d\u00e9rogation \u00e0 l'article 15 ci-dessus")
                .contains("r\u00e9gis par l'article 16 des pr\u00e9sents statuts")
                .contains("conform\u00e9ment \u00e0 l'article 15 des pr\u00e9sents statuts")
                .contains("l'article 37 des pr\u00e9sents statuts");
        assertThat(ClausesStatuts.articles(r)).hasSize(40);
    }
}

package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot B (2026-09-11) — UNE CASE À COCHER PLACÉE DANS UNE BOUCLE.
 *
 * <p><b>Le défaut.</b> La passe de cases tournait une fois, au niveau document,
 * <b>avant</b> l'expansion des boucles. Une case dont la valeur est une variable
 * d'item — {@code ◈ CASE À COCHER pilotée par $BE_GENRE}, dans la boucle
 * {@code BENEFICIAIRES_EFFECTIFS} — n'avait donc aucun scope où se lire. La
 * passe ne cochait rien, <b>et consommait le marqueur</b> : la case sortait
 * inerte, « ☐ Masculin ☐ Féminin » sur un bénéficiaire dont le genre était
 * renseigné, sans marqueur résiduel et donc sans alarme.
 *
 * <p>Trouvé en LISANT le document produit — la déclaration des bénéficiaires
 * effectifs du parcours témoin — et non par un compteur. Même famille que le
 * défaut ① du lot A, qui laissait 23 blocs sur 27 inertes.
 *
 * <p><b>Le correctif.</b> La passe document ignore ce qui est entre
 * {@code ▼ DÉBUT BOUCLE} et {@code ▲ FIN BOUCLE} ; l'expansion la rejoue par
 * occurrence, avec le scope de l'item.
 *
 * <p>Le code est PARTAGÉ avec les neuf autres workflows : les deux derniers
 * tests vérifient explicitement qu'une case HORS boucle, et une boucle SANS
 * case, se comportent exactement comme avant.
 */
class CaseACocherDansUneBoucleTest {

    private final DocxTemplateEngine engine = new DocxTemplateEngine();

    private static final String COCHEE = "☒";   // ☒
    private static final String VIDE = "☐";     // ☐

    /** Un document : une ligne par texte fourni, style « ListParagraph » si demandé. */
    private static byte[] docx(List<String[]> lignes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String[] ligne : lignes) {
                XWPFParagraph p = doc.createParagraph();
                if (ligne.length > 1 && ligne[1] != null) p.setStyle(ligne[1]);
                XWPFRun r = p.createRun();
                r.setText(ligne[0]);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static String texte(byte[] docx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        }
        return sb.toString();
    }

    /** Le gabarit de la déclaration des bénéficiaires effectifs, réduit à l'os. */
    private static byte[] gabaritAvecBoucle() throws Exception {
        List<String[]> l = new ArrayList<>();
        l.add(new String[] { "▼ DÉBUT BOUCLE — BENEFICIAIRES", "JurikaBalise" });
        l.add(new String[] { "Nom : $BE_NOM" });
        l.add(new String[] { "Genre :" });
        l.add(new String[] { "◈ CASE À COCHER pilotée par $BE_GENRE", "JurikaBalise" });
        l.add(new String[] { "Masculin", "ListParagraph" });
        l.add(new String[] { "Féminin", "ListParagraph" });
        l.add(new String[] { "▲ FIN BOUCLE — BENEFICIAIRES", "JurikaBalise" });
        return docx(l);
    }

    @Test
    @DisplayName("La case d'une occurrence suit LA DONNÉE DE CETTE OCCURRENCE")
    void laCaseSuitLaDonneeDeLItem() throws Exception {
        byte[] rendu = engine.render(gabaritAvecBoucle(), "be.docx", Map.of(
                "BENEFICIAIRES", List.of(
                        Map.of("BE_NOM", "BENANI", "BE_GENRE", "Masculin"),
                        Map.of("BE_NOM", "ALAMI", "BE_GENRE", "Féminin")))).bytes();

        String texte = texte(rendu);

        // Deux occurrences, chacune avec SA case cochée — et une seule par occurrence.
        assertThat(texte).contains("BENANI").contains("ALAMI");
        assertThat(compter(texte, COCHEE))
                .as("une case cochée par occurrence, pas plus :\n%s", texte)
                .isEqualTo(2);
        assertThat(compter(texte, VIDE))
                .as("l'autre option de chaque occurrence reste vide")
                .isEqualTo(2);

        // Et c'est la BONNE case : « ☒ Masculin » chez le premier, « ☒ Féminin »
        // chez le second. Compter les ☒ ne suffirait pas — deux cases cochées au
        // hasard donneraient le même compte.
        assertThat(texte).contains(COCHEE + " Masculin");
        assertThat(texte).contains(COCHEE + " Féminin");

        // Aucun marqueur ne survit.
        assertThat(texte).doesNotContain("◈").doesNotContain("$BE_GENRE");
    }

    @Test
    @DisplayName("Une occurrence sans valeur ne coche rien, et ne laisse aucun marqueur")
    void occurrenceSansValeur() throws Exception {
        byte[] rendu = engine.render(gabaritAvecBoucle(), "be.docx", Map.of(
                "BENEFICIAIRES", List.of(Map.of("BE_NOM", "BENANI")))).bytes();

        String texte = texte(rendu);

        assertThat(compter(texte, COCHEE)).isZero();
        assertThat(compter(texte, VIDE))
                .as("les deux options sortent, vides : le formulaire reste lisible")
                .isEqualTo(2);
        assertThat(texte).doesNotContain("◈");
    }

    @Test
    @DisplayName("Une boucle VIDE ne laisse ni case, ni marqueur, ni section fantôme")
    void boucleVide() throws Exception {
        byte[] rendu = engine.render(gabaritAvecBoucle(), "be.docx",
                Map.of("BENEFICIAIRES", List.of())).bytes();

        String texte = texte(rendu);

        assertThat(texte).doesNotContain("Masculin").doesNotContain("Genre");
        assertThat(texte).doesNotContain("◈").doesNotContain("▼").doesNotContain("▲");
    }

    // =================================================================
    //  Non-régression — le code est partagé avec les neuf autres workflows
    // =================================================================

    @Test
    @DisplayName("NON-RÉGRESSION — une case HORS boucle coche toujours au niveau document")
    void caseHorsBoucleInchangee() throws Exception {
        byte[] gabarit = docx(List.of(
                new String[] { "Forme juridique :" },
                new String[] { "◈ CASE À COCHER pilotée par $FORME_JURIDIQUE", "JurikaBalise" },
                new String[] { "SARL", "ListParagraph" },
                new String[] { "SARL AU", "ListParagraph" }));

        String texte = texte(engine.render(gabarit, "f.docx",
                Map.of("FORME_JURIDIQUE", "SARL AU")).bytes());

        assertThat(texte).contains(COCHEE + " SARL AU");
        assertThat(texte).contains(VIDE + " SARL");
        assertThat(compter(texte, COCHEE)).isEqualTo(1);
    }

    @Test
    @DisplayName("NON-RÉGRESSION — une boucle SANS case s'expanse exactement comme avant")
    void boucleSansCaseInchangee() throws Exception {
        byte[] gabarit = docx(List.of(
                new String[] { "▼ DÉBUT BOUCLE — ASSOCIES", "JurikaBalise" },
                new String[] { "$ASSOCIE_NOM détient $ASSOCIE_PARTS parts." },
                new String[] { "▲ FIN BOUCLE — ASSOCIES", "JurikaBalise" }));

        String texte = texte(engine.render(gabarit, "a.docx", Map.of(
                "ASSOCIES", List.of(
                        Map.of("ASSOCIE_NOM", "BENANI", "ASSOCIE_PARTS", "600"),
                        Map.of("ASSOCIE_NOM", "ALAMI", "ASSOCIE_PARTS", "400")))).bytes());

        assertThat(texte).contains("BENANI détient 600 parts.");
        assertThat(texte).contains("ALAMI détient 400 parts.");
        assertThat(texte).doesNotContain("$ASSOCIE_NOM");
    }

    private static int compter(String texte, String marqueur) {
        int n = 0;
        int i = texte.indexOf(marqueur);
        while (i >= 0) {
            n++;
            i = texte.indexOf(marqueur, i + 1);
        }
        return n;
    }
}

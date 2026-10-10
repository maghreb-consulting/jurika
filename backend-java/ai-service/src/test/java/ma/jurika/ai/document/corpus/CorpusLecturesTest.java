package ma.jurika.ai.document.corpus;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L2, etape E2 : lecture de l'index et du dictionnaire unique du corpus
 * (corpus FICTIF de test, genere par scripts/l2/fixture_corpus_test.py), et
 * erreurs bloquantes sur des classeurs defectueux.
 */
class CorpusLecturesTest {

    static final Path CORPUS_TEST = Path.of("src/test/resources/corpus-test/CORPUS_TEST");

    @Test
    void lit_l_index_du_corpus() {
        List<ModeleCorpus> modeles = LecteurCorpus.lireIndex(CORPUS_TEST);
        assertThat(modeles).extracting(ModeleCorpus::code)
                .containsExactly("ACTE_TEST_SIMPLE", "PV_TEST_SARL", "PV_TEST_SARL_AU");
        ModeleCorpus sarl = modeles.get(1);
        assertThat(sarl.forme()).isEqualTo("SARL");
        assertThat(sarl.jumeau()).isEqualTo("PV_TEST_SARL_AU");
        assertThat(sarl.modeleBase()).isEqualTo("PV_TEST");
        assertThat(sarl.gabarit()).isEqualTo("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx");
    }

    @Test
    void lit_le_dictionnaire_et_ses_alias() {
        DictionnaireUnique d = LecteurCorpus.lireDictionnaire(CORPUS_TEST);
        assertThat(d.variables()).contains("$DENOMINATION", "$ASSOCIE_NOM").hasSize(8 + ma.jurika.ai.document.ClassementVariables.charger().externesCorpus().size()); // L3 : + externes
        assertThat(d.alias()).containsEntry("$DENOMINATION_SOCIALE", "$DENOMINATION");
        assertThat(d.connue("$DENOMINATION_SOCIALE")).isTrue();
        assertThat(d.connue("$INCONNUE")).isFalse();
    }

    @Test
    void fichier_absent_bloquant(@TempDir Path vide) {
        assertThatThrownBy(() -> LecteurCorpus.lireIndex(vide))
                .isInstanceOf(CorpusException.class).hasMessageContaining("introuvable");
    }

    @Test
    void colonne_absente_bloquante(@TempDir Path racine) throws Exception {
        index(racine, List.of("Famille (dossier)", "Famille", "Mod\u00e8le", "Forme",
                "Mod\u00e8le de base", "Jumeau SARL / SARL AU"), List.of());
        assertThatThrownBy(() -> LecteurCorpus.lireIndex(racine))
                .isInstanceOf(CorpusException.class).hasMessageContaining("colonne absente : Gabarit Word");
    }

    @Test
    void code_en_double_et_gabarit_manquant_bloquants(@TempDir Path racine) throws Exception {
        List<String> entete = List.of("Famille (dossier)", "Famille", "Mod\u00e8le", "Forme",
                "Mod\u00e8le de base", "Jumeau SARL / SARL AU", "Gabarit Word");
        index(racine, entete, List.of(
                List.of("01", "F", "A", "SARL", "A", "-", "01/A.docx"),
                List.of("01", "F", "A", "SARL", "A", "-", "01/A.docx")));
        assertThatThrownBy(() -> LecteurCorpus.lireIndex(racine)).hasMessageContaining("en double : A");
        index(racine, entete, List.of(List.of("01", "F", "B", "SARL", "B", "-", "")));
        assertThatThrownBy(() -> LecteurCorpus.lireIndex(racine)).hasMessageContaining("sans gabarit Word : B");
    }

    @Test
    void alias_vers_variable_inconnue_bloquant(@TempDir Path racine) throws Exception {
        Path f = racine.resolve(LecteurCorpus.DICTIONNAIRE);
        Files.createDirectories(f.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            onglet(wb, "Variables", List.of("Variable", LecteurCorpus.COL_LIBELLE), List.of(List.of("$A", "A")));
            onglet(wb, "Alias", List.of("Alias (nom employ\u00e9 par un mod\u00e8le)", "Nom canonique retenu"),
                    List.of(List.of("$B", "$C")));
            try (OutputStream o = Files.newOutputStream(f)) {
                wb.write(o);
            }
        }
        assertThatThrownBy(() -> LecteurCorpus.lireDictionnaire(racine))
                .isInstanceOf(CorpusException.class).hasMessageContaining("$B vise une variable inconnue : $C");
    }

    private static void index(Path racine, List<String> entete, List<List<String>> lignes) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            onglet(wb, "Mod\u00e8les", entete, lignes);
            try (OutputStream o = Files.newOutputStream(racine.resolve(LecteurCorpus.INDEX))) {
                wb.write(o);
            }
        }
    }

    private static void onglet(XSSFWorkbook wb, String nom, List<String> entete, List<List<String>> lignes) {
        Sheet s = wb.createSheet(nom);
        Row r0 = s.createRow(0);
        for (int i = 0; i < entete.size(); i++) r0.createCell(i).setCellValue(entete.get(i));
        for (int j = 0; j < lignes.size(); j++) {
            Row r = s.createRow(j + 1);
            for (int i = 0; i < lignes.get(j).size(); i++) r.createCell(i).setCellValue(lignes.get(j).get(i));
        }
    }
}

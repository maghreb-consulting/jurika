package ma.jurika.ai.document;

import ma.jurika.ai.document.corpus.CorpusException;
import ma.jurika.ai.document.corpus.DictionnaireUnique;
import ma.jurika.ai.document.corpus.LecteurCorpus;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L3 : regle des variables. La liste des externes est explicite ; chaque nom doit
 * exister (dictionnaire unique, ou texte d'un gabarit du classpath hors corpus).
 */
class ClassementVariablesTest {

    static final java.nio.file.Path CORPUS_TEST = java.nio.file.Path.of("src/test/resources/corpus-test/CORPUS_TEST");

    private final ClassementVariables classement = ClassementVariables.charger();

    @Test
    void identifiants_d_un_organisme_externes_le_reste_interne() {
        for (String externe : new String[]{"ICE", "$RC_NUMERO", "IDENTIFIANT_FISCAL", "IDENTIFIANT_TP",
                "CNSS_NUMERO", "DATE_IMMATRICULATION", "CERTIFICAT_NEGATIF_NUMERO", "DEPOT_LEGAL_NUMERO",
                "DEPOT_ACTES_REFERENCE"}) {
            assertThat(classement.estExterne(externe)).as(externe).isTrue();
        }
        for (String interne : new String[]{"SIEGE_VILLE", "DATE_ACTE", "DENOMINATION", "CAPITAL_CHIFFRES",
                "RC_VILLE", "DEPOT_GREFFE_DATE_LIMITE", "TRIBUNAL_VILLE", "ASSOCIE_NOM"}) {
            assertThat(classement.estExterne(interne)).as(interne).isFalse();
        }
    }

    @Test
    void un_alias_d_une_variable_externe_est_externe() {
        DictionnaireUnique d = new DictionnaireUnique(Set.of("$ICE"), Map.of("$NUMERO_ICE", "$ICE"));
        assertThat(classement.estExterne("NUMERO_ICE")).isFalse();
        assertThat(classement.estExterne("$NUMERO_ICE", d)).isTrue();
    }

    @Test
    void un_nom_inconnu_du_dictionnaire_est_bloquant() {
        DictionnaireUnique sansIce = new DictionnaireUnique(Set.of("$RC_NUMERO"), Map.of());
        assertThatThrownBy(() -> classement.verifierContre(sansIce))
                .isInstanceOf(CorpusException.class).hasMessageContaining("ICE");
    }

    @Test
    void la_liste_versionnee_passe_le_controle_du_dictionnaire_de_test() {
        // Le dictionnaire fictif reprend la liste (scripts/l2/fixture_corpus_test.py) :
        // le controle au demarrage passe dans les tests Spring.
        classement.verifierContre(LecteurCorpus.lireDictionnaire(CORPUS_TEST));
    }

    @Test
    void lecture_stricte_de_la_liste() {
        assertThatThrownBy(() -> ClassementVariables.lire("ICE\n")).hasMessageContaining("hors section");
        assertThatThrownBy(() -> ClassementVariables.lire("[corpus]\n# vide\n")).hasMessageContaining("Aucune");
    }

    @Test
    void chaque_nom_hors_corpus_figure_dans_un_gabarit_du_classpath() throws Exception {
        StringBuilder texte = new StringBuilder();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:templates/docx/*.docx")) {
            try (InputStream in = r.getInputStream(); XWPFDocument doc = new XWPFDocument(in);
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                texte.append(ex.getText()).append('\n');
            }
        }
        assertThat(classement.externesHorsCorpus()).isNotEmpty();
        for (String n : classement.externesHorsCorpus()) {
            assertThat(texte.toString()).as("nom jamais ecrit : " + n).contains(n);
        }
    }

    @Test
    void le_dictionnaire_donne_le_libelle_a_l_ecran() {
        DictionnaireUnique d = LecteurCorpus.lireDictionnaire(CORPUS_TEST);
        assertThat(d.libelle("DENOMINATION")).isEqualTo("Dénomination de la société");
        assertThat(d.libelle("$DENOMINATION_SOCIALE")).as("alias resolu").isEqualTo("Dénomination de la société");
    }
}

package ma.jurika.ai.document.corpus;

import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.GabaritIntrouvableException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L2, etape E4 : le moteur resout d'abord le corpus (empreinte verifiee),
 * puis le classpath pour les seuls codes absents du corpus (signales) ; un
 * gabarit introuvable est une erreur explicite (plus de document de remplacement) ;
 * un alias du dictionnaire recoit la meme valeur que son nom canonique.
 */
class ResolutionCorpusTest {

    private final CorpusCharge corpus = ChargeurCorpus.charger(CorpusLecturesTest.CORPUS_TEST);
    private final DocxTemplateEngine engine = new DocxTemplateEngine(null, null, corpus);

    @Test
    void gabarit_du_corpus_rendu() throws Exception {
        DocumentResult r = engine.generate("PV_TEST_SARL", donnees());
        assertThat(texte(r)).contains("Les associ\u00e9s de ACME SARL se sont r\u00e9unis \u00e0 Rabat.");
        assertThat(r.templateFound()).isTrue();
        assertThat(engine.codesServisHorsCorpus()).isEmpty();
    }

    @Test
    void variante_de_forme_et_alias_recoivent_la_meme_valeur() throws Exception {
        Map<String, Object> d = donnees();
        d.put("formeJuridique", "SARL_AU");
        DocumentResult r = engine.generate("PV_TEST", d);
        // PV_TEST_SARL_AU emploie l'alias $DENOMINATION_SOCIALE ; la donnee est sous $DENOMINATION.
        assertThat(texte(r)).contains("L'associ\u00e9 unique de ACME SARL a d\u00e9cid\u00e9 \u00e0 Rabat.");
        assertThat(r.missingVariables()).isEmpty();
    }

    @Test
    void alias_ne_propage_pas_une_valeur_vide() {
        // Defaut vu sur le temoin du vrai corpus ($VILLE_GREFFE -> $RC_VILLE) : une valeur
        // vide recopiee sous l'autre nom masquait le marqueur de valeur manquante et
        // imprimait un trou dans la phrase (\u00ab registre du commerce de , numero \u00bb).
        Map<String, Object> d = donnees();
        d.put("DENOMINATION", "");
        d.put("formeJuridique", "SARL_AU");
        DocumentResult r = engine.generate("PV_TEST", d);
        assertThat(r.missingVariables()).contains("DENOMINATION_SOCIALE");
    }

    @Test
    void code_absent_du_corpus_servi_par_le_classpath_et_signale() {
        DocumentResult r = engine.generate("ACTE_NOMINATION_GERANT", donnees());
        assertThat(r.bytes()).isNotEmpty();
        assertThat(engine.codesServisHorsCorpus()).containsExactly("ACTE_NOMINATION_GERANT");
    }

    @Test
    void gabarit_introuvable_erreur_explicite_sans_document_de_remplacement() {
        assertThatThrownBy(() -> engine.generate("MODELE_QUI_N_EXISTE_PAS", donnees()))
                .isInstanceOf(GabaritIntrouvableException.class)
                .hasMessageContaining("MODELE_QUI_N_EXISTE_PAS");
        assertThatThrownBy(() -> new DocxTemplateEngine().generate("MODELE_QUI_N_EXISTE_PAS", donnees()))
                .isInstanceOf(GabaritIntrouvableException.class);
    }

    @Test
    void gabarit_modifie_apres_chargement_refuse_au_rendu(@TempDir Path tmp) throws Exception {
        Path racine = CorpusLoaderTest.copie(tmp);
        DocxTemplateEngine e = new DocxTemplateEngine(null, null, ChargeurCorpus.charger(racine));
        CorpusLoaderTest.gabarit(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx"), "Altere.");
        assertThatThrownBy(() -> e.generate("PV_TEST_SARL", donnees()))
                .isInstanceOf(CorpusException.class).hasMessageContaining("modifie depuis le chargement");
    }

    @Test
    void gabarit_non_rendable_refuse_sans_repli_sur_le_classpath(@TempDir Path tmp) throws Exception {
        Path racine = CorpusLoaderTest.copie(tmp);
        CorpusLoaderTest.gabarit(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx"),
                "\u25bc D\u00c9BUT BOUCLE \u2014 ASSOCIES");
        DocxTemplateEngine e = new DocxTemplateEngine(null, null, ChargeurCorpus.charger(racine));
        assertThatThrownBy(() -> e.generate("PV_TEST_SARL", donnees()))
                .isInstanceOf(CorpusException.class).hasMessageContaining("non rendable");
    }

    @Test
    void section_dictionnaire_des_variables_signalee_et_retiree_du_rendu(@TempDir Path tmp) throws Exception {
        // 92 gabarits du corpus 2026-10-03 se terminent par une section de documentation
        // \u00ab DICTIONNAIRE DES VARIABLES \u2014 <CODE> \u00bb : elle ne doit pas s'imprimer dans l'acte.
        Path racine = CorpusLoaderTest.copie(tmp);
        CorpusLoaderTest.gabarit(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx"),
                "Les associ\u00e9s de $DENOMINATION se sont r\u00e9unis \u00e0 $LIEU_SIGNATURE.",
                "Le g\u00e9rant",
                "DICTIONNAIRE DES VARIABLES \u2014 PV_TEST_SARL",
                "$GERANT_NOM \u2014 nom du g\u00e9rant.");
        CorpusCharge c = ChargeurCorpus.charger(racine);
        assertThat(c.gabarit("PV_TEST_SARL").orElseThrow().avertissements())
                .contains("section DICTIONNAIRE DES VARIABLES presente (documentation, retiree au rendu)");
        DocumentResult r = new DocxTemplateEngine(null, null, c).generate("PV_TEST_SARL", donnees());
        String t = texte(r);
        assertThat(t).contains("Les associ\u00e9s de ACME SARL se sont r\u00e9unis \u00e0 Rabat.", "Le g\u00e9rant");
        assertThat(t).doesNotContain("DICTIONNAIRE DES VARIABLES").doesNotContain("nom du g\u00e9rant");
        assertThat(r.missingVariables()).isEmpty();
    }

    private static Map<String, Object> donnees() {
        Map<String, Object> d = new HashMap<>();
        d.put("DENOMINATION", "ACME SARL");
        d.put("LIEU_SIGNATURE", "Rabat");
        return d;
    }

    private static String texte(DocumentResult r) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(r.bytes()));
             XWPFWordExtractor x = new XWPFWordExtractor(doc)) {
            return x.getText();
        }
    }
}

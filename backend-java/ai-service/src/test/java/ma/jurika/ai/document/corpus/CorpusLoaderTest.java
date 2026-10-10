package ma.jurika.ai.document.corpus;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L2, etape E3 : chargement du corpus (corpus FICTIF de test) : empreintes,
 * controles d'integration, rapport, erreurs bloquantes et gabarit non rendable.
 */
class CorpusLoaderTest {

    static final Path CORPUS_TEST = CorpusLecturesTest.CORPUS_TEST;

    @Test
    void corpus_sain_charge_sans_anomalie() {
        CorpusCharge corpus = ChargeurCorpus.charger(CORPUS_TEST);
        assertThat(corpus.version()).isEqualTo("CORPUS_TEST");
        assertThat(corpus.gabarits()).containsOnlyKeys("ACTE_TEST_SIMPLE", "PV_TEST_SARL", "PV_TEST_SARL_AU");
        GabaritCorpus acte = corpus.gabarit("ACTE_TEST_SIMPLE").orElseThrow();
        assertThat(acte.rendable()).isTrue();
        assertThat(acte.avertissements()).isEmpty();
        assertThat(acte.variables()).contains("$DENOMINATION", "$ASSOCIE_NOM", "$GERANT_NOM", "$LIEU_SIGNATURE");
        assertThat(corpus.gabarit("PV_TEST_SARL_AU").orElseThrow().variables()).contains("$DENOMINATION_SOCIALE");

        RapportChargement r = corpus.rapport(List.of("CODE_HORS_CORPUS"));
        assertThat(r.version()).isEqualTo("CORPUS_TEST");
        assertThat(r.modeles()).isEqualTo(3);
        assertThat(r.variables()).isEqualTo(8 + ma.jurika.ai.document.ClassementVariables.charger().externesCorpus().size()); // L3 : + externes
        assertThat(r.alias()).isEqualTo(1);
        assertThat(r.nonRendables()).isEmpty();
        assertThat(r.avertissements()).isEmpty();
        assertThat(r.horsCorpusClasspath()).containsExactly("CODE_HORS_CORPUS");
        assertThat(r.empreintes()).hasSize(3);
    }

    @Test
    void empreinte_est_le_sha256_du_fichier() throws Exception {
        CorpusCharge corpus = ChargeurCorpus.charger(CORPUS_TEST);
        Path fichier = CORPUS_TEST.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx");
        String attendue = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fichier)));
        assertThat(corpus.gabarit("PV_TEST_SARL").orElseThrow().empreinte()).isEqualTo(attendue);
        assertThat(corpus.lireVerifie("PV_TEST_SARL")).isEqualTo(Files.readAllBytes(fichier));
    }

    @Test
    void gabarit_modifie_apres_chargement_refuse(@TempDir Path tmp) throws Exception {
        Path racine = copie(tmp);
        CorpusCharge corpus = ChargeurCorpus.charger(racine);
        gabarit(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx"), "Texte altere $DENOMINATION.");
        assertThatThrownBy(() -> corpus.lireVerifie("PV_TEST_SARL"))
                .isInstanceOf(CorpusException.class).hasMessageContaining("modifie depuis le chargement");
        assertThat(corpus.lireVerifie("PV_TEST_SARL_AU")).isNotEmpty();
    }

    @Test
    void code_absent_du_corpus_refuse() {
        CorpusCharge corpus = ChargeurCorpus.charger(CORPUS_TEST);
        assertThatThrownBy(() -> corpus.lireVerifie("INCONNU"))
                .isInstanceOf(CorpusException.class).hasMessageContaining("absent du corpus");
    }

    @Test
    void boucle_non_fermee_rend_le_gabarit_non_rendable(@TempDir Path tmp) throws Exception {
        Path racine = copie(tmp);
        gabarit(racine.resolve("01_TEST/GABARITS_WORD/ACTE_TEST_SIMPLE.docx"),
                "\u25bc D\u00c9BUT BOUCLE \u2014 ASSOCIES", "Associ\u00e9 : $ASSOCIE_NOM.",
                "\u25c7 SI : $GERANT_UNIQUE = Oui", "\u25c6 FIN SI");
        CorpusCharge corpus = ChargeurCorpus.charger(racine);
        GabaritCorpus g = corpus.gabarit("ACTE_TEST_SIMPLE").orElseThrow();
        assertThat(g.rendable()).isFalse();
        assertThat(g.erreursStructure()).containsExactly("BOUCLE ASSOCIES jamais ferme");
        assertThat(corpus.rapport(List.of()).nonRendables()).containsOnlyKeys("ACTE_TEST_SIMPLE");
        assertThatThrownBy(() -> corpus.lireVerifie("ACTE_TEST_SIMPLE"))
                .isInstanceOf(CorpusException.class).hasMessageContaining("non rendable");
        assertThat(corpus.gabarit("PV_TEST_SARL").orElseThrow().rendable()).isTrue();
    }

    @Test
    void sinon_hors_si_et_fins_croisees_sont_des_erreurs_de_structure() {
        DictionnaireUnique d = LecteurCorpus.lireDictionnaire(CORPUS_TEST);
        ControlesIntegration.Resultat r = ControlesIntegration.controler(List.of(
                "\u25c7 SINON", "\u25bc D\u00c9BUT BOUCLE \u2014 A", "\u25c7 SI : x",
                "\u25b2 FIN BOUCLE \u2014 A", "\u25c6 FIN SI", "\u25c6 FIN SI"), d);
        assertThat(r.erreursStructure()).containsExactly(
                "paragraphe 1 : \u25c7 SINON hors d'un SI",
                "paragraphe 4 : FIN BOUCLE A sans DEBUT correspondant (ouvert : SI)",
                "paragraphe 6 : FIN SI sans SI ouvert",
                "BOUCLE A jamais ferme");
    }

    @Test
    void sinon_si_et_sinon_dans_un_si_sont_valides() {
        DictionnaireUnique d = LecteurCorpus.lireDictionnaire(CORPUS_TEST);
        ControlesIntegration.Resultat r = ControlesIntegration.controler(List.of(
                "\u25c7 SI : $GERANT_UNIQUE = Oui", "a", "\u25c7 SINON SI : $GERANT_UNIQUE = Non", "b",
                "\u25c7 SINON", "c", "\u25c6 FIN SI"), d);
        assertThat(r.erreursStructure()).isEmpty();
        assertThat(r.avertissements()).isEmpty();
    }

    @Test
    void variable_hors_dictionnaire_et_marqueur_residuel_sont_des_avertissements(@TempDir Path tmp) throws Exception {
        Path racine = copie(tmp);
        gabarit(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL.docx"),
                "Soci\u00e9t\u00e9 $DENOMINATION, associ\u00e9 $ASSOCIE_ et ${ANCIEN}.");
        CorpusCharge corpus = ChargeurCorpus.charger(racine);
        GabaritCorpus g = corpus.gabarit("PV_TEST_SARL").orElseThrow();
        assertThat(g.rendable()).isTrue();
        assertThat(g.avertissements()).containsExactly(
                "paragraphe 1 : marqueur residuel \"${\"",
                "variable absente du dictionnaire : $ASSOCIE_");
        assertThat(corpus.rapport(List.of()).avertissements()).containsOnlyKeys("PV_TEST_SARL");
    }

    @Test
    void numero_d_article_romain_n_est_pas_un_marqueur_residuel() {
        // Faux positif releve sur le vrai corpus (DECLARATION_ANNUELLE_PRODUITS_PARTS_SARL).
        DictionnaireUnique d = LecteurCorpus.lireDictionnaire(CORPUS_TEST);
        ControlesIntegration.Resultat r = ControlesIntegration.controler(List.of(
                "article 247-XXXVII-C du code g\u00e9n\u00e9ral des imp\u00f4ts"), d);
        assertThat(r.avertissements()).isEmpty();
    }

    @Test
    void gabarit_absent_bloquant(@TempDir Path tmp) throws Exception {
        Path racine = copie(tmp);
        Files.delete(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL_AU.docx"));
        assertThatThrownBy(() -> ChargeurCorpus.charger(racine))
                .isInstanceOf(CorpusException.class).hasMessageContaining("Gabarit introuvable : PV_TEST_SARL_AU");
    }

    @Test
    void gabarit_illisible_bloquant(@TempDir Path tmp) throws Exception {
        Path racine = copie(tmp);
        Files.writeString(racine.resolve("01_TEST/GABARITS_WORD/PV_TEST_SARL_AU.docx"), "pas un docx");
        assertThatThrownBy(() -> ChargeurCorpus.charger(racine))
                .isInstanceOf(CorpusException.class).hasMessageContaining("Gabarit Word illisible : PV_TEST_SARL_AU");
    }

    @Test
    void racine_absente_bloquante(@TempDir Path tmp) {
        assertThatThrownBy(() -> ChargeurCorpus.charger(tmp.resolve("absent")))
                .isInstanceOf(CorpusException.class).hasMessageContaining("Racine du corpus introuvable");
    }

    @Test
    void le_chargement_ne_modifie_pas_le_corpus() throws Exception {
        Path f = CORPUS_TEST.resolve("01_TEST/GABARITS_WORD/ACTE_TEST_SIMPLE.docx");
        byte[] avant = Files.readAllBytes(f);
        long date = Files.getLastModifiedTime(f).toMillis();
        ChargeurCorpus.charger(CORPUS_TEST);
        assertThat(Files.readAllBytes(f)).isEqualTo(avant);
        assertThat(Files.getLastModifiedTime(f).toMillis()).isEqualTo(date);
    }

    static Path copie(Path cible) throws IOException {
        Path racine = cible.resolve("CORPUS_COPIE");
        try (Stream<Path> s = Files.walk(CORPUS_TEST)) {
            for (Path p : s.toList()) {
                Path d = racine.resolve(CORPUS_TEST.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p, d);
            }
        }
        return racine;
    }

    static void gabarit(Path fichier, String... paragraphes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); OutputStream out = Files.newOutputStream(fichier)) {
            for (String p : paragraphes) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
        }
    }
}

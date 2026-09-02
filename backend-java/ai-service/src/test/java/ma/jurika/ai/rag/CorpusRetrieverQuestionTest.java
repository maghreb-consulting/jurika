package ma.jurika.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalisation d'une question posee en francais naturel.
 *
 * <p>Le defaut corrige ici etait invisible et total : {@code websearch_to_tsquery}
 * relie tous les lexemes par un ET, et la configuration {@code french} de PostgreSQL
 * ne traite pas les interrogatifs comme des mots vides. « <b>Comment</b> creer une
 * SARL ? » exigeait donc qu'un passage contienne le lexeme <i>comment</i> : les
 * quatre suggestions affichees par la page ChatBot elles-memes ne retrouvaient
 * aucun passage.
 */
class CorpusRetrieverQuestionTest {

    @Test
    void lesInterrogatifsSontRetires() {
        assertThat(CorpusRetriever.termesSignificatifs("Comment creer une SARL ?"))
                .containsExactly("creer", "sarl");

        assertThat(CorpusRetriever.termesSignificatifs(
                "Quelle majorite faut-il pour agreer un nouvel associe ?"))
                .doesNotContain("quelle", "faut")
                .contains("majorite", "agreer", "nouvel", "associe");

        assertThat(CorpusRetriever.termesSignificatifs(
                "Que prevoient les statuts en cas de cession de parts a un tiers ?"))
                .doesNotContain("que", "prevoient", "cas")
                .contains("statuts", "cession", "parts", "tiers");
    }

    @Test
    void lesTermesTropCourtsSontEcartes() {
        // « a », « un », « de » n'apportent rien au classement et diluent le ET.
        assertThat(CorpusRetriever.termesSignificatifs("le role de la gerance"))
                .containsExactly("role", "gerance");
    }

    @Test
    void laPonctuationEtLaCasseNeProduisentPasDeTermeParasite() {
        List<String> termes = CorpusRetriever.termesSignificatifs(
                "Cession de parts sociales : consentement des associes ?");
        assertThat(termes).containsExactly("cession", "parts", "sociales", "consentement", "associes");
        assertThat(termes).doesNotContain("des");
        // Aucun caractere susceptible d'etre lu comme un operateur par le moteur FTS.
        assertThat(termes).allSatisfy(t -> assertThat(t).matches("[\\p{L}\\p{N}]+"));
    }

    @Test
    void uneQuestionUniquementInterrogativeNePerdPasTousSesTermes() {
        // Cas limite : si le filtrage vide la liste, le caller repart des termes bruts
        // plutot que de ne rien chercher.
        assertThat(CorpusRetriever.termesSignificatifs("Comment ? Pourquoi ?")).isEmpty();
        assertThat(CorpusRetriever.tousLesTermes("Comment ? Pourquoi ?"))
                .containsExactly("comment", "pourquoi");
    }

    @Test
    void lesAccentsSontConserves() {
        // Le dictionnaire francais de PostgreSQL gere les accents ; les retirer nous
        // ferait perdre la correspondance avec un corpus accentue.
        assertThat(CorpusRetriever.tousLesTermes("Société à responsabilité limitée"))
                .containsExactly("société", "à", "responsabilité", "limitée");
    }
}

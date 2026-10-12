package ma.jurika.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de {@link RagService#ask} (retriever + chat client simules, aucun appel
 * reseau, aucune base). Lot L5 E8, RG-COM-02 : reponse uniquement a partir des sources
 * deposees, avec citation ; sans source pertinente, la reponse le dit et ne cite rien.
 */
class RagServiceLlmTest {

    /** Retriever qui renvoie un chunk fixe (asCitations reste la vraie implementation). */
    private static CorpusRetriever retrieverWithChunk() {
        return new CorpusRetriever((org.springframework.jdbc.core.JdbcTemplate) null) {
            @Override
            public List<Map<String, Object>> searchHybrid(String q, int limit) {
                return List.of(Map.of(
                        "source", "Code de commerce",
                        "article", "Article 46",
                        "chunk_text", "Le capital de la SARL est librement fixé par les associés.",
                        "metadata", Map.of(),
                        "rank", 0.87));
            }
        };
    }

    private static CorpusRetriever retrieverEmpty() {
        return new CorpusRetriever((org.springframework.jdbc.core.JdbcTemplate) null) {
            @Override
            public List<Map<String, Object>> searchHybrid(String q, int limit) {
                return List.of();
            }
        };
    }

    private static RagProperties props(boolean enabled, String key) {
        return new RagProperties(enabled, 5,
                new RagProperties.Embed(null, null, null, null, 0, 0),
                new RagProperties.Chat("gemini", "http://unused/v1", key, "gemini-1.5-flash", 0.2, 30));
    }

    /** Chat client qui renvoie une reponse fixe sans reseau. */
    private static RagChatClient chatClientReturning(String answer, RagProperties p) {
        return new RagChatClient(p, new RestTemplate()) {
            @Override public boolean isReady() { return true; }
            @Override public Optional<String> complete(String system, String user) {
                return Optional.ofNullable(answer);
            }
        };
    }

    /** Chat client qui echoue s'il est appele. */
    private static RagChatClient chatClientInterdit(String raison) {
        return new RagChatClient(props(true, "sk-key"), new RestTemplate()) {
            @Override public boolean isReady() { return true; }
            @Override public Optional<String> complete(String s, String u) {
                throw new AssertionError(raison);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sources(Map<String, Object> res) {
        return (List<Map<String, Object>>) res.get("sources");
    }

    @Test
    void enabledWithKeyAndChunk_returnsLlmAnswerGroundedWithCitation() {
        RagProperties p = props(true, "sk-key");
        RagChatClient chat = chatClientReturning(
                "Le capital de la SARL est fixé librement (Code de commerce, Article 46).", p);
        RagService svc = new RagService(retrieverWithChunk(), chat, true, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?");

        assertThat(res.get("ragMode")).isEqualTo("llm");
        assertThat(String.valueOf(res.get("reponse"))).contains("Article 46");
        assertThat((Integer) res.get("chunksRetrieved")).isEqualTo(1);
        assertThat(sources(res)).hasSize(1);
        assertThat(sources(res).get(0)).containsEntry("reference", "Code de commerce");
        assertThat(sources(res).get(0)).containsEntry("article", "Article 46");
        assertThat(String.valueOf(sources(res).get(0).get("extract"))).contains("capital de la SARL");
        assertThat((Double) res.get("confidence")).isBetween(0.6, 0.95);
    }

    @Test
    void disabled_restitueLesPassagesCitesSansAppelAuModele() {
        RagService svc = new RagService(retrieverWithChunk(),
                chatClientInterdit("complete() ne doit pas etre appele quand rag.enabled=false"), false, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?");

        assertThat(res.get("ragMode")).isEqualTo("extraits");
        // La reponse est le passage depose lui-meme, avec sa reference : rien d'autre.
        assertThat(String.valueOf(res.get("reponse")))
                .contains("Le capital de la SARL est librement fixé par les associés.")
                .contains("Code de commerce");
        assertThat(sources(res)).extracting(s -> s.get("reference")).containsExactly("Code de commerce");
    }

    @Test
    void apiDown_restitueLesPassagesSansPlanter() {
        RagService svc = new RagService(retrieverWithChunk(),
                chatClientReturning(null, props(true, "sk-key")), true, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?");

        assertThat(res.get("ragMode")).isEqualTo("extraits");
        assertThat(String.valueOf(res.get("reponse"))).contains("librement fixé");
        assertThat(sources(res)).isNotEmpty();
        assertThat(String.valueOf(sources(res).get(0).get("extract"))).contains("capital de la SARL");
    }

    /**
     * RG-COM-02 : sans source pertinente, la reponse le dit, ne cite rien et n'appelle pas le
     * modele. Avant le lot L5 E8, une base de connaissances codee en dur repondait et citait
     * "Loi 5-96, Art. 47-49" sans qu'aucune source n'ait ete deposee.
     */
    @Test
    void sansSourcePertinente_laReponseLeDitSansRienInventer() {
        RagService svc = new RagService(retrieverEmpty(),
                chatClientInterdit("Pas de passage -> pas d'appel au modele"), true, 5);

        Map<String, Object> res = svc.ask("Comment creer une SARL au Maroc ?");

        assertThat(res.get("ragMode")).isEqualTo("aucune-source");
        assertThat(res.get("reponse")).isEqualTo(RagService.REPONSE_SANS_SOURCE);
        assertThat(sources(res)).isEmpty();
        assertThat((Integer) res.get("chunksRetrieved")).isZero();
        assertThat((Double) res.get("confidence")).isZero();
    }

    /** RG-COM-04 : dernier controle avant le modele, meme si le controleur est contourne. */
    @Test
    void questionAvecDonneePersonnelle_niRechercheeNiEnvoyee() {
        AtomicBoolean recherche = new AtomicBoolean(false);
        CorpusRetriever espion = new CorpusRetriever((org.springframework.jdbc.core.JdbcTemplate) null) {
            @Override
            public List<Map<String, Object>> searchHybrid(String q, int limit) {
                recherche.set(true);
                return List.of();
            }
        };
        RagService svc = new RagService(espion, chatClientInterdit("question refusee"), true, 5);

        assertThatThrownBy(() -> svc.ask("Le gerant joignable au 0612345678 peut-il signer ?"))
                .isInstanceOf(DonneePersonnelleDetecteeException.class)
                .hasMessageNotContaining("0612345678");
        assertThat(recherche).isFalse();
    }
}

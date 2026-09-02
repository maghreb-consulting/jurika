package ma.jurika.ai.rag;

import ma.jurika.ai.infrastructure.ChatbotKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de la logique de {@link RagService#ask} (retriever + chat client mockés,
 * aucun appel réseau, aucune base). Couvre les trois chemins exigés :
 * <ul>
 *   <li>enabled + clé + chunk -> réponse LLM ancrée + citation correcte (champ {@code extract}) ;</li>
 *   <li>disabled -> repli KB+FTS ({@code ragMode=fts}) ;</li>
 *   <li>API down (client renvoie vide) -> repli KB+FTS sans crash.</li>
 * </ul>
 */
class RagServiceLlmTest {

    private static final UUID WS = UUID.randomUUID();

    /** Retriever qui renvoie un chunk fixe (asCitations reste la vraie implémentation). */
    private static CorpusRetriever retrieverWithChunk() {
        return new CorpusRetriever((org.springframework.jdbc.core.JdbcTemplate) null) {
            @Override
            public List<Map<String, Object>> searchHybrid(String q, int limit, UUID workspaceId) {
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
            public List<Map<String, Object>> searchHybrid(String q, int limit, UUID workspaceId) {
                return List.of();
            }
        };
    }

    private static RagProperties props(boolean enabled, String key) {
        return new RagProperties(enabled, 5,
                new RagProperties.Embed(null, null, null, null, 0, 0),
                new RagProperties.Chat("gemini", "http://unused/v1", key, "gemini-1.5-flash", 0.2, 30));
    }

    /** Chat client qui renvoie une réponse fixe sans réseau. */
    private static RagChatClient chatClientReturning(String answer, RagProperties p) {
        return new RagChatClient(p, new RestTemplate()) {
            @Override public boolean isReady() { return true; }
            @Override public Optional<String> complete(String system, String user) {
                return Optional.ofNullable(answer);
            }
        };
    }

    @Test
    void enabledWithKeyAndChunk_returnsLlmAnswerGroundedWithCitation() {
        RagProperties p = props(true, "sk-key");
        RagChatClient chat = chatClientReturning(
                "Le capital de la SARL est fixé librement (Code de commerce, Article 46).", p);
        RagService svc = new RagService(new ChatbotKnowledgeBase(), retrieverWithChunk(), chat, true, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?", WS);

        assertThat(res.get("ragMode")).isEqualTo("llm");
        assertThat(String.valueOf(res.get("reponse"))).contains("Article 46");
        assertThat((Integer) res.get("chunksRetrieved")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) res.get("sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0)).containsEntry("reference", "Code de commerce");
        assertThat(sources.get(0)).containsEntry("article", "Article 46");
        // Citation correcte : le champ est bien `extract` (non nul, non "undefined").
        assertThat(String.valueOf(sources.get(0).get("extract"))).contains("capital de la SARL");

        // Confiance dérivée du rank (0.87), bornée [0.6, 0.95].
        assertThat((Double) res.get("confidence")).isBetween(0.6, 0.95);
    }

    @Test
    void disabled_fallsBackToKbFtsWithoutLlm() {
        // Client qui exploserait s'il était appelé -> prouve l'absence d'appel quand flag off.
        RagProperties p = props(false, "sk-key");
        RagChatClient chat = new RagChatClient(p, new RestTemplate()) {
            @Override public boolean isReady() { return false; }
            @Override public Optional<String> complete(String s, String u) {
                throw new AssertionError("complete() ne doit pas être appelé quand rag.enabled=false");
            }
        };
        RagService svc = new RagService(new ChatbotKnowledgeBase(), retrieverWithChunk(), chat, false, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?", WS);

        assertThat(res.get("ragMode")).isEqualTo("fts");
        assertThat(String.valueOf(res.get("reponse"))).isNotBlank();
    }

    @Test
    void apiDown_fallsBackToKbFtsWithoutCrash() {
        RagProperties p = props(true, "sk-key");
        RagChatClient chat = chatClientReturning(null, p); // complete() -> Optional.empty()
        RagService svc = new RagService(new ChatbotKnowledgeBase(), retrieverWithChunk(), chat, true, 5);

        Map<String, Object> res = svc.ask("Quel capital pour une SARL ?", WS);

        assertThat(res.get("ragMode")).isEqualTo("fts");
        assertThat(String.valueOf(res.get("reponse"))).isNotBlank();
        // Les chunks récupérés restent cités même en repli.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) res.get("sources");
        assertThat(sources).isNotEmpty();
        assertThat(String.valueOf(sources.get(0).get("extract"))).contains("capital de la SARL");
    }

    @Test
    void enabledButNoChunks_fallsBackToKb() {
        RagProperties p = props(true, "sk-key");
        RagChatClient chat = new RagChatClient(p, new RestTemplate()) {
            @Override public boolean isReady() { return true; }
            @Override public Optional<String> complete(String s, String u) {
                throw new AssertionError("Pas de chunk -> pas d'appel LLM (grounding requis)");
            }
        };
        RagService svc = new RagService(new ChatbotKnowledgeBase(), retrieverEmpty(), chat, true, 5);

        Map<String, Object> res = svc.ask("question sans corpus", WS);

        assertThat(res.get("ragMode")).isEqualTo("fts");
        assertThat((Integer) res.get("chunksRetrieved")).isZero();
    }
}

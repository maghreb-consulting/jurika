package ma.jurika.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests du client d'embeddings (aucun appel reseau reel : MockRestServiceServer).
 * Couvre : parsing data[].embedding, header Bearer + endpoint, batch aligne,
 * et la robustesse (jamais throw) sur cle absente / voie desactivee / erreur HTTP.
 */
class RagEmbeddingClientTest {

    private static final RagProperties.Chat NO_CHAT =
            new RagProperties.Chat(null, null, null, null, -1.0, 0);

    private static RagProperties enabledProps() {
        return new RagProperties(true, 5,
                new RagProperties.Embed("gemini", "https://embed.example/v1", "sk-embed-key",
                        "text-embedding-004", 768, 30),
                NO_CHAT);
    }

    @Test
    void embedBatch_success_parsesVectorsAndCallsEndpointWithBearer() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagEmbeddingClient client = new RagEmbeddingClient(enabledProps(), rt);

        String body = """
                {
                  "data": [
                    { "embedding": [0.1, 0.2, 0.3] },
                    { "embedding": [0.4, 0.5, 0.6] }
                  ]
                }
                """;
        server.expect(requestTo("https://embed.example/v1/embeddings"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-embed-key"))
                .andExpect(jsonPath("$.model").value("text-embedding-004"))
                .andExpect(jsonPath("$.input[0]").value("texte A"))
                .andExpect(jsonPath("$.input[1]").value("texte B"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embedBatch(List.of("texte A", "texte B"));

        server.verify();
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    void embed_single_returnsFirstVector() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagEmbeddingClient client = new RagEmbeddingClient(enabledProps(), rt);

        server.expect(requestTo("https://embed.example/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[1.0,2.0]}]}", MediaType.APPLICATION_JSON));

        Optional<float[]> vec = client.embed("une question");

        server.verify();
        assertThat(vec).isPresent();
        assertThat(vec.get()).containsExactly(1.0f, 2.0f);
    }

    @Test
    void embedBatch_serverError_returnsEmptyNeverThrows() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagEmbeddingClient client = new RagEmbeddingClient(enabledProps(), rt);

        server.expect(requestTo("https://embed.example/v1/embeddings"))
                .andRespond(withServerError().body("upstream down"));

        List<float[]> vectors = client.embedBatch(List.of("texte"));

        server.verify();
        assertThat(vectors).isEmpty();
    }

    @Test
    void embedBatch_missingApiKey_returnsEmptyWithoutHttp() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagProperties noKey = new RagProperties(true, 5,
                new RagProperties.Embed("gemini", "https://embed.example/v1", "",
                        "text-embedding-004", 768, 30),
                NO_CHAT);
        RagEmbeddingClient client = new RagEmbeddingClient(noKey, rt);

        assertThat(client.isReady()).isFalse();
        assertThat(client.embedBatch(List.of("texte"))).isEmpty();
        server.verify(); // aucun appel attendu
    }

    @Test
    void embedBatch_disabledFlag_returnsEmptyWithoutHttp() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagProperties disabled = new RagProperties(false, 5,
                new RagProperties.Embed("gemini", "https://embed.example/v1", "sk-embed-key",
                        "text-embedding-004", 768, 30),
                NO_CHAT);
        RagEmbeddingClient client = new RagEmbeddingClient(disabled, rt);

        assertThat(client.isReady()).isFalse();
        assertThat(client.embedBatch(List.of("texte"))).isEmpty();
        server.verify();
    }

    @Test
    void embed_blankText_returnsEmpty() {
        RagEmbeddingClient client = new RagEmbeddingClient(enabledProps(), new RestTemplate());
        assertThat(client.embed("")).isEmpty();
        assertThat(client.embed("   ")).isEmpty();
    }

    @Test
    void toVectorLiteral_formatsBracketedCsv_andNullForEmpty() {
        assertThat(RagEmbeddingClient.toVectorLiteral(new float[]{0.5f, 1.25f}))
                .isEqualTo("[0.5,1.25]");
        assertThat(RagEmbeddingClient.toVectorLiteral(null)).isNull();
        assertThat(RagEmbeddingClient.toVectorLiteral(new float[]{})).isNull();
    }

    @Test
    void embedBatch_responseWithoutData_returnsEmpty() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagEmbeddingClient client = new RagEmbeddingClient(enabledProps(), rt);

        server.expect(requestTo("https://embed.example/v1/embeddings"))
                .andRespond(withSuccess("{\"object\":\"list\"}", MediaType.APPLICATION_JSON));

        assertThat(client.embedBatch(List.of("texte"))).isEmpty();
    }
}

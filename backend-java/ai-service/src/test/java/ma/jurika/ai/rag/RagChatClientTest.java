package ma.jurika.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests du client de chat RAG (aucun appel reseau reel : MockRestServiceServer).
 * Couvre : texte libre (pas de response_format), Bearer + endpoint, messages system/user,
 * et robustesse (jamais throw) sur cle absente / voie desactivee / erreur HTTP.
 */
class RagChatClientTest {

    private static RagProperties enabledProps() {
        return new RagProperties(true, 5,
                new RagProperties.Embed(null, null, null, null, 0, 0),
                new RagProperties.Chat("gemini", "https://chat.example/v1", "sk-chat-key",
                        "gemini-1.5-flash", 0.2, 30));
    }

    @Test
    void complete_success_returnsContentAndCallsEndpointWithBearer() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagChatClient client = new RagChatClient(enabledProps(), rt);

        String body = """
                { "choices": [ { "message": { "content": "La SARL exige un capital libere a 25%." } } ] }
                """;
        server.expect(requestTo("https://chat.example/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-chat-key"))
                .andExpect(jsonPath("$.model").value("gemini-1.5-flash"))
                .andExpect(jsonPath("$.response_format").doesNotExist()) // TEXTE libre
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("QUESTION: capital ?"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<String> answer = client.complete("Tu es un assistant.", "QUESTION: capital ?");

        server.verify();
        assertThat(answer).contains("La SARL exige un capital libere a 25%.");
    }

    @Test
    void complete_serverError_returnsEmptyNeverThrows() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagChatClient client = new RagChatClient(enabledProps(), rt);

        server.expect(requestTo("https://chat.example/v1/chat/completions"))
                .andRespond(withServerError().body("upstream down"));

        assertThat(client.complete("sys", "user")).isEmpty();
        server.verify();
    }

    @Test
    void complete_missingApiKey_returnsEmptyWithoutHttp() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagProperties noKey = new RagProperties(true, 5,
                new RagProperties.Embed(null, null, null, null, 0, 0),
                new RagProperties.Chat("gemini", "https://chat.example/v1", "",
                        "gemini-1.5-flash", 0.2, 30));
        RagChatClient client = new RagChatClient(noKey, rt);

        assertThat(client.isReady()).isFalse();
        assertThat(client.complete("sys", "user")).isEmpty();
        server.verify(); // aucun appel attendu
    }

    @Test
    void complete_disabledFlag_returnsEmptyWithoutHttp() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagProperties disabled = new RagProperties(false, 5,
                new RagProperties.Embed(null, null, null, null, 0, 0),
                new RagProperties.Chat("gemini", "https://chat.example/v1", "sk-chat-key",
                        "gemini-1.5-flash", 0.2, 30));
        RagChatClient client = new RagChatClient(disabled, rt);

        assertThat(client.isReady()).isFalse();
        assertThat(client.complete("sys", "user")).isEmpty();
        server.verify();
    }

    @Test
    void complete_blankUserPrompt_returnsEmpty() {
        RagChatClient client = new RagChatClient(enabledProps(), new RestTemplate());
        assertThat(client.complete("sys", "")).isEmpty();
        assertThat(client.complete("sys", "   ")).isEmpty();
    }

    @Test
    void complete_responseWithoutChoices_returnsEmpty() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        RagChatClient client = new RagChatClient(enabledProps(), rt);

        server.expect(requestTo("https://chat.example/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.complete("sys", "user")).isEmpty();
    }
}

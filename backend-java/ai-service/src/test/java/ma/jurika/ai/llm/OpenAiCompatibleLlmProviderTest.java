package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;
import ma.jurika.ai.llm.schema.DocumentSchema.FieldDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmProviderTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenAiCompatibleLlmProvider provider;
    private DocumentSchema cinSchema;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        LlmProperties props = new LlmProperties(
                true,
                "groq",
                "https://api.groq.com/openai/v1",
                "sk-test-key",
                "llama-3.1-70b-versatile",
                30,
                0.0,
                "",
                120,
                220,
                60,
                1600
        );
        provider = new OpenAiCompatibleLlmProvider(props, restTemplate);
        cinSchema = new DocumentSchema(
                "CIN",
                "Carte d'identité",
                List.of(
                        new FieldDef("nom", "string", "Nom", true),
                        new FieldDef("prenom", "string", "Prénom", true),
                        new FieldDef("cinNumero", "string", "Numéro CIN", true)
                )
        );
    }

    @Test
    void extract_success_parsesFieldsAndCallsCorrectEndpointWithBearer() {
        String body = """
                {
                  "choices": [
                    { "message": { "content": "{\\"nom\\": \\"BENATIK\\", \\"prenom\\": \\"Oussama\\", \\"cinNumero\\": \\"AB123456\\"}" } }
                  ]
                }
                """;
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andExpect(jsonPath("$.model").value("llama-3.1-70b-versatile"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extract("TEXTE OCR BRUT", cinSchema);

        server.verify();
        assertThat(result.degraded()).isFalse();
        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fields()).containsEntry("nom", "BENATIK");
        assertThat(result.fields()).containsEntry("prenom", "Oussama");
        assertThat(result.fields()).containsEntry("cinNumero", "AB123456");
    }

    @Test
    void extract_serverError_returnsDegraded() {
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withServerError().body("upstream timeout"));

        LlmExtractionResult result = provider.extract("TEXTE", cinSchema);

        server.verify();
        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("500");
        assertThat(result.fields()).isEmpty();
    }

    @Test
    void extract_emptyText_skipsHttpAndReturnsDegraded() {
        LlmExtractionResult result = provider.extract("", cinSchema);
        assertThat(result.degraded()).isTrue();
        // pas d'appel HTTP attendu
        server.verify();
    }

    @Test
    void extract_missingApiKey_returnsDegradedWithoutHttp() {
        LlmProperties noKey = new LlmProperties(true, "groq",
                "https://api.groq.com/openai/v1", "", "llama-3.1-70b-versatile", 30, 0.0,
                "", 120, 220, 60, 1600);
        OpenAiCompatibleLlmProvider noKeyProvider = new OpenAiCompatibleLlmProvider(noKey, restTemplate);

        LlmExtractionResult result = noKeyProvider.extract("TEXTE", cinSchema);

        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("LLM_API_KEY");
        server.verify();
    }

    @Test
    void extract_localProviderOllama_acceptsEmptyKey() {
        LlmProperties ollama = new LlmProperties(true, "ollama",
                "http://localhost:11434/v1", "", "llama3.1", 30, 0.0,
                "", 120, 220, 60, 1600);
        RestTemplate localRt = new RestTemplate();
        MockRestServiceServer localServer = MockRestServiceServer.createServer(localRt);
        OpenAiCompatibleLlmProvider ollamaProvider = new OpenAiCompatibleLlmProvider(ollama, localRt);

        String body = """
                { "choices": [ { "message": { "content": "{\\"nom\\":\\"X\\",\\"prenom\\":\\"Y\\",\\"cinNumero\\":\\"Z1\\"}" } } ] }
                """;
        localServer.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = ollamaProvider.extract("TEXTE", cinSchema);

        localServer.verify();
        assertThat(result.degraded()).isFalse();
        assertThat(result.fields()).containsEntry("nom", "X");
    }

    @Test
    void extract_malformedJson_recoversFromBracesSubstring() {
        // Quelques LLM préfixent par "Voici le JSON :\n{...}" malgré response_format.
        String body = """
                {
                  "choices": [
                    { "message": { "content": "Voici: {\\"nom\\":\\"X\\",\\"prenom\\":\\"Y\\",\\"cinNumero\\":\\"Z1\\"} fin" } }
                  ]
                }
                """;
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extract("TEXTE", cinSchema);

        assertThat(result.degraded()).isFalse();
        assertThat(result.fields()).containsEntry("nom", "X");
    }

    @Test
    void buildSystemPrompt_listsAllFieldsAndMarksRequired() {
        String prompt = OpenAiCompatibleLlmProvider.buildSystemPrompt(cinSchema);
        assertThat(prompt).contains("CIN");
        assertThat(prompt).contains("nom");
        assertThat(prompt).contains("prenom");
        assertThat(prompt).contains("cinNumero");
        assertThat(prompt).contains("OBLIGATOIRE");
        assertThat(prompt).contains("YYYY-MM-DD");
    }

    @Test
    void extract_emptyChoices_returnsDegraded() {
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extract("TEXTE", cinSchema);

        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("choices");
    }
}

package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;
import ma.jurika.ai.llm.schema.DocumentSchema.FieldDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaVisionLlmProviderTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OllamaVisionLlmProvider provider;
    private DocumentSchema cinSchema;
    private final byte[] image = new byte[] {1, 2, 3, 4};

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        LlmProperties props = new LlmProperties(
                true, "ollama", "http://localhost:11434/v1", "",
                "qwen2.5:7b", 30, 0.0,
                "qwen3-vl:2b", 120, 220, 60, 1600
        );
        provider = new OllamaVisionLlmProvider(props, restTemplate);
        cinSchema = new DocumentSchema(
                "CIN", "Carte d'identité",
                List.of(
                        new FieldDef("nom", "string", "Nom", true),
                        new FieldDef("prenom", "string", "Prénom", true),
                        new FieldDef("cinNumero", "string", "Numéro CIN", true)
                )
        );
    }

    @Test
    void isOperational_trueWhenVisionModelSet() {
        assertThat(provider.isOperational()).isTrue();
    }

    @Test
    void extractFromImage_success_parsesFieldsAndBuildsMultimodalMessage() {
        String body = """
                {
                  "choices": [
                    { "message": { "content": "{\\"nom\\": \\"BENATIK\\", \\"prenom\\": \\"Oussama\\", \\"cinNumero\\": \\"AB123456\\"}" } }
                  ]
                }
                """;
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen3-vl:2b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").exists())
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extractFromImage(image, "image/png", cinSchema);

        server.verify();
        assertThat(result.degraded()).isFalse();
        assertThat(result.provider()).isEqualTo("ollama-vision");
        assertThat(result.model()).isEqualTo("qwen3-vl:2b");
        assertThat(result.fields()).containsEntry("nom", "BENATIK");
        assertThat(result.fields()).containsEntry("prenom", "Oussama");
        assertThat(result.fields()).containsEntry("cinNumero", "AB123456");
    }

    @Test
    void extractFromImage_serverError_returnsDegraded() {
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andRespond(withServerError());

        LlmExtractionResult result = provider.extractFromImage(image, "image/png", cinSchema);

        server.verify();
        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("VISION HTTP");
    }

    @Test
    void extractFromImage_emptyBytes_returnsDegradedWithoutHttp() {
        LlmExtractionResult result = provider.extractFromImage(new byte[0], "image/png", cinSchema);

        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("Image vide");
        // Aucun appel HTTP.
        server.verify();
    }

    @Test
    void extractFromImage_missingSchema_returnsDegradedWithoutHttp() {
        LlmExtractionResult result = provider.extractFromImage(image, "image/png", null);

        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("Schéma absent");
    }

    @Test
    void extractFromImage_contentAsListOfParts_parsesText() {
        // Certains modèles Ollama renvoient content sous forme de liste [{type:text,text:"..."}, ...]
        String body = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": [
                          { "type": "text", "text": "{\\"nom\\":\\"X\\",\\"prenom\\":\\"Y\\",\\"cinNumero\\":\\"Z1\\"}" }
                        ]
                      }
                    }
                  ]
                }
                """;
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extractFromImage(image, "image/png", cinSchema);

        assertThat(result.degraded()).isFalse();
        assertThat(result.fields()).containsEntry("nom", "X");
    }

    @Test
    void extractFromImage_jsonMalformed_returnsDegraded() {
        String body = """
                { "choices": [ { "message": { "content": "ceci nest pas du json" } } ] }
                """;
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        LlmExtractionResult result = provider.extractFromImage(image, "image/png", cinSchema);

        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("Parsing");
    }

    @Test
    void isOperational_falseWhenVisionModelEmpty() {
        LlmProperties offProps = new LlmProperties(
                true, "ollama", "http://localhost:11434/v1", "",
                "qwen2.5:7b", 30, 0.0,
                "", 120, 220, 60, 1600
        );
        OllamaVisionLlmProvider off = new OllamaVisionLlmProvider(offProps, restTemplate);
        assertThat(off.isOperational()).isFalse();

        LlmExtractionResult result = off.extractFromImage(image, "image/png", cinSchema);
        assertThat(result.degraded()).isTrue();
        assertThat(result.warning()).contains("non défini");
    }
}

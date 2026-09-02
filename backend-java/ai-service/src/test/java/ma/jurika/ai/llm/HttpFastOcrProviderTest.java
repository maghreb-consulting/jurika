package ma.jurika.ai.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests {@link HttpFastOcrProvider} : contrat HTTP avec le microservice ocr-service.
 * Vérifie le succès, l'erreur 5xx, le timeout simulé, le cache de health.
 */
class HttpFastOcrProviderTest {

    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private FastOcrProperties props(boolean enabled) {
        return new FastOcrProperties(enabled, "http://localhost:8089", 5, "latin", 30);
    }

    @Test
    void extract_success_returnsParsedResult() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        server.expect(requestTo("http://localhost:8089/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"text\":\"BENATIK\\nOussama\",\"engine\":\"doctr\",\"confidence\":0.93,\"ms\":1840,\"totalMs\":2010,\"lang\":\"latin\",\"downscaled\":true,\"lines\":[]}",
                        MediaType.APPLICATION_JSON));

        FastOcrResult r = provider.extract(JPEG, "scan.jpg", "image/jpeg");

        assertThat(r.degraded()).isFalse();
        assertThat(r.text()).contains("BENATIK").contains("Oussama");
        assertThat(r.engine()).isEqualTo("doctr");
        assertThat(r.confidence()).isEqualTo(0.93);
        assertThat(r.ms()).isEqualTo(1840);
        assertThat(r.hasText()).isTrue();
        server.verify();
    }

    @Test
    void extract_serverError_returnsDegraded() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        server.expect(requestTo("http://localhost:8089/ocr"))
                .andRespond(withServerError().body("oom"));

        FastOcrResult r = provider.extract(JPEG, "scan.jpg", "image/jpeg");

        assertThat(r.degraded()).isTrue();
        assertThat(r.warning()).contains("500");
        assertThat(r.hasText()).isFalse();
    }

    @Test
    void extract_emptyBytes_isDegraded() {
        RestTemplate template = new RestTemplate();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        FastOcrResult r = provider.extract(new byte[0], "x.jpg", "image/jpeg");

        assertThat(r.degraded()).isTrue();
        assertThat(r.warning()).contains("Image vide");
    }

    @Test
    void extract_disabledConfig_isDegradedWithoutNetworkCall() {
        RestTemplate template = new RestTemplate();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(
                new FastOcrProperties(false, "http://localhost:8089", 5, "latin", 30),
                template);

        FastOcrResult r = provider.extract(JPEG, "x.jpg", "image/jpeg");

        assertThat(r.degraded()).isTrue();
        assertThat(provider.isOperational()).isFalse();
    }

    @Test
    void extract_malformedJson_isDegraded() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        server.expect(requestTo("http://localhost:8089/ocr"))
                .andRespond(withSuccess("not-a-json", MediaType.APPLICATION_JSON));

        FastOcrResult r = provider.extract(JPEG, "x.jpg", "image/jpeg");

        assertThat(r.degraded()).isTrue();
        assertThat(r.warning()).contains("parsing");
    }

    @Test
    void isOperational_returnsTrueWhenHealthOk() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        server.expect(requestTo("http://localhost:8089/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"UP\",\"engine\":\"doctr\",\"ready\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.isOperational()).isTrue();
        server.verify();
    }

    @Test
    void isOperational_returnsFalseWhenHealthDown() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        server.expect(requestTo("http://localhost:8089/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(provider.isOperational()).isFalse();
    }

    @Test
    void isOperational_isCachedBetweenCalls() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        HttpFastOcrProvider provider = new HttpFastOcrProvider(props(true), template);

        // Une seule réponse health UP — si on appelle 3 fois et le mock ne reçoit qu'1 GET, cache OK.
        server.expect(requestTo("http://localhost:8089/health"))
                .andRespond(withSuccess(
                        "{\"status\":\"UP\",\"engine\":\"doctr\",\"ready\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.isOperational()).isTrue();
        assertThat(provider.isOperational()).isTrue();
        assertThat(provider.isOperational()).isTrue();
        server.verify();
    }
}

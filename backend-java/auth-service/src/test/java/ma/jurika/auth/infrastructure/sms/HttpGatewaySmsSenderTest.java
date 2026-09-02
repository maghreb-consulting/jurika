package ma.jurika.auth.infrastructure.sms;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.jurika.common.observability.BusinessMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du {@link HttpGatewaySmsSender}. Pas de mock HTTP : on monte
 * un mini {@link HttpServer} JDK 21 sur un port ephemère pour vérifier que la
 * requête sortante a la bonne shape (body interpolé, header d'auth posé).
 */
class HttpGatewaySmsSenderTest {

    private BusinessMetrics metrics;
    private HttpServer server;
    private final List<String> recordedBodies = new ArrayList<>();
    private final List<String> recordedAuthHeaders = new ArrayList<>();
    private int responseStatus = 200;
    private String responseBody = "{\"status\":\"ok\"}";

    @BeforeEach
    void setUp() throws IOException {
        metrics = new BusinessMetrics(new SimpleMeterRegistry());
        recordedBodies.clear();
        recordedAuthHeaders.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/send", exchange -> {
            recordedAuthHeaders.add(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] in = exchange.getRequestBody().readAllBytes();
            recordedBodies.add(new String(in, StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/send";
    }

    @Test
    void render_textbeeTemplate_interpolatesRecipientAndMessage() {
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                url(), "x-api-key", "secret-abc",
                "{\"recipients\":[\"{recipient}\"],\"message\":\"{message}\"}",
                5000, metrics);

        String rendered = sender.renderBody("+212600000001", "Code 123456");
        assertThat(rendered).isEqualTo(
                "{\"recipients\":[\"+212600000001\"],\"message\":\"Code 123456\"}");
    }

    @Test
    void render_escapesJsonSpecials() {
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                url(), "", "", null, 5000, metrics);
        // Quote, backslash, newline doivent être echappes.
        String rendered = sender.renderBody("+212600000002", "ligne1\nq\"uote\\back");
        assertThat(rendered).contains("\\n");
        assertThat(rendered).contains("\\\"");
        assertThat(rendered).contains("\\\\");
    }

    @Test
    void send_postsBodyAndAuthHeader_ok() {
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                url(), "x-api-key", "secret-abc",
                "{\"recipients\":[\"{recipient}\"],\"message\":\"{message}\"}",
                5000, metrics);

        sender.send("+212600000001", "Code 654321");

        assertThat(recordedBodies).hasSize(1);
        assertThat(recordedBodies.get(0))
                .contains("+212600000001")
                .contains("Code 654321");
        assertThat(recordedAuthHeaders).containsExactly("secret-abc");
    }

    @Test
    void send_httpError_doesNotThrow() {
        responseStatus = 500;
        responseBody = "{\"error\":\"server down\"}";
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                url(), "x-api-key", "secret-abc", null, 5000, metrics);

        // Ne doit JAMAIS exception — le login 2FA doit pouvoir continuer
        // (TOTP / code recuperation restent dispos).
        sender.send("+212600000001", "Code");
        assertThat(recordedBodies).hasSize(1);
    }

    @Test
    void send_missingUrl_logsAndSkips_noException() {
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                "", "", "", null, 5000, metrics);
        sender.send("+212600000001", "Code");
        assertThat(recordedBodies).isEmpty(); // jamais d'appel sortant
    }

    @Test
    void send_withoutAuthHeader_skipsHeader() {
        HttpGatewaySmsSender sender = new HttpGatewaySmsSender(
                url(), "", "", null, 5000, metrics);
        sender.send("+212600000001", "Code");
        assertThat(recordedBodies).hasSize(1);
        assertThat(recordedAuthHeaders).containsExactly((String) null);
    }
}

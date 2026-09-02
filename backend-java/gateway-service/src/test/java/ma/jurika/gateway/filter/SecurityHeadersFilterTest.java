package ma.jurika.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void appliesAllOwaspHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/tickets"));
        // The chain must commit the response so the beforeCommit callback fires —
        // this mirrors what Spring Cloud Gateway does once the downstream answers.
        GatewayFilterChain chain = e -> e.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        HttpHeaders h = exchange.getResponse().getHeaders();
        assertThat(h.getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=63072000; includeSubDomains; preload");
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(h.getFirst("Permissions-Policy"))
                .isEqualTo("geolocation=(), microphone=(), camera=()");
        assertThat(h.getFirst("Content-Security-Policy"))
                .contains("default-src 'self'")
                .contains("frame-ancestors 'none'")
                .contains("form-action 'self'");
    }

    @Test
    void doesNotOverrideExistingHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");
        GatewayFilterChain chain = e -> e.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
                .isEqualTo("SAMEORIGIN");
    }

    @Test
    void runsBeforeRouting() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    /**
     * Regression for the production stack trace where a downstream POST /signup returned 201 CREATED
     * and the response had been committed by the time SecurityHeadersFilter ran its trailing
     * Mono.fromRunnable, causing headers.set(...) to throw on a ReadOnlyHttpHeaders view.
     */
    @Test
    void doesNotThrowWhenResponseAlreadyCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/public/signup/cabinet"));
        GatewayFilterChain chain = e -> e.getResponse().setComplete();

        // Must not throw — beforeCommit hook fires while headers are still mutable.
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }
}

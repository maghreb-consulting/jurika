package ma.jurika.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void reusesIncomingHeaderAndPropagatesDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/tickets")
                        .header(CorrelationIdWebFilter.HEADER, "abc-123"));

        AtomicReference<ServerWebExchange> seenByDownstream = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seenByDownstream.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo("abc-123");
        assertThat(seenByDownstream.get().getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo("abc-123");
    }

    @Test
    void generatesUuidWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        String generated = exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        assertThat(generated).isNotNull().hasSize(36);
    }

    @Test
    void runsBeforeSecurityHeaders() {
        assertThat(filter.getOrder()).isLessThan(-100);
    }
}

package ma.jurika.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive equivalent of {@code CorrelationIdFilter} for the gateway. Reads (or generates)
 * the {@code X-Correlation-Id} header on incoming requests, propagates it to downstream
 * services via the mutated request, and echoes it back to the client.
 *
 * <p>Runs at order {@code HIGHEST_PRECEDENCE + 10} so it executes before
 * {@code SecurityHeadersFilter} (-100) and {@code JwtClaimsPropagationFilter} (-100).
 */
@Component
public class CorrelationIdWebFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}

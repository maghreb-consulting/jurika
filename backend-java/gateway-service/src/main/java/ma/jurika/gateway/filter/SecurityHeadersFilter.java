package ma.jurika.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds OWASP-recommended security headers to every response.
 *
 * <ul>
 *   <li>HSTS — forces HTTPS (2 years, includeSubDomains, preload).</li>
 *   <li>CSP — restrictive default-src/script-src; WebSocket allowed for realtime.</li>
 *   <li>X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy.</li>
 * </ul>
 *
 * <p>Order {@code -100} runs after {@link JwtClaimsPropagationFilter} but before route
 * forwarding so headers apply to all backend responses.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private static final String CSP =
            "default-src 'self'; " +
            "script-src 'self'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data:; " +
            "connect-src 'self' ws: wss:; " +
            "frame-ancestors 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Register a beforeCommit hook so headers are mutated while still writable.
        // A .then(...) after chain.filter() arrives once the response is already
        // committed (proxied routes stream the body downstream) and getHeaders()
        // becomes a ReadOnlyHttpHeaders → headers.set(...) throws UOE.
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders h = exchange.getResponse().getHeaders();
            putIfAbsent(h, "Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            putIfAbsent(h, "X-Content-Type-Options", "nosniff");
            putIfAbsent(h, "X-Frame-Options", "DENY");
            putIfAbsent(h, "Referrer-Policy", "strict-origin-when-cross-origin");
            putIfAbsent(h, "Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            putIfAbsent(h, "Content-Security-Policy", CSP);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private static void putIfAbsent(HttpHeaders headers, String name, String value) {
        // Defensive: if another filter already swapped to a read-only view, skip
        // rather than crash the response pipeline (header is non-critical).
        if (!headers.containsKey(name)) {
            try {
                headers.set(name, value);
            } catch (UnsupportedOperationException ignored) {
                // Response committed earlier than expected — leave existing headers as-is.
            }
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

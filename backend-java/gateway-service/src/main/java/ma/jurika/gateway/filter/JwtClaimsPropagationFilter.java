package ma.jurika.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import ma.jurika.common.security.JwtPublicKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.security.PublicKey;

import static ma.jurika.gateway.filter.HeaderConstants.*;

@Component
public class JwtClaimsPropagationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtClaimsPropagationFilter.class);
    private static final String BEARER = "Bearer ";

    private final Key verificationKey;

    public JwtClaimsPropagationFilter(JwtPublicKeyProvider keyProvider) {
        this.verificationKey = keyProvider.verificationKey();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER) || verificationKey == null) {
            ServerHttpRequest stripped = request.mutate()
                    .headers(h -> {
                        h.remove(HEADER_USER_ID);
                        h.remove(HEADER_WORKSPACE_ID);
                        h.remove(HEADER_ROLE);
                        h.remove(HEADER_EMAIL);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        try {
            JwtParserBuilder parserBuilder = Jwts.parser();
            if (verificationKey instanceof PublicKey pk) {
                parserBuilder.verifyWith(pk);
            } else if (verificationKey instanceof javax.crypto.SecretKey sk) {
                parserBuilder.verifyWith(sk);
            } else {
                throw new IllegalStateException("Cle de verification JWT inconnue");
            }
            Claims claims = parserBuilder
                    .build()
                    .parseSignedClaims(authHeader.substring(BEARER.length()))
                    .getPayload();

            ServerHttpRequest mutated = request.mutate()
                    .header(HEADER_USER_ID, str(claims, "uid"))
                    .header(HEADER_WORKSPACE_ID, str(claims, "wsid"))
                    .header(HEADER_ROLE, str(claims, "role"))
                    .header(HEADER_EMAIL, str(claims, "email"))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            log.debug("Invalid JWT at gateway: {}", ex.getMessage());
            return chain.filter(exchange);
        }
    }

    private static String str(Claims claims, String key) {
        Object value = claims.get(key);
        return value == null ? "" : value.toString();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

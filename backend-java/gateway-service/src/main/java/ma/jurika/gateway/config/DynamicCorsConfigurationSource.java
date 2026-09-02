package ma.jurika.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive {@link CorsConfigurationSource} backed by a periodically-refreshed allowlist.
 *
 * <p>Seeds the allowlist from {@code jurika.cors.allowed-origins} (bootstrap value used
 * before the first refresh succeeds), then polls
 * {@code GET http://auth-service/api/v1/admin/cors-origins} every {@code refresh-interval}
 * to pick up new entries from the {@code workspace_allowed_origins} table.
 *
 * <p>Origins not in the cache yield a {@link CorsConfiguration} without
 * {@code Access-Control-Allow-Origin}, so the browser blocks the response.
 */
@Component
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicCorsConfigurationSource.class);

    private final WebClient.Builder webClientBuilder;
    private final String authServiceBaseUrl;
    private final AtomicReference<Set<String>> cache = new AtomicReference<>(Set.of());

    public DynamicCorsConfigurationSource(
            WebClient.Builder webClientBuilder,
            @Value("${jurika.cors.allowed-origins:http://localhost:5173}") String bootstrapOrigins,
            @Value("${jurika.cors.auth-service-base-url:http://auth-service:8081}") String authServiceBaseUrl) {
        this.webClientBuilder = webClientBuilder;
        this.authServiceBaseUrl = authServiceBaseUrl;
        Set<String> seed = new HashSet<>(Arrays.asList(bootstrapOrigins.split("\\s*,\\s*")));
        cache.set(Set.copyOf(seed));
    }

    @Override
    public CorsConfiguration getCorsConfiguration(org.springframework.web.server.ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String origin = request.getHeaders().getOrigin();
        CorsConfiguration cfg = baseConfig();
        if (origin != null && cache.get().contains(origin)) {
            cfg.setAllowedOrigins(List.of(origin));
            cfg.setAllowCredentials(true);
        }
        // If the origin is not in the allowlist, we deliberately leave allowedOrigins null
        // so Spring does not emit Access-Control-Allow-Origin -> the browser blocks the response.
        return cfg;
    }

    @Scheduled(fixedDelayString = "${jurika.cors.refresh-interval-ms:300000}",
               initialDelayString = "${jurika.cors.refresh-initial-delay-ms:30000}")
    public void refresh() {
        try {
            List<String> origins = webClientBuilder.build()
                    .get()
                    .uri(authServiceBaseUrl + "/api/v1/admin/cors-origins")
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (origins == null || origins.isEmpty()) {
                log.debug("CORS refresh: empty response from auth-service, keeping previous cache");
                return;
            }
            cache.set(Set.copyOf(origins));
            log.info("CORS allowlist refreshed: {} origins", origins.size());
        } catch (Exception e) {
            log.warn("CORS refresh failed (keeping previous cache): {}", e.getMessage());
        }
    }

    public Set<String> currentAllowlist() {
        return cache.get();
    }

    private static CorsConfiguration baseConfig() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        cfg.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-Requested-With"
        ));
        cfg.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION));
        cfg.setMaxAge(3600L);
        return cfg;
    }
}

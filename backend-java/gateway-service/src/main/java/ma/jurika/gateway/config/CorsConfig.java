package ma.jurika.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.cors.reactive.CorsWebFilter;

/**
 * Wires the reactive CORS filter to {@link DynamicCorsConfigurationSource}, which polls
 * the {@code workspace_allowed_origins} table via auth-service every 5 minutes
 * (cf. TASK 10 / RG-SAAS-01).
 *
 * <p>Origines non whitelistees -> pas de {@code Access-Control-Allow-Origin} -> blocage
 * navigateur.
 */
@Configuration
@EnableScheduling
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(DynamicCorsConfigurationSource source) {
        return new CorsWebFilter(source);
    }
}

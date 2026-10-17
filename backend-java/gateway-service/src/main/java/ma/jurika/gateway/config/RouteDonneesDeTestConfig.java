package ma.jurika.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Route des donnees de test des parcours de bout en bout ({@code /api/v1/test/**} vers le
 * seed de dataroom-service). Decision du 2026-10-17 : elle n'existe qu'en environnement de
 * test. Avant, elle etait declaree dans application.yml, donc presente partout (production
 * comprise) ; elle n'est plus creee que si {@code jurika.test.seed.enabled=true}
 * (JURIKA_TEST_SEED_ENABLED, faux par defaut) et hors profil {@code prod}. Meme double verrou
 * cote dataroom-service (TestSeedController).
 */
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class RouteDonneesDeTestConfig {

    @Bean
    public RouteLocator routeDonneesDeTest(RouteLocatorBuilder routes) {
        return routes.routes()
                .route("test-seed", r -> r.path("/api/v1/test/**").uri("lb://dataroom-service"))
                .build();
    }
}

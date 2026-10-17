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
 * cote dataroom-service (TestSeedController). Recette R3 : memes verrous pour l'envoi des
 * exemplaires de courriels (auth-service et billing-service).
 *
 * <p>Recette R2 (2026-10-17) : la route {@code /api/v1/test/auth-2fa-sms/**} mene a auth-service
 * (dernier code SMS simule de la double authentification, CodeSmsDeTestController, present
 * seulement avec jurika.sms.simulation=true et hors prod). Elle passe avant la route du seed,
 * qui couvre tout {@code /api/v1/test/**}.
 *
 * <p>Recette R1 : {@code /api/v1/test/messages/**} (messages envoyes aux identites de test,
 * MessagesDeTestController d'auth-service, meme double verrou) va a auth-service ; la route est
 * placee avant la route generale vers dataroom.
 */
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class RouteDonneesDeTestConfig {

    @Bean
    public RouteLocator routeDonneesDeTest(RouteLocatorBuilder routes) {
        return routes.routes()
                // Recette R3 : envoi des exemplaires de courriels, vers le service qui envoie ;
                // avant la route generale du seed (ordre plus petit = evaluee d'abord).
                .route("test-courriels-auth", r -> r.order(-1).path("/api/v1/test/courriels/auth")
                        .uri("lb://auth-service"))
                .route("test-courriels-facturation", r -> r.order(-1).path("/api/v1/test/courriels/facturation")
                        .uri("lb://billing-service"))
                .route("test-auth-2fa-sms", r -> r.order(-1).path("/api/v1/test/auth-2fa-sms/**").uri("lb://auth-service"))
                .route("test-messages", r -> r.order(-1).path("/api/v1/test/messages", "/api/v1/test/messages/**")
                        .uri("lb://auth-service"))
                .route("test-seed", r -> r.path("/api/v1/test/**").uri("lb://dataroom-service"))
                .build();
    }
}

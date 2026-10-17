package ma.jurika.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision du 2026-10-17 : la route de donnees de test (/api/v1/test/**) n'existe qu'en
 * environnement de test. Elle n'est plus declaree dans application.yml (toujours chargee) ; la
 * passerelle ne la cree que si jurika.test.seed.enabled=true et hors profil prod.
 */
class RouteDonneesDeTestTest {

    private final ReactiveWebApplicationContextRunner contexte = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, WebFluxAutoConfiguration.class, GatewayAutoConfiguration.class))
            .withPropertyValues("spring.cloud.discovery.enabled=false", "spring.cloud.gateway.discovery.locator.enabled=false")
            .withUserConfiguration(RouteDonneesDeTestConfig.class);

    private static boolean routePresente(RouteLocator routes) {
        return Boolean.TRUE.equals(routes.getRoutes().any(r -> "test-seed".equals(r.getId())).block());
    }

    @Test
    void application_yml_ne_declare_plus_la_route() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(yml).doesNotContain("Path=/api/v1/test/**");
        }
    }

    @Test
    void absente_par_defaut() {
        contexte.run(c -> assertThat(routePresente(c.getBean(RouteLocator.class))).isFalse());
    }

    @Test
    void absente_en_production_meme_interrupteur_leve() {
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class))).isFalse());
    }

    /** Recette R3 : envoi des exemplaires de courriels, vers le service qui envoie. */
    private static java.util.Map<String, String> routesCourriels(RouteLocator routes) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        routes.getRoutes().filter(r -> r.getId().startsWith("test-courriels"))
                .doOnNext(r -> m.put(r.getId(), r.getUri().toString())).blockLast();
        return m;
    }

    @Test
    void routes_des_exemplaires_de_courriels_en_test_seulement() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> {
                    RouteLocator routes = c.getBean(RouteLocator.class);
                    assertThat(routesCourriels(routes))
                            .containsEntry("test-courriels-auth", "lb://auth-service")
                            .containsEntry("test-courriels-facturation", "lb://billing-service");
                    // Avant la route generale /api/v1/test/** (seed de dataroom).
                    java.util.List<String> ordre = routes.getRoutes().map(r -> r.getId()).collectList().block();
                    assertThat(ordre.indexOf("test-courriels-auth")).isLessThan(ordre.indexOf("test-seed"));
                    assertThat(ordre.indexOf("test-courriels-facturation")).isLessThan(ordre.indexOf("test-seed"));
                });
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routesCourriels(c.getBean(RouteLocator.class))).isEmpty());
    }

    @Test
    void presente_en_test_avec_l_interrupteur() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class))).isTrue());
    }
}

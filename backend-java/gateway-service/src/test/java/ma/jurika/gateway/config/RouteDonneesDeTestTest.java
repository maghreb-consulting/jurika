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

    @Test
    void presente_en_test_avec_l_interrupteur() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class))).isTrue());
    }
}

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
        return routePresente(routes, "test-seed");
    }

    private static boolean routePresente(RouteLocator routes, String id) {
        return Boolean.TRUE.equals(routes.getRoutes().any(r -> id.equals(r.getId())).block());
    }

    /**
     * Recette R2 (2026-10-17) : route des codes SMS simules de la double authentification
     * (/api/v1/test/auth-2fa-sms/** vers auth-service), meme double verrou que le seed ; elle passe
     * avant la route du seed (qui couvre tout /api/v1/test/**).
     */
    private static org.springframework.cloud.gateway.route.Route route(RouteLocator routes, String id) {
        return routes.getRoutes().filter(r -> id.equals(r.getId())).blockFirst();
    }

    @Test
    void route_des_codes_sms_de_test_absente_en_production_et_par_defaut() {
        contexte.run(c -> assertThat(routePresente(c.getBean(RouteLocator.class), "test-auth-2fa-sms")).isFalse());
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class), "test-auth-2fa-sms")).isFalse());
    }

    @Test
    void route_des_codes_sms_de_test_vers_auth_avant_le_seed() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true").run(c -> {
            RouteLocator routes = c.getBean(RouteLocator.class);
            var sms = route(routes, "test-auth-2fa-sms");
            assertThat(sms).isNotNull();
            assertThat(sms.getUri().toString()).isEqualTo("lb://auth-service");
            assertThat(sms.getOrder()).isLessThan(route(routes, "test-seed").getOrder());
        });
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
        contexte.run(c -> assertThat(routePresente(c.getBean(RouteLocator.class), "test-messages")).isFalse());
    }

    @Test
    void absente_en_production_meme_interrupteur_leve() {
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class))).isFalse());
        contexte.withPropertyValues("spring.profiles.active=prod", "jurika.test.seed.enabled=true")
                .run(c -> assertThat(routePresente(c.getBean(RouteLocator.class), "test-messages")).isFalse());
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

    /** Recette R1 : les messages des identites de test vont a auth-service, avant la route generale. */
    @Test
    void messages_de_test_vers_auth_avant_la_route_generale() {
        contexte.withPropertyValues("spring.profiles.active=dev", "jurika.test.seed.enabled=true")
                .run(c -> {
                    var routes = c.getBean(RouteLocator.class).getRoutes().collectList().block();
                    var messages = routes.stream().filter(r -> "test-messages".equals(r.getId())).findFirst().orElseThrow();
                    var seed = routes.stream().filter(r -> "test-seed".equals(r.getId())).findFirst().orElseThrow();
                    assertThat(messages.getUri().toString()).isEqualTo("lb://auth-service");
                    assertThat(messages.getOrder()).isLessThan(seed.getOrder());
                });
    }
}

package ma.jurika.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E2) : routage reel de la passerelle (application.yml), dans l'ordre de declaration
 * (premiere route dont un motif Path correspond, comme Spring Cloud Gateway). Garde contre le
 * joker {@code /api/v1/public/**} (vers auth-service) qui capturerait une route publique d'un
 * autre service declaree apres lui (404 silencieux).
 */
class RoutesPasserelleTest {

    private record Route(String id, String uri, List<String> chemins) {}

    @SuppressWarnings("unchecked")
    private static List<Route> routes() throws Exception {
        try (InputStream in = RoutesPasserelleTest.class.getResourceAsStream("/application.yml")) {
            assertThat(in).as("application.yml de la passerelle").isNotNull();
            Map<String, Object> racine = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) racine.get("spring");
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
            List<Map<String, Object>> brutes = (List<Map<String, Object>>) gateway.get("routes");
            List<Route> routes = new ArrayList<>();
            for (Map<String, Object> r : brutes) {
                List<String> chemins = new ArrayList<>();
                for (Object p : (List<Object>) r.get("predicates")) {
                    String predicat = p.toString();
                    if (predicat.startsWith("Path=")) {
                        for (String c : predicat.substring("Path=".length()).split(",")) {
                            chemins.add(c.trim());
                        }
                    }
                }
                routes.add(new Route((String) r.get("id"), (String) r.get("uri"), chemins));
            }
            return routes;
        }
    }

    private static Route routePour(String chemin) throws Exception {
        PathPatternParser parser = new PathPatternParser();
        PathContainer pc = PathContainer.parsePath(chemin);
        for (Route r : routes()) {
            for (String motif : r.chemins()) {
                if (parser.parse(motif).matches(pc)) return r;
            }
        }
        return null;
    }

    @Test
    void les_preferences_du_compte_vont_a_auth_service() throws Exception {
        Route r = routePour("/api/v1/users/me/preferences");
        assertThat(r).isNotNull();
        assertThat(r.uri()).isEqualTo("lb://auth-service");
    }

    @Test
    void les_mentions_legales_publiques_de_l_editeur_vont_a_billing_service() throws Exception {
        Route r = routePour("/api/v1/public/editeur/mentions-legales");
        assertThat(r).as("route des mentions legales").isNotNull();
        assertThat(r.uri()).isEqualTo("lb://billing-service");
    }

    @Test
    void les_autres_routes_publiques_restent_inchangees() throws Exception {
        assertThat(routePour("/api/v1/public/pricing").uri()).isEqualTo("lb://auth-service");
        assertThat(routePour("/api/v1/public/events").uri()).isEqualTo("lb://supervision-service");
        // Seule la lecture des mentions legales est ouverte a billing : rien d'autre sous /editeur.
        assertThat(routePour("/api/v1/public/editeur/autre").uri()).isEqualTo("lb://auth-service");
    }
}

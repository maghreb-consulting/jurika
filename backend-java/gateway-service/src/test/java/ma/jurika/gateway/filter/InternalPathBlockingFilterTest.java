package ma.jurika.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E16d (reponse E1 n° 7) : la passerelle ne route plus aucun
 * {@code /internal/**}. Ces endpoints sont en permitAll dans les services (appels
 * entre services, sans authentification) : exposes par la passerelle, ils etaient
 * joignables de l'exterieur. {@code /api/v1/public/events} (front) reste route.
 */
class InternalPathBlockingFilterTest {

    private final InternalPathBlockingFilter filter = new InternalPathBlockingFilter();

    private boolean passe(String chemin) {
        // URI deja encodee, telle qu'un client l'envoie (post(String) re-encoderait '%').
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.POST, java.net.URI.create(chemin)));
        AtomicBoolean appele = new AtomicBoolean();
        WebFilterChain chain = ex -> {
            appele.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        if (!appele.get()) {
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        return appele.get();
    }

    @Test
    void internal_refuse() {
        assertThat(passe("/internal/events")).isFalse();
        assertThat(passe("/internal/workspaces/x/status")).isFalse();
        assertThat(passe("/internal")).isFalse();
        assertThat(passe("/INTERNAL/events")).isFalse();
        // URI complete : seule, "//internal" serait lu comme un hote par le mock.
        assertThat(passe("http://localhost//internal/events")).isFalse();
        assertThat(passe("/%69nternal/events")).isFalse();
    }

    @Test
    void routes_publiques_inchangees() {
        assertThat(passe("/api/v1/public/events")).isTrue();
        assertThat(passe("/api/v1/tickets")).isTrue();
        assertThat(passe("/internalisation")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aucune_route_de_la_passerelle_ne_vise_internal() {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Map<String, Object> racine = yaml.getObject();
        Map<String, Object> gateway = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                racine.get("spring")).get("cloud")).get("gateway");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");
        assertThat(routes).isNotEmpty();
        for (Map<String, Object> r : routes) {
            for (Object p : (List<Object>) r.get("predicates")) {
                assertThat(String.valueOf(p)).as("route " + r.get("id")).doesNotContain("/internal");
            }
        }
        assertThat(routes).anySatisfy(r -> {
            assertThat(r.get("id")).isEqualTo("supervision-public-events");
            assertThat(String.valueOf(r.get("predicates"))).contains("/api/v1/public/events");
        });
    }
}

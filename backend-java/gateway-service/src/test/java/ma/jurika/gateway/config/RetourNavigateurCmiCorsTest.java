package ma.jurika.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E16, parcours du paiement par carte) : le retour du navigateur depuis la page de
 * paiement (okUrl / failUrl, formulaire POST soumis par la page du CMI) arrive avec l'origine du
 * CMI, ou "null" (page en Referrer-Policy no-referrer, cas du simulateur). Le filtre CORS de la
 * passerelle refusait toute origine hors liste par un 403 : le paiement accepte ne revenait
 * jamais sur JURIKA. Ces navigations ne lisent aucune reponse par script : CORS ne les concerne
 * pas. Les routes de l'API restent refusees a une origine hors liste.
 */
class RetourNavigateurCmiCorsTest {

    private static final String FRONT = "http://192.168.1.251";

    private boolean passe(String chemin, String origine) {
        DynamicCorsConfigurationSource source =
                new DynamicCorsConfigurationSource(WebClient.builder(), FRONT, "http://auth-service:8081");
        CorsWebFilter filtre = new CorsConfig().corsWebFilter(source);
        MockServerHttpRequest req = MockServerHttpRequest.post("http://192.168.1.251:8080" + chemin)
                .header("Origin", origine)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        AtomicBoolean suite = new AtomicBoolean(false);
        WebFilterChain chaine = e -> {
            suite.set(true);
            return Mono.empty();
        };
        filtre.filter(ex, chaine).block();
        return suite.get() && ex.getResponse().getStatusCode() != HttpStatus.FORBIDDEN;
    }

    @Test
    void retour_du_cmi_depuis_son_origine_n_est_pas_refuse() {
        assertThat(passe("/api/v1/billing/cmi/retour/ok", "https://payment.cmi.co.ma")).isTrue();
        assertThat(passe("/api/v1/billing/cmi/retour/echec", "https://payment.cmi.co.ma")).isTrue();
    }

    @Test
    void decision_du_simulateur_avec_origine_null_n_est_pas_refusee() {
        assertThat(passe("/api/v1/billing/cmi-simulateur/decision", "null")).isTrue();
    }

    @Test
    void une_route_de_l_api_reste_refusee_a_une_origine_hors_liste() {
        assertThat(passe("/api/v1/billing/subscriptions", "https://payment.cmi.co.ma")).isFalse();
        assertThat(passe("/api/v1/billing/cmi-simulateur-autre", "null")).isFalse();
    }
}

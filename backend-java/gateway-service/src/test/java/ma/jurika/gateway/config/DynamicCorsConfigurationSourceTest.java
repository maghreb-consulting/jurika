package ma.jurika.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E16, parcours de l'essai gratuit) : le formulaire de l'essai envoie l'empreinte de
 * l'appareil dans l'en-tete X-Jurika-Appareil (anti-abus, RG-ESS). La passerelle ne l'acceptait
 * pas au controle prealable CORS : depuis l'origine du front, le navigateur bloquait la demande
 * d'essai (net::ERR_FAILED), aucun essai ne pouvait etre demande.
 */
class DynamicCorsConfigurationSourceTest {

    private static final String FRONT = "http://192.168.1.251";

    private CorsConfiguration preflight(String... entetes) {
        DynamicCorsConfigurationSource source =
                new DynamicCorsConfigurationSource(WebClient.builder(), FRONT, "http://auth-service:8081");
        MockServerHttpRequest req = MockServerHttpRequest.options("/api/v1/public/essai/demandes")
                .header("Origin", FRONT)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", String.join(",", entetes))
                .build();
        return source.getCorsConfiguration(MockServerWebExchange.from(req));
    }

    @Test
    void l_en_tete_de_l_appareil_de_l_essai_est_autorise() {
        CorsConfiguration cfg = preflight("content-type", "x-jurika-appareil");
        assertThat(cfg.checkOrigin(FRONT)).isEqualTo(FRONT);
        assertThat(cfg.checkHttpMethod(HttpMethod.POST)).isNotNull();
        assertThat(cfg.checkHeaders(List.of("content-type", "x-jurika-appareil")))
                .as("en-tetes acceptes au controle prealable")
                .isNotNull()
                .hasSize(2);
    }

    @Test
    void un_en_tete_inconnu_reste_refuse() {
        CorsConfiguration cfg = preflight("x-inconnu");
        assertThat(cfg.checkHeaders(List.of("x-inconnu"))).isNull();
    }
}

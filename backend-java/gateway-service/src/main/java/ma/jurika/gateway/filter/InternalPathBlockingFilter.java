package ma.jurika.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lot L0, etape E16d : la passerelle refuse tout {@code /internal/**} (404).
 *
 * <p>Les endpoints {@code /internal/**} des services sont en permitAll : ils
 * servent aux appels entre services, sur le reseau Docker, sans authentification.
 * La passerelle est la porte d'entree publique : elle ne doit jamais les exposer.
 * Le filtre s'execute AVANT la correspondance des routes et la securite, sur le
 * chemin d'ORIGINE de la requete : la route publique {@code /api/v1/public/events}
 * (reecrite ensuite vers {@code /internal/events} de supervision) n'est pas
 * concernee. Chemin decode, en minuscules, barres obliques repetees fusionnees.
 */
@Component
public class InternalPathBlockingFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (estInterne(exchange.getRequest().getPath().pathWithinApplication().value())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    static boolean estInterne(String brut) {
        String chemin = UriUtils.decode(brut, StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .replaceAll("/{2,}", "/");
        return chemin.equals("/internal") || chemin.startsWith("/internal/");
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}

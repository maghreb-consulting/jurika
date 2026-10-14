package ma.jurika.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static ma.jurika.gateway.filter.HeaderConstants.HEADER_ROLE;
import static ma.jurika.gateway.filter.HeaderConstants.HEADER_WORKSPACE_ID;

/**
 * Lot L7 (E6, RG-CTR-03) : tant que le cabinet a un contrat d'abonnement en attente
 * d'acceptation, ses superviseurs et employes n'accedent qu'a l'ecran du contrat. Toute autre
 * route du cabinet est refusee par la passerelle (423, code {@code CONTRAT_EN_ATTENTE}) ; le
 * controle est cote serveur (P6), l'ecran ne fait que rediriger.
 *
 * <p>Passent : la session (connexion, double authentification, rafraichissement, profil),
 * l'identite du cabinet (a completer avant l'acceptation), le contrat lui-meme et les routes
 * publiques. Le super-admin et les clients du cabinet ne sont pas concernes.
 *
 * <p>L'etat vient de billing-service ({@code GET /internal/contrat/workspaces/{id}/acces}),
 * garde 15 secondes. Si billing ne repond pas, la derniere reponse connue s'applique ; sans
 * reponse connue, la requete est refusee (503, {@code CONTRAT_VERIFICATION_INDISPONIBLE}) :
 * le controle n'est jamais saute en silence.
 */
@Component
public class ContratEnAttenteFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ContratEnAttenteFilter.class);

    /** Apres JwtClaimsPropagationFilter (-100), qui pose le role et le workspace verifies. */
    public static final int ORDER = -90;
    static final Duration VALIDITE = Duration.ofSeconds(15);

    static final List<String> ROUTES_PERMISES = List.of(
            "/api/v1/billing/contrat/**",
            "/api/v1/public/**",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/workspace-check",
            "/api/v1/auth/verify-2fa",
            "/api/v1/auth/verify-recovery-code",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/password-reset/**",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            "/api/v1/auth/me/**",
            "/api/v1/auth/setup-2fa",
            "/api/v1/auth/setup-2fa/**",
            "/api/v1/auth/2fa/**",
            "/api/v1/auth/change-password",
            "/api/v1/users/me/**",
            "/api/v1/workspace/profile",
            "/api/v1/workspace/profile/**");

    private final Function<UUID, Mono<Boolean>> verification;
    private final Map<UUID, Etat> cache = new ConcurrentHashMap<>();
    private final AntPathMatcher chemins = new AntPathMatcher();

    private record Etat(boolean enAttente, Instant lu) {}

    @Autowired
    public ContratEnAttenteFilter(WebClient.Builder webClientBuilder,
                                  @Value("${jurika.contrat.billing-base-url:http://billing-service:8090}") String billingUrl) {
        WebClient client = webClientBuilder.baseUrl(billingUrl).build();
        this.verification = ws -> client.get()
                .uri("/internal/contrat/workspaces/{id}/acces", ws)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> Boolean.TRUE.equals(m.get("enAttente")))
                .timeout(Duration.ofSeconds(3));
    }

    /** Tests : verification fournie. */
    ContratEnAttenteFilter(Function<UUID, Mono<Boolean>> verification) {
        this.verification = verification;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String role = exchange.getRequest().getHeaders().getFirst(HEADER_ROLE);
        String wsBrut = exchange.getRequest().getHeaders().getFirst(HEADER_WORKSPACE_ID);
        if (!("SUPERVISEUR".equals(role) || "EMPLOYE".equals(role)) || wsBrut == null || wsBrut.isBlank()
                || permise(exchange.getRequest().getPath().pathWithinApplication().value())) {
            return chain.filter(exchange);
        }
        UUID ws;
        try {
            ws = UUID.fromString(wsBrut);
        } catch (IllegalArgumentException ex) {
            return chain.filter(exchange);
        }
        return verdict(ws).flatMap(v -> switch (v) {
            case INCONNU -> refuser(exchange, HttpStatus.SERVICE_UNAVAILABLE, "CONTRAT_VERIFICATION_INDISPONIBLE",
                    "L\u2019\u00e9tat du contrat d\u2019abonnement n\u2019a pas pu \u00eatre v\u00e9rifi\u00e9. "
                            + "R\u00e9essayez dans quelques instants.");
            case EN_ATTENTE -> refuser(exchange, HttpStatus.LOCKED, "CONTRAT_EN_ATTENTE",
                    "Le contrat d\u2019abonnement du cabinet attend l\u2019acceptation du superviseur. "
                            + "Seul l\u2019\u00e9cran du contrat est accessible jusque-l\u00e0.");
            case LIBRE -> chain.filter(exchange);
        });
    }

    enum Verdict { LIBRE, EN_ATTENTE, INCONNU }

    /** Selon billing ; INCONNU si billing ne repond pas et qu'aucune reponse n'est connue. */
    Mono<Verdict> verdict(UUID ws) {
        Etat connu = cache.get(ws);
        if (connu != null && connu.lu().plus(VALIDITE).isAfter(Instant.now())) {
            return Mono.just(de(connu.enAttente()));
        }
        return verification.apply(ws)
                .map(b -> {
                    cache.put(ws, new Etat(b, Instant.now()));
                    return de(b);
                })
                .onErrorResume(ex -> {
                    log.warn("Etat du contrat illisible pour workspace={} : {}", ws, ex.toString());
                    return Mono.just(connu != null ? de(connu.enAttente()) : Verdict.INCONNU);
                })
                .defaultIfEmpty(connu != null ? de(connu.enAttente()) : Verdict.INCONNU);
    }

    private static Verdict de(boolean enAttente) {
        return enAttente ? Verdict.EN_ATTENTE : Verdict.LIBRE;
    }

    boolean permise(String chemin) {
        for (String motif : ROUTES_PERMISES) {
            if (chemins.match(motif, chemin)) return true;
        }
        return false;
    }

    private static Mono<Void> refuser(ServerWebExchange exchange, HttpStatus statut, String code, String message) {
        exchange.getResponse().setStatusCode(statut);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String corps = "{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(corps.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}

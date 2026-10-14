package ma.jurika.common.trial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implementation distante de {@link TrialAccessChecker} pour les services qui
 * ne possedent pas la table {@code workspaces} : lit
 * {@code GET /internal/workspaces/{id}/status} d'auth-service et garde la
 * reponse 60 secondes.
 *
 * <p>Lot L7 (E4) : client HTTP simple (RestClient), sans Feign ni Caffeine,
 * pour etre actif dans chaque service. Un cabinet inconnu d'auth (404) n'a pas
 * d'essai ; toute autre erreur leve {@link EtatEssaiIndisponibleException}
 * (jamais mise en cache) : les controles de l'essai refusent au lieu de
 * supposer l'absence d'essai (l'ancienne version laissait passer en silence).
 */
public class RemoteTrialAccessChecker implements TrialAccessChecker {

    private static final Logger log = LoggerFactory.getLogger(RemoteTrialAccessChecker.class);
    static final Duration DUREE_CACHE = Duration.ofSeconds(60);

    private record Entree(Optional<TrialState> etat, Instant expireLe) {}

    private final Function<UUID, Optional<WorkspaceStatusFeignClient.WorkspaceStatusResponse>> lecture;
    private final Clock horloge;
    private final ConcurrentHashMap<UUID, Entree> cache = new ConcurrentHashMap<>();

    public RemoteTrialAccessChecker(String urlAuth) {
        this(lecteurHttp(RestClient.builder().baseUrl(urlAuth).build()), Clock.systemUTC());
    }

    RemoteTrialAccessChecker(Function<UUID, Optional<WorkspaceStatusFeignClient.WorkspaceStatusResponse>> lecture,
                             Clock horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    @Override
    public Optional<TrialState> getState(UUID workspaceId) {
        Instant maintenant = horloge.instant();
        Entree e = cache.get(workspaceId);
        if (e != null && maintenant.isBefore(e.expireLe())) {
            return e.etat();
        }
        Optional<TrialState> etat = lecture.apply(workspaceId).map(RemoteTrialAccessChecker::versEtat);
        if (cache.size() > 10_000) cache.clear();
        cache.put(workspaceId, new Entree(etat, maintenant.plus(DUREE_CACHE)));
        return etat;
    }

    static Function<UUID, Optional<WorkspaceStatusFeignClient.WorkspaceStatusResponse>> lecteurHttp(RestClient client) {
        return ws -> {
            try {
                return Optional.ofNullable(client.get()
                        .uri("/internal/workspaces/{ws}/status", ws)
                        .retrieve()
                        .body(WorkspaceStatusFeignClient.WorkspaceStatusResponse.class));
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404))) {
                    log.debug("Workspace {} inconnu d'auth-service : pas d'essai", ws);
                    return Optional.empty();
                }
                throw new EtatEssaiIndisponibleException("Etat d'essai illisible (HTTP "
                        + e.getStatusCode().value() + ") pour " + ws, e);
            } catch (RestClientException e) {
                throw new EtatEssaiIndisponibleException("auth-service injoignable pour l'etat d'essai de " + ws, e);
            }
        };
    }

    private static TrialState versEtat(WorkspaceStatusFeignClient.WorkspaceStatusResponse r) {
        return new TrialState(r.workspaceId(), mapStatus(r), r.daysRemaining(), r.trialEndsAt(), r.selectedPlan());
    }

    /**
     * Combine essai et abonnement : un abonnement actif (ou en periode payee)
     * leve l'essai (CONVERTED) ; sinon le statut d'essai brut ; aucun statut
     * d'essai et aucun abonnement : statut {@code null} (pas d'essai).
     */
    static String mapStatus(WorkspaceStatusFeignClient.WorkspaceStatusResponse r) {
        if (r.subscriptionStatus() != null) {
            switch (r.subscriptionStatus()) {
                case "active", "trialing", "past_due" -> {
                    return "CONVERTED";
                }
                case "cancelled" -> {
                    if (r.subscriptionEndsAt() != null && r.subscriptionEndsAt().isAfter(Instant.now())) {
                        return "CONVERTED";
                    }
                }
                default -> {
                    // statut d'essai brut
                }
            }
        }
        return r.trialStatus();
    }

    /** Pour les tests : vide le cache local. */
    public void invalidate(UUID workspaceId) {
        cache.remove(workspaceId);
    }
}

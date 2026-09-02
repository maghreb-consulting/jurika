package ma.jurika.common.trial;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 12 — impl distante de {@link TrialAccessChecker} pour les 5
 * microservices (dataroom/dashboard/supervision/ticket/workflow) qui ne
 * possedent pas la table {@code workspaces}.
 *
 * <p>Appelle auth-service via Feign et cache la reponse 60 secondes
 * (latence + protection contre les rafales — un service en pleine
 * charge peut faire des centaines de requetes/sec).
 *
 * <p>Strategy : si Feign throw (auth-service down), on logue + fail-open
 * (Optional.empty) pour ne pas bloquer le service en cas de coupure
 * auth-service — meme philosophie que le filter {@code shouldNotFilter}.
 *
 * <p>Sprint 12 — pour cancelled + hors-periode, on remappe sur
 * {@code TRIAL_EXPIRED} pour que le filter renvoie 402 (acceptance criteria :
 * "5 services reagissent a workspace cancelled hors periode").
 */
public class RemoteTrialAccessChecker implements TrialAccessChecker {

    private static final Logger log = LoggerFactory.getLogger(RemoteTrialAccessChecker.class);

    private final WorkspaceStatusFeignClient client;
    private final Cache<UUID, Optional<TrialState>> cache;

    public RemoteTrialAccessChecker(WorkspaceStatusFeignClient client) {
        this.client = client;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public Optional<TrialState> getState(UUID workspaceId) {
        return cache.get(workspaceId, this::fetchRemote);
    }

    private Optional<TrialState> fetchRemote(UUID workspaceId) {
        try {
            WorkspaceStatusFeignClient.WorkspaceStatusResponse r = client.getStatus(workspaceId);
            if (r == null) return Optional.empty();
            String effectiveStatus = mapStatus(r);
            return Optional.of(new TrialState(
                    r.workspaceId(),
                    effectiveStatus,
                    r.daysRemaining(),
                    r.trialEndsAt(),
                    r.selectedPlan()
            ));
        } catch (FeignException.NotFound e) {
            log.debug("Workspace {} sans trial cote auth-service (legacy?) — fail-open", workspaceId);
            return Optional.empty();
        } catch (FeignException e) {
            log.warn("auth-service injoignable pour workspace={} (fail-open). status={}", workspaceId, e.status());
            return Optional.empty();
        }
    }

    /**
     * Sprint 12 — combine trial + subscription :
     *  - subscription active -> CONVERTED (filter laisse passer)
     *  - subscription cancelled hors periode -> TRIAL_EXPIRED (filter 402)
     *  - subscription past_due dans la grace period -> CONVERTED (Stripe retry)
     *  - sinon : trial_status brut
     */
    private static String mapStatus(WorkspaceStatusFeignClient.WorkspaceStatusResponse r) {
        if (r.subscriptionStatus() != null) {
            switch (r.subscriptionStatus()) {
                case "active":
                case "trialing":
                case "past_due":   // grace period — Stripe retry
                    return "CONVERTED";
                case "cancelled": {
                    // RG-BL07 : acces preserve jusqu'a current_period_end
                    java.time.Instant now = java.time.Instant.now();
                    if (r.subscriptionEndsAt() != null && r.subscriptionEndsAt().isAfter(now)) {
                        return "CONVERTED";
                    }
                    return "TRIAL_EXPIRED";
                }
                case "incomplete":
                case "incomplete_expired":
                case "unpaid":
                    return "TRIAL_EXPIRED";
                default:
                    // fall-through
            }
        }
        return r.trialStatus() != null ? r.trialStatus() : "TRIAL_ACTIVE";
    }

    /** Pour les tests : vide le cache local. */
    public void invalidate(UUID workspaceId) {
        cache.invalidate(workspaceId);
    }
}

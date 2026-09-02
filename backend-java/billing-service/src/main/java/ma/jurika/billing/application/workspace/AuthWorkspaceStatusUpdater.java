package ma.jurika.billing.application.workspace;

import feign.FeignException;
import ma.jurika.common.trial.WorkspaceStatusFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 T7 — impl Feign de {@link WorkspaceStatusUpdater}.
 *
 * <p>Notifie auth-service des transitions de status workspace via
 * PUT /internal/workspaces/{id}/status. Si le call echoue (auth-service
 * indisponible), on log + swallow : la transition cote billing-service
 * est deja persistee (subscription row mise a jour par le webhook handler),
 * un job de reconciliation peut rattraper plus tard.
 *
 * <p>Wiring : voir {@link WorkspaceUpdaterConfig#authWorkspaceStatusUpdater}.
 * Avant Sprint 12 finition (2026-06-04) cette classe etait declaree
 * {@code @Component} + {@code @ConditionalOnBean(WorkspaceStatusFeignClient.class)},
 * mais la condition etait evaluee pendant le component scan — donc AVANT
 * l'enregistrement des Feign clients par {@code ImportBeanDefinitionRegistrar}.
 * Resultat : le bean n'etait jamais cree et le {@link LoggingWorkspaceStatusUpdater}
 * no-op prenait silencieusement le relais, faisant tomber dans le vide toutes
 * les transitions Stripe → auth-service. Le wiring est desormais en
 * {@code @Configuration} + {@code @Bean} pour evaluation tardive.
 */
public class AuthWorkspaceStatusUpdater implements WorkspaceStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(AuthWorkspaceStatusUpdater.class);

    private final WorkspaceStatusFeignClient client;

    public AuthWorkspaceStatusUpdater(WorkspaceStatusFeignClient client) {
        this.client = client;
    }

    @Override
    public void activate(UUID workspaceId, String planCode, Instant accessUntil) {
        safeCall(workspaceId, "ACTIVATED", planCode, accessUntil);
    }

    @Override
    public void markCancelled(UUID workspaceId, Instant accessUntil) {
        safeCall(workspaceId, "CANCELLED", null, accessUntil);
    }

    @Override
    public void markPastDue(UUID workspaceId) {
        safeCall(workspaceId, "PAST_DUE", null, null);
    }

    @Override
    public void issueCredentials(UUID workspaceId, String reason) {
        try {
            client.issueCredentials(workspaceId, reason);
            log.info("issue-credentials notifie workspace={} reason={}", workspaceId, reason);
        } catch (FeignException e) {
            log.warn("auth-service injoignable pour issue-credentials workspace={} : {} — un job admin pourra rejouer",
                    workspaceId, e.getMessage());
        }
    }

    private void safeCall(UUID workspaceId, String transition, String planCode, Instant accessUntil) {
        try {
            client.updateStatus(workspaceId, transition, planCode,
                    accessUntil != null ? accessUntil.toString() : null);
            log.info("auth-service notifie workspace={} transition={} plan={}",
                    workspaceId, transition, planCode);
        } catch (FeignException e) {
            log.warn("auth-service injoignable pour transition {} workspace={} : {} — sera reconcilie",
                    transition, workspaceId, e.getMessage());
        }
    }
}

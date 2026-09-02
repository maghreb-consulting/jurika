package ma.jurika.billing.application.workspace;

import ma.jurika.common.trial.WorkspaceStatusFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 finition (2026-06-04) — wiring du {@link WorkspaceStatusUpdater}.
 *
 * <p>Trois strategies, par ordre de preference :
 *  1. <b>Feign client</b> (production normale via Eureka) : si
 *     {@link WorkspaceStatusFeignClient} est present comme bean, on l'utilise.
 *  2. <b>RestClient direct</b> (dev local sans Eureka, ou Feign skippe par
 *     l'autoconfig) : on POST directement sur {@code jurika.auth.internal-url}
 *     via {@link RestClient}. Aucun probleme de Conditional/order.
 *  3. <b>Logging no-op</b> (defini dans {@link LoggingWorkspaceStatusUpdater},
 *     {@code @ConditionalOnMissingBean}) : degradation gracieuse si le bean
 *     primaire ne se cree pas.
 *
 * <p>Bug fix historique : l'ancien pattern {@code @Component +
 * @ConditionalOnBean(WorkspaceStatusFeignClient.class)} tombait en silence —
 * la condition s'evalue avant que le proxy Feign soit visible comme bean
 * resolvable par type (FactoryBean). Resultat : transitions Stripe lost
 * dans le vide. Ce wiring rend impossible la regression silencieuse :
 * meme sans Feign, on appelle bien auth-service en HTTP direct.
 */
@Configuration
public class WorkspaceUpdaterConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceUpdaterConfig.class);

    /**
     * Bean primaire : prefere le Feign client si dispo, sinon construit un
     * RestClient updater pointant sur jurika.auth.internal-url. JAMAIS un noop —
     * si on n'a meme pas l'URL d'auth-service, on log et on accepte le silence
     * (mais ca serait une mauvaise config).
     */
    @Bean
    @Primary
    public WorkspaceStatusUpdater workspaceStatusUpdater(
            ObjectProvider<WorkspaceStatusFeignClient> feignProvider,
            @Value("${jurika.auth.internal-url:http://localhost:8081}") String authInternalUrl) {
        WorkspaceStatusFeignClient feign = feignProvider.getIfAvailable();
        if (feign != null) {
            log.info("WorkspaceStatusUpdater = Feign client (auth-service via discovery)");
            return new AuthWorkspaceStatusUpdater(feign);
        }
        log.info("WorkspaceStatusUpdater = RestClient direct (url={})", authInternalUrl);
        return new RestClientWorkspaceStatusUpdater(
                RestClient.builder().baseUrl(authInternalUrl).build());
    }

    /**
     * Impl de secours en RestClient direct — utilisee quand Feign n'est pas
     * wire correctement (cas billing-service Sprint 12). Cible le meme endpoint
     * {@code PUT /internal/workspaces/{id}/status} que la version Feign.
     */
    public static final class RestClientWorkspaceStatusUpdater implements WorkspaceStatusUpdater {
        private static final Logger log = LoggerFactory.getLogger(RestClientWorkspaceStatusUpdater.class);
        private final RestClient http;

        public RestClientWorkspaceStatusUpdater(RestClient http) {
            this.http = http;
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

        /**
         * BUG 14 (2026-06-07) — re-emission des identifiants apres validation
         * paiement (CARD via webhook, TEST_BYPASS, ou validation manuelle
         * hors-ligne). POST /internal/workspaces/{id}/issue-credentials sur
         * auth-service qui regenere un MDP temp + send welcome email.
         *
         * <p>Sans cette override, l'interface utilisait son defaut no-op et
         * le wizard signup paiement-first ne recevait JAMAIS l'email avec
         * les identifiants.
         */
        @Override
        public void issueCredentials(UUID workspaceId, String reason) {
            URI uri = URI.create("/internal/workspaces/" + workspaceId
                    + "/issue-credentials"
                    + (reason != null ? "?reason=" + reason : ""));
            try {
                http.post().uri(uri).retrieve().toBodilessEntity();
                log.info("auth-service issue-credentials notifie workspace={} reason={}",
                        workspaceId, reason);
            } catch (RuntimeException e) {
                log.warn("auth-service injoignable pour issue-credentials workspace={} : {} — un admin pourra rejouer",
                        workspaceId, e.getMessage());
            }
        }

        private void safeCall(UUID workspaceId, String transition, String planCode, Instant accessUntil) {
            StringBuilder query = new StringBuilder("?transition=").append(transition);
            if (planCode != null) query.append("&planCode=").append(planCode);
            if (accessUntil != null) query.append("&accessUntil=").append(accessUntil);
            URI uri = URI.create("/internal/workspaces/" + workspaceId + "/status" + query);
            try {
                http.put().uri(uri).retrieve().toBodilessEntity();
                log.info("auth-service notifie workspace={} transition={} plan={}",
                        workspaceId, transition, planCode);
            } catch (RuntimeException e) {
                log.warn("auth-service injoignable pour transition {} workspace={} : {} — sera reconcilie",
                        transition, workspaceId, e.getMessage());
            }
        }
    }
}

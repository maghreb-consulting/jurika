package ma.jurika.billing.application.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — impl par defaut no-op qui log les transitions. Remplacee
 * par {@code AuthWorkspaceStatusUpdater} Feign en T7. Fallback bean :
 * permet a billing-service de demarrer meme sans auth-service joignable
 * (degradation gracieuse).
 *
 * <p>Sprint 12 fix — pattern correct {@code @Bean + @ConditionalOnMissingBean}
 * dans une {@code @Configuration}, evalue apres le composant scan ; l'ancien
 * pattern {@code @Component + @ConditionalOnMissingBean} sur classe interne
 * statique ne marchait pas (scan eager → bean jamais cree).
 */
@Configuration
public class LoggingWorkspaceStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(LoggingWorkspaceStatusUpdater.class);

    @Bean
    @ConditionalOnMissingBean(WorkspaceStatusUpdater.class)
    public WorkspaceStatusUpdater noopWorkspaceStatusUpdater() {
        return new WorkspaceStatusUpdater() {
            @Override
            public void activate(UUID workspaceId, String planCode, Instant accessUntil) {
                log.info("[noop] activate workspace={} plan={} until={}", workspaceId, planCode, accessUntil);
            }

            @Override
            public void markCancelled(UUID workspaceId, Instant accessUntil) {
                log.info("[noop] cancel workspace={} until={}", workspaceId, accessUntil);
            }

            @Override
            public void markPastDue(UUID workspaceId) {
                log.info("[noop] past_due workspace={}", workspaceId);
            }

            // BUG 14 (2026-06-07) — sans cette override explicite,
            // le defaut no-op de l'interface aurait silencieusement
            // avale les emissions de credentials TEST_BYPASS / hors-ligne.
            // On loggue en WARN pour signaler la vraie consequence : aucun
            // email de bienvenue ne sera envoye au SUPERVISEUR.
            @Override
            public void issueCredentials(UUID workspaceId, String reason) {
                log.warn("[noop] issue-credentials workspace={} reason={} -- "
                        + "auth-service injoignable, aucun email envoye. "
                        + "Verifie jurika.auth.internal-url + connectivite reseau.",
                        workspaceId, reason);
            }
        };
    }
}

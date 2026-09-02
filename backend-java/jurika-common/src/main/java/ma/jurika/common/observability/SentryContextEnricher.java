package ma.jurika.common.observability;

import io.sentry.Sentry;
import io.sentry.protocol.User;

import java.util.UUID;

/**
 * Sprint 2 / TASK 5 — Enrichit le scope Sentry de la requete courante.
 *
 * <p>API statique : pas de DI, pas de bean. Compatible avec
 * {@code JwtAuthFilter} (servlet + bientot reactive via cleanScope()).
 *
 * <p>RGPD by design : on ne pousse PAS l'email ni l'IP — uniquement les
 * identifiants opaques {@code userId} et {@code workspaceId}, et le
 * {@code correlationId} du MDC pour rapprocher logs / traces / errors.
 */
public final class SentryContextEnricher {

    private SentryContextEnricher() {
    }

    /**
     * A appeler quand un principal authentifie est resolu (apres parse JWT).
     * Le scope reste actif jusqu'a {@link #clearScope()} ou la fin du thread/request.
     */
    public static void enrich(UUID workspaceId, UUID userId, String correlationId) {
        Sentry.configureScope(scope -> {
            if (userId != null) {
                User user = new User();
                user.setId(userId.toString());
                // Pas d'email, pas d'IP : RGPD/CNDP (cf. send-default-pii=false).
                scope.setUser(user);
            }
            if (workspaceId != null) {
                scope.setTag("workspace", workspaceId.toString());
            }
            if (correlationId != null && !correlationId.isBlank()) {
                scope.setTag("correlationId", correlationId);
            }
        });
    }

    /**
     * Nettoyage en fin de requete pour eviter qu'un thread reutilise (pool servlet)
     * ne reprenne le scope du dernier user vu.
     */
    public static void clearScope() {
        Sentry.configureScope(scope -> {
            scope.setUser(null);
            scope.removeTag("workspace");
            scope.removeTag("correlationId");
        });
    }
}

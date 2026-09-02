package ma.jurika.common.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Port d'emission d'evenements d'audit.
 * <p>
 * Chaque service fournit son propre adapter (table dediee, RabbitMQ, etc.).
 * Si aucun bean n'est defini, {@link AuditAspect} utilise un fallback logger no-op.
 */
public interface AuditEventEmitter {

    /**
     * Persiste un evenement d'audit (append-only).
     *
     * @param workspaceId  workspace courant (peut etre null pour les actions SuperAdmin globales)
     * @param actorId      user ayant declenche l'action (null = anonyme/systeme)
     * @param action       code UPPER_SNAKE_CASE
     * @param resourceType type de ressource cible (peut etre null)
     * @param resourceId   id de la ressource (peut etre null)
     * @param metadata     champs additionnels en cle/valeur (jsonb)
     */
    default void emit(UUID workspaceId,
                      UUID actorId,
                      String action,
                      String resourceType,
                      UUID resourceId,
                      Map<String, Object> metadata) {
        emit(new AuditEvent(workspaceId, actorId, action, resourceType, resourceId,
                metadata, null, null));
    }

    /**
     * Variante etendue Sprint 2 : permet de transporter {@code correlationId} (du MDC)
     * et {@code sourceService} (spring.application.name) pour ecriture dans les colonnes
     * dediees de la table {@code audit_log}.
     */
    void emit(AuditEvent event);

    /**
     * Evenement structure prepare par {@link AuditAspect}. Les champs Sprint 2
     * ({@code correlationId}, {@code sourceService}) sont nullable pour preserver
     * la compatibilite avec les emetteurs legacy.
     */
    record AuditEvent(
            UUID workspaceId,
            UUID actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            String correlationId,
            String sourceService
    ) {}
}

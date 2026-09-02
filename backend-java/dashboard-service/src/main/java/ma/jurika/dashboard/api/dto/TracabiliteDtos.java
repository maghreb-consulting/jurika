package ma.jurika.dashboard.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de la traçabilité (E2) — lecture de audit_log scopee au workspace.
 */
public final class TracabiliteDtos {

    private TracabiliteDtos() {}

    /**
     * Une entree d'audit lisible : qui ({@code userId}) / action
     * ({@code action} + {@code actionLabel} FR) / quoi ({@code entityType}/{@code entityId})
     * / quand ({@code createdAt}).
     */
    public record TracabiliteEntry(
            long id,
            String action,
            String actionLabel,
            UUID userId,
            String entityType,
            UUID entityId,
            String metadata,
            String sourceService,
            Instant createdAt
    ) {}

    /** Page de resultats (tri created_at DESC). */
    public record TracabilitePage(
            List<TracabiliteEntry> items,
            long total,
            int limit,
            int offset
    ) {}
}

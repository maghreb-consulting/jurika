package ma.jurika.auth.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint admin de consultation de l'audit log (Sprint 2 / TASK 6 commit 3, RG-SAAS-02).
 *
 * <p>Accessible uniquement au role {@code SUPER_ADMIN}. Active la session setting
 * {@code app.audit_bypass=true} pour traverser la RLS multi-tenant et lire les
 * evenements de tous les workspaces.
 */
@RestController
public class AdminAuditController {

    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbc;

    public AdminAuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/admin/audit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse search(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safeOffset = Math.max(offset, 0);

        // RLS bypass scope a la transaction courante (SET LOCAL).
        jdbc.execute("SET LOCAL app.audit_bypass = 'true'");

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (workspaceId != null) { where.append(" AND workspace_id = ?"); args.add(workspaceId); }
        if (userId != null)      { where.append(" AND user_id = ?");      args.add(userId); }
        // Filtre par type d'utilisateur (role) : le role n'est pas stocke dans
        // audit_log, on restreint donc aux user_id des utilisateurs de ce role.
        if (role != null && !role.isBlank()) {
            // Lot L0 (E13b) : users est sous RLS ; fonction SECURITY DEFINER (auth V34).
            where.append(" AND user_id IN (SELECT * FROM admin_utilisateurs_par_role(?))");
            args.add(role.trim());
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?"); args.add(action);
        }
        if (sourceService != null && !sourceService.isBlank()) {
            where.append(" AND source_service = ?"); args.add(sourceService);
        }
        if (fromDate != null) { where.append(" AND created_at >= ?"); args.add(java.sql.Timestamp.from(fromDate)); }
        if (toDate != null)   { where.append(" AND created_at <  ?"); args.add(java.sql.Timestamp.from(toDate)); }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray());

        args.add(safeLimit);
        args.add(safeOffset);
        List<AuditEntry> rows = jdbc.query(
                "SELECT id, workspace_id, user_id, action, entity_type, entity_id, " +
                "       source_service, correlation_id, metadata, created_at " +
                "FROM audit_log" + where +
                " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AuditEntry(
                        rs.getLong("id"),
                        (UUID) rs.getObject("workspace_id"),
                        (UUID) rs.getObject("user_id"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        (UUID) rs.getObject("entity_id"),
                        rs.getString("source_service"),
                        rs.getString("correlation_id"),
                        rs.getString("metadata"),
                        rs.getTimestamp("created_at").toInstant()),
                args.toArray());

        Map<String, Object> filters = new HashMap<>();
        filters.put("workspaceId", workspaceId);
        filters.put("userId", userId);
        filters.put("role", role);
        filters.put("action", action);
        filters.put("sourceService", sourceService);
        filters.put("fromDate", fromDate);
        filters.put("toDate", toDate);
        return new PageResponse(rows, total == null ? 0L : total, safeOffset, safeLimit, filters);
    }

    /**
     * Valeurs distinctes presentes dans le journal, pour alimenter les listes
     * deroulantes de filtrage (actions + services source). Sur donnees reelles
     * (SELECT DISTINCT), jamais une liste inventee.
     */
    @GetMapping("/api/v1/admin/audit/facets")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public Facets facets() {
        jdbc.execute("SET LOCAL app.audit_bypass = 'true'");
        List<String> actions = jdbc.queryForList(
                "SELECT DISTINCT action FROM audit_log WHERE action IS NOT NULL ORDER BY action",
                String.class);
        List<String> sourceServices = jdbc.queryForList(
                "SELECT DISTINCT source_service FROM audit_log "
                        + "WHERE source_service IS NOT NULL ORDER BY source_service",
                String.class);
        return new Facets(actions, sourceServices);
    }

    public record Facets(List<String> actions, List<String> sourceServices) {}

    public record AuditEntry(
            long id,
            UUID workspaceId,
            UUID userId,
            String action,
            String entityType,
            UUID entityId,
            String sourceService,
            String correlationId,
            String metadata,
            Instant createdAt
    ) {}

    public record PageResponse(
            List<AuditEntry> items,
            long total,
            int offset,
            int limit,
            Map<String, Object> filters
    ) {}
}

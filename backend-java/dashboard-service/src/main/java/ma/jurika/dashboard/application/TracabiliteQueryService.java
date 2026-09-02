package ma.jurika.dashboard.application;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dashboard.api.dto.TracabiliteDtos.TracabiliteEntry;
import ma.jurika.dashboard.api.dto.TracabiliteDtos.TracabilitePage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lecture de la traçabilité (audit_log) scopee au workspace courant (E2).
 *
 * <p><b>Securite RLS / multi-tenant</b> : le workspace provient TOUJOURS de
 * {@link TenantContext#get()} (le JWT), jamais d'un parametre de requete, et
 * n'est JAMAIS contourne via {@code app.audit_bypass} (contrairement a la vue
 * SUPER_ADMIN plateforme de auth-service). Double garde :
 * <ol>
 *   <li>filtre SQL explicite {@code WHERE workspace_id = ?} ;</li>
 *   <li>methodes {@code @Transactional} -> {@code RlsAspect} positionne
 *       {@code app.current_workspace_id}, donc la policy RLS de {@code audit_log}
 *       restreint deja les lignes au workspace courant.</li>
 * </ol>
 */
@Service
public class TracabiliteQueryService {

    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbc;

    public TracabiliteQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filtres optionnels. Le workspace n'est PAS ici : il vient du JWT. */
    public record Filter(
            String entityType,
            UUID entityId,
            UUID userId,
            String action,
            Instant from,
            Instant to,
            /**
             * Traçabilité (2026-07-15) — filtre par statut de l'acteur :
             * {@code ACTIVE} (comptes encore actifs), {@code RETIRED} (comptes
             * desactives OU purges de la table users), ou {@code null}/autre =
             * tous. Applique cote SQL (correct meme sur de grands volumes
             * pagines) via une correlation sur la table {@code users}.
             */
            String actorStatus,
            int limit,
            int offset
    ) {}

    @Transactional(readOnly = true)
    public TracabilitePage search(Filter f) {
        UUID workspaceId = TenantContext.get();
        int limit = Math.min(Math.max(f.limit(), 1), MAX_LIMIT);
        int offset = Math.max(f.offset(), 0);

        StringBuilder where = new StringBuilder("WHERE workspace_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        if (notBlank(f.entityType())) { where.append(" AND entity_type = ?"); args.add(f.entityType()); }
        if (f.entityId() != null)     { where.append(" AND entity_id = ?");   args.add(f.entityId()); }
        if (f.userId() != null)       { where.append(" AND user_id = ?");     args.add(f.userId()); }
        if (notBlank(f.action()))     { where.append(" AND action = ?");      args.add(f.action()); }
        if (f.from() != null)         { where.append(" AND created_at >= ?"); args.add(java.sql.Timestamp.from(f.from())); }
        if (f.to() != null)           { where.append(" AND created_at < ?");  args.add(java.sql.Timestamp.from(f.to())); }

        // Traçabilité (2026-07-15) — filtre par statut de l'acteur. Un acteur est
        // "actif" s'il existe encore dans users (meme workspace) avec un statut
        // != INACTIVE ; "retire" sinon (INACTIVE ou compte purge). Les events
        // systeme (user_id NULL) ne sont pas des acteurs -> exclus des deux sous-
        // ensembles. Correlation sur audit_log.user_id (defense RLS + predicat
        // explicite workspace_id, cf multi-tenant-defense-in-depth).
        String actor = f.actorStatus();
        if ("ACTIVE".equalsIgnoreCase(actor)) {
            where.append(" AND audit_log.user_id IS NOT NULL AND EXISTS ("
                    + "SELECT 1 FROM users u WHERE u.id = audit_log.user_id "
                    + "AND u.workspace_id = ? AND u.status <> 'INACTIVE')");
            args.add(workspaceId);
        } else if ("RETIRED".equalsIgnoreCase(actor)) {
            where.append(" AND audit_log.user_id IS NOT NULL AND NOT EXISTS ("
                    + "SELECT 1 FROM users u WHERE u.id = audit_log.user_id "
                    + "AND u.workspace_id = ? AND u.status <> 'INACTIVE')");
            args.add(workspaceId);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log " + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(offset);
        List<TracabiliteEntry> items = jdbc.query(
                "SELECT id, action, user_id, entity_type, entity_id, metadata, source_service, created_at "
                        + "FROM audit_log " + where
                        + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, rn) -> new TracabiliteEntry(
                        rs.getLong("id"),
                        rs.getString("action"),
                        AuditActionLabels.label(rs.getString("action")),
                        (UUID) rs.getObject("user_id"),
                        rs.getString("entity_type"),
                        (UUID) rs.getObject("entity_id"),
                        rs.getString("metadata"),
                        rs.getString("source_service"),
                        rs.getTimestamp("created_at").toInstant()),
                pageArgs.toArray());

        return new TracabilitePage(items, total == null ? 0 : total, limit, offset);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

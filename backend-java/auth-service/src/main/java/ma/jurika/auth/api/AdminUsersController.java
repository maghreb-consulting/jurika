package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vue cross-workspace des utilisateurs de la plateforme (SUPER_ADMIN).
 *
 * <p>Lecture directe en JdbcTemplate (registre users/workspaces non cloisonne
 * par RLS applicative). Aucune donnee metier n'est exposee (RG-U07) : uniquement
 * l'identite, le role, le workspace, le statut et l'etat 2FA.
 *
 * <p>Le reset de mot de passe reutilise l'endpoint existant
 * {@code POST /api/v1/admin/workspaces/{ws}/users/{id}/resend-welcome}
 * (regeneration d'un MDP temporaire + email). Suspendre / reactiver muent le
 * statut du compte et sont auditees.
 */
@RestController
public class AdminUsersController {

    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public AdminUsersController(JdbcTemplate jdbc, AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "25") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safeOffset = Math.max(offset, 0);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (u.first_name ILIKE ? OR u.last_name ILIKE ?"
                    + " OR u.login_email ILIKE ? OR u.contact_email ILIKE ?)");
            String like = "%" + search.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND u.role = ?"); args.add(role.trim());
        }
        if (workspaceId != null) {
            where.append(" AND u.workspace_id = ?"); args.add(workspaceId);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeLimit);
        pageArgs.add(safeOffset);
        List<UserRow> rows = jdbc.query(
                "SELECT u.id, u.first_name, u.last_name, u.login_email, u.contact_email,"
                        + " u.role, u.status, u.totp_enabled, u.created_at, u.last_login_at,"
                        + " u.workspace_id, w.code AS ws_code, w.name AS ws_name "
                        + "FROM users u LEFT JOIN workspaces w ON w.id = u.workspace_id"
                        + where
                        + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new UserRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("login_email"),
                        rs.getString("contact_email"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getBoolean("totp_enabled"),
                        (UUID) rs.getObject("workspace_id"),
                        rs.getString("ws_code"),
                        rs.getString("ws_name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("last_login_at") == null
                                ? null : rs.getTimestamp("last_login_at").toInstant()),
                pageArgs.toArray());

        return new PageResponse(rows, total == null ? 0L : total, safeOffset, safeLimit);
    }

    @PostMapping("/api/v1/admin/users/{userId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> suspend(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        return changeStatus(userId, "INACTIVE", "USER_SUSPENDED", admin, http);
    }

    @PostMapping("/api/v1/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> reactivate(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        return changeStatus(userId, "ACTIVE", "USER_REACTIVATED", admin, http);
    }

    private ResponseEntity<Map<String, Object>> changeStatus(
            UUID userId, String newStatus, String auditAction,
            AuthenticatedUser admin, HttpServletRequest http) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT workspace_id, role FROM users WHERE id = ?", userId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
        if ("SUPER_ADMIN".equals(row.get("role"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un compte SUPER_ADMIN ne peut pas etre suspendu ici.");
        }
        UUID wsId = (UUID) row.get("workspace_id");

        jdbc.update("UPDATE users SET status = ?, updated_at = NOW() WHERE id = ?",
                newStatus, userId);

        Map<String, Object> meta = new HashMap<>();
        meta.put("newStatus", newStatus);
        auditLogger.log(wsId, admin == null ? null : admin.userId(),
                auditAction, "USER", userId,
                http.getRemoteAddr(), http.getHeader("User-Agent"), meta);
        return ResponseEntity.ok(Map.of("userId", userId, "status", newStatus));
    }

    public record UserRow(
            UUID id,
            String firstName,
            String lastName,
            String loginEmail,
            String contactEmail,
            String role,
            String status,
            boolean totpEnabled,
            UUID workspaceId,
            String workspaceCode,
            String workspaceName,
            Instant createdAt,
            Instant lastLoginAt) {}

    public record PageResponse(
            List<UserRow> items,
            long total,
            int offset,
            int limit) {}
}

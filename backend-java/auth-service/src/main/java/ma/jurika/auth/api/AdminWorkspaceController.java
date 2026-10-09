package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import ma.jurika.auth.application.ResendWelcomeUseCase;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des workspaces (cabinets clients) par le SUPER_ADMIN.
 *
 * <p>Le SUPER_ADMIN opere cross-workspace de facon controlee, comme
 * l'agregateur dashboard : lecture en JdbcTemplate + {@code SET LOCAL
 * app.audit_bypass='true'} pour traverser la RLS multi-tenant de audit_log.
 * Les tables workspaces / users ne sont pas cloisonnees par RLS applicative
 * (registre de tenants) ; le compte de service les lit directement.
 *
 * <p>Toutes les actions muent le statut du workspace et sont auditees
 * (WORKSPACE_ACTIVATED / WORKSPACE_SUSPENDED / WORKSPACE_DEACTIVATED).
 *
 * <p>HIGH-9 historique : {@code resend-welcome} regenere + renvoie le mail
 * initial (MDP temp) pour un cabinet bloque.
 */
@RestController
@RequestMapping("/api/v1/admin/workspaces")
public class AdminWorkspaceController {

    private static final List<String> VALID_STATUSES =
            List.of("ACTIVE", "SUSPENDED", "DEACTIVATED", "PENDING_VERIFICATION");

    private final ResendWelcomeUseCase resendWelcomeUseCase;
    private final JdbcTemplate jdbc;
    private final AuditLogger auditLogger;

    public AdminWorkspaceController(ResendWelcomeUseCase resendWelcomeUseCase,
                                    JdbcTemplate jdbc,
                                    AuditLogger auditLogger) {
        this.resendWelcomeUseCase = resendWelcomeUseCase;
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
    }

    /**
     * Liste tous les workspaces de la plateforme (cross-workspace) avec, pour
     * chacun : code, denomination, forfait, statut (ESSAI derive du trial),
     * nb d'employes, nb de clients, stockage (best-effort), date de creation
     * et contact.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public List<WorkspaceRow> list() {
        jdbc.execute("SET LOCAL app.audit_bypass = 'true'");

        // Lot L0 (E13b) : vue transverse par nature ; sous jurika_app, la RLS ne
        // laisserait voir que le workspace de l'administrateur. Fonctions
        // SECURITY DEFINER d'auth V34 (lecture seule, colonnes minimales).
        List<WorkspaceRow> rows = jdbc.query("""
                SELECT id, code, name, contact_email, status, trial_status,
                       selected_plan, created_at, employes, clients
                FROM admin_liste_workspaces()
                """, (rs, i) -> new WorkspaceRow(
                        (UUID) rs.getObject("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("contact_email"),
                        computeStatut(rs.getString("status"), rs.getString("trial_status")),
                        rs.getString("selected_plan"),
                        rs.getLong("employes"),
                        rs.getLong("clients"),
                        null, null,
                        rs.getTimestamp("created_at").toInstant()));

        // Stockage par workspace (best-effort) : si le schema dataroom differe
        // ou est indisponible, on laisse null -> le front affiche "—" sans mentir.
        Map<UUID, long[]> storage = new HashMap<>();
        try {
            jdbc.query("""
                    SELECT workspace_id, octets AS bytes, documents AS n
                    FROM admin_stockage_par_workspace()
                    """, rs -> {
                storage.put((UUID) rs.getObject("workspace_id"),
                        new long[]{rs.getLong("bytes"), rs.getLong("n")});
            });
        } catch (RuntimeException ignored) {
            // stockage indisponible -> reste null
        }

        if (storage.isEmpty()) return rows;
        return rows.stream()
                .map(r -> {
                    long[] s = storage.get(r.id());
                    return s == null ? r : r.withStorage(s[0], s[1]);
                })
                .toList();
    }

    // Lot L0 (E13b) : en transaction, pour que le workspace du chemin (pose par
    // ContexteWorkspaceCheminConfig) atteigne la RLS.
    @PostMapping("/{workspaceId}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> activate(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        return changeStatus(workspaceId, "ACTIVE", "WORKSPACE_ACTIVATED", admin, http);
    }

    @PostMapping("/{workspaceId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> suspend(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        return changeStatus(workspaceId, "SUSPENDED", "WORKSPACE_SUSPENDED", admin, http);
    }

    @PostMapping("/{workspaceId}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> deactivate(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        return changeStatus(workspaceId, "DEACTIVATED", "WORKSPACE_DEACTIVATED", admin, http);
    }

    private ResponseEntity<Map<String, Object>> changeStatus(
            UUID workspaceId, String newStatus, String auditAction,
            AuthenticatedUser admin, HttpServletRequest http) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide");
        }
        int updated = jdbc.update(
                "UPDATE workspaces SET status = ?, updated_at = NOW() WHERE id = ?",
                newStatus, workspaceId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace introuvable");
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("newStatus", newStatus);
        auditLogger.log(workspaceId, admin == null ? null : admin.userId(),
                auditAction, "WORKSPACE", workspaceId,
                http.getRemoteAddr(), http.getHeader("User-Agent"), meta);
        return ResponseEntity.ok(Map.of("workspaceId", workspaceId, "status", newStatus));
    }

    /**
     * Vue detaillee d'un workspace : identite complete + compteurs (equipe,
     * clients, tickets, dossiers), stockage, repartitions (roles, documents,
     * tickets) et activite 30j / evenements recents. Donnees 100% reelles ;
     * les agregats cross-service (dataroom / tickets) degradent proprement en
     * null / liste vide si le schema est indisponible.
     */
    @GetMapping("/{workspaceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public WorkspaceDetail detail(@PathVariable UUID workspaceId) {
        jdbc.execute("SET LOCAL app.audit_bypass = 'true'");

        WorkspaceDetail base;
        try {
            base = jdbc.queryForObject("""
                    SELECT id, code, name, status, trial_status, selected_plan, contact_email,
                           city, ice, if_fiscal, rc_number, adresse, telephone, site_web,
                           created_at, trial_started_at, trial_ends_at, subscription_status
                    FROM workspaces WHERE id = ?
                    """, (rs, i) -> new WorkspaceDetail(
                            (UUID) rs.getObject("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            computeStatut(rs.getString("status"), rs.getString("trial_status")),
                            rs.getString("selected_plan"),
                            rs.getString("contact_email"),
                            rs.getString("city"),
                            rs.getString("ice"),
                            rs.getString("if_fiscal"),
                            rs.getString("rc_number"),
                            rs.getString("adresse"),
                            rs.getString("telephone"),
                            rs.getString("site_web"),
                            instant(rs, "created_at"),
                            instant(rs, "trial_started_at"),
                            instant(rs, "trial_ends_at"),
                            rs.getString("trial_status"),
                            rs.getString("subscription_status")),
                    workspaceId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace introuvable");
        }

        // Repartition des utilisateurs par role + compteurs equipe / clients.
        List<CategoryCount> usersByRole = jdbc.query("""
                SELECT role, COUNT(*) FROM users WHERE workspace_id = ? GROUP BY role ORDER BY 2 DESC
                """, (rs, i) -> new CategoryCount(rs.getString(1), rs.getLong(2)), workspaceId);
        long employes = usersByRole.stream()
                .filter(c -> "EMPLOYE".equals(c.label()) || "SUPERVISEUR".equals(c.label()))
                .mapToLong(CategoryCount::count).sum();
        long clients = usersByRole.stream()
                .filter(c -> "CLIENT".equals(c.label())).mapToLong(CategoryCount::count).sum();

        // Activite 30j + evenements par action + derniers evenements (audit_log).
        List<DayCount> activity30d = jdbc.query("""
                SELECT date_trunc('day', created_at)::date AS d, COUNT(*)
                FROM audit_log
                WHERE workspace_id = ? AND created_at >= NOW() - INTERVAL '30 days'
                GROUP BY 1 ORDER BY 1
                """, (rs, i) -> new DayCount(rs.getObject("d", LocalDate.class).toString(), rs.getLong(2)),
                workspaceId);
        List<CategoryCount> eventsByAction = jdbc.query("""
                SELECT action, COUNT(*) FROM audit_log
                WHERE workspace_id = ? AND created_at >= NOW() - INTERVAL '30 days'
                GROUP BY action ORDER BY 2 DESC LIMIT 8
                """, (rs, i) -> new CategoryCount(rs.getString(1), rs.getLong(2)), workspaceId);
        List<EventLite> recentEvents = jdbc.query("""
                SELECT id, action, user_id, entity_type, created_at FROM audit_log
                WHERE workspace_id = ? ORDER BY created_at DESC LIMIT 15
                """, (rs, i) -> new EventLite(
                        rs.getLong("id"), rs.getString("action"),
                        (UUID) rs.getObject("user_id"), rs.getString("entity_type"),
                        rs.getTimestamp("created_at").toInstant()),
                workspaceId);

        // Documents par type + stockage (best-effort).
        List<CategoryCount> docsByType = new ArrayList<>();
        Long storageBytes = null, storageFiles = null;
        try {
            docsByType.add(new CategoryCount("JURIDIQUE", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dataroom_documents WHERE workspace_id = ? AND is_current = true",
                    Long.class, workspaceId)));
            docsByType.add(new CategoryCount("DEPOT", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dataroom_depots WHERE workspace_id = ? AND deleted_at IS NULL",
                    Long.class, workspaceId)));
            Map<String, Object> st = jdbc.queryForMap("""
                    SELECT COALESCE(SUM(size_bytes),0) AS bytes, COUNT(*) AS n FROM (
                        SELECT size_bytes FROM dataroom_documents WHERE workspace_id = ? AND is_current = true
                        UNION ALL
                        SELECT size_bytes FROM dataroom_depots WHERE workspace_id = ? AND deleted_at IS NULL
                    ) t
                    """, workspaceId, workspaceId);
            storageBytes = ((Number) st.get("bytes")).longValue();
            storageFiles = ((Number) st.get("n")).longValue();
        } catch (RuntimeException ignored) {
            docsByType.clear();
        }

        // Tickets par statut + total + dossiers (best-effort).
        List<CategoryCount> ticketsByStatut = new ArrayList<>();
        long ticketsTotal = 0, dossiers = 0;
        try {
            ticketsByStatut = jdbc.query("""
                    SELECT statut, COUNT(*) FROM tickets WHERE workspace_id = ? GROUP BY statut ORDER BY 2 DESC
                    """, (rs, i) -> new CategoryCount(rs.getString(1), rs.getLong(2)), workspaceId);
            ticketsTotal = ticketsByStatut.stream().mapToLong(CategoryCount::count).sum();
        } catch (RuntimeException ignored) {
            ticketsByStatut = new ArrayList<>();
        }
        try {
            Long d = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?", Long.class, workspaceId);
            dossiers = d == null ? 0 : d;
        } catch (RuntimeException ignored) {
            dossiers = 0;
        }

        return base.withAggregates(employes, clients, ticketsTotal, dossiers,
                storageBytes, storageFiles, usersByRole, docsByType, ticketsByStatut,
                eventsByAction, activity30d, recentEvents);
    }

    private static Instant instant(ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @PostMapping("/{workspaceId}/users/{userId}/resend-welcome")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> resendWelcome(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest http) {
        ResendWelcomeUseCase.Result r = resendWelcomeUseCase.execute(
                workspaceId, userId,
                admin == null ? "?" : admin.email(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of(
                "emailDelivered", r.emailDelivered(),
                "message", r.emailDelivered()
                        ? "Email renvoye avec un nouveau mot de passe temporaire."
                        : "Echec d'envoi — verifier la config SMTP et reessayer."
        ));
    }

    /** ESSAI si un trial est actif, sinon le statut brut du workspace. */
    private static String computeStatut(String status, String trialStatus) {
        if ("TRIAL_ACTIVE".equals(trialStatus)) return "ESSAI";
        return status;
    }

    public record WorkspaceRow(
            UUID id,
            String code,
            String name,
            String contactEmail,
            String statut,
            String forfait,
            long employes,
            long clients,
            Long storageBytes,
            Long storageFiles,
            Instant createdAt) {
        WorkspaceRow withStorage(long bytes, long files) {
            return new WorkspaceRow(id, code, name, contactEmail, statut, forfait,
                    employes, clients, bytes, files, createdAt);
        }
    }

    public record CategoryCount(String label, long count) {}
    public record DayCount(String day, long count) {}
    public record EventLite(long id, String action, UUID userId, String entityType, Instant createdAt) {}

    public record WorkspaceDetail(
            UUID id, String code, String name, String statut, String forfait,
            String contactEmail, String city, String ice, String ifFiscal, String rcNumber,
            String adresse, String telephone, String siteWeb,
            Instant createdAt, Instant trialStartedAt, Instant trialEndsAt,
            String trialStatus, String subscriptionStatus,
            long employes, long clients, long ticketsTotal, long dossiers,
            Long storageBytes, Long storageFiles,
            List<CategoryCount> usersByRole, List<CategoryCount> docsByType,
            List<CategoryCount> ticketsByStatut, List<CategoryCount> eventsByAction,
            List<DayCount> activity30d, List<EventLite> recentEvents,
            Instant generatedAt) {

        /** Constructeur "identite seule" utilise par le RowMapper. */
        WorkspaceDetail(UUID id, String code, String name, String statut, String forfait,
                        String contactEmail, String city, String ice, String ifFiscal, String rcNumber,
                        String adresse, String telephone, String siteWeb,
                        Instant createdAt, Instant trialStartedAt, Instant trialEndsAt,
                        String trialStatus, String subscriptionStatus) {
            this(id, code, name, statut, forfait, contactEmail, city, ice, ifFiscal, rcNumber,
                    adresse, telephone, siteWeb, createdAt, trialStartedAt, trialEndsAt, trialStatus,
                    subscriptionStatus, 0, 0, 0, 0, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now());
        }

        WorkspaceDetail withAggregates(long employes, long clients, long ticketsTotal, long dossiers,
                                       Long storageBytes, Long storageFiles,
                                       List<CategoryCount> usersByRole, List<CategoryCount> docsByType,
                                       List<CategoryCount> ticketsByStatut, List<CategoryCount> eventsByAction,
                                       List<DayCount> activity30d, List<EventLite> recentEvents) {
            return new WorkspaceDetail(id, code, name, statut, forfait, contactEmail, city, ice, ifFiscal,
                    rcNumber, adresse, telephone, siteWeb, createdAt, trialStartedAt, trialEndsAt, trialStatus,
                    subscriptionStatus, employes, clients, ticketsTotal, dossiers, storageBytes, storageFiles,
                    usersByRole, docsByType, ticketsByStatut, eventsByAction, activity30d, recentEvents,
                    Instant.now());
        }
    }
}

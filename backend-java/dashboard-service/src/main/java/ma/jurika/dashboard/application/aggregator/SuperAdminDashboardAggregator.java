package ma.jurika.dashboard.application.aggregator;

import ma.jurika.dashboard.api.dto.DashboardDtos.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 10 -- aggregator SUPER_ADMIN (cross-workspace).
 * Necessite app.audit_bypass = 'true' au niveau session pour contourner la RLS.
 */
@Component
public class SuperAdminDashboardAggregator {

    /** Services dont on expose la sante sur le dashboard SUPER_ADMIN. */
    private static final List<String> MONITORED_SERVICES = List.of(
            "auth-service", "ticket-service", "workflow-service",
            "dataroom-service", "ai-service", "supervision-service",
            "realtime-service");

    private final JdbcTemplate jdbc;
    /** Optionnel : absent si le discovery client n'est pas configure (tests). */
    private final ObjectProvider<DiscoveryClient> discoveryClient;

    public SuperAdminDashboardAggregator(JdbcTemplate jdbc,
                                         ObjectProvider<DiscoveryClient> discoveryClient) {
        this.jdbc = jdbc;
        this.discoveryClient = discoveryClient;
    }

    public SuperAdminDashboardDto aggregate() {
        // Active la lecture cross-workspace (bypass RLS)
        jdbc.execute("SELECT set_config('app.audit_bypass', 'true', true)");

        Long activeWs = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT workspace_id) FROM audit_log
                WHERE action LIKE 'LOGIN%' AND created_at >= NOW() - INTERVAL '30 days'
                """, Long.class);

        // Totaux plateforme reels (alimentent les KPIs "Workspaces" et
        // "Utilisateurs plateforme" du dashboard SUPER_ADMIN).
        Long totalWs = jdbc.queryForObject("SELECT COUNT(*) FROM workspaces", Long.class);
        Long totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        List<DailyCount> signups = jdbc.query("""
                SELECT date_trunc('day', created_at)::date AS d, COUNT(*)
                FROM users
                WHERE created_at >= NOW() - INTERVAL '30 days'
                GROUP BY 1 ORDER BY 1
                """, (rs, rn) -> new DailyCount(rs.getDate(1).toLocalDate(), rs.getLong(2)));

        List<WorkspaceUsage> topWs = jdbc.query("""
                SELECT a.workspace_id, COALESCE(w.name, 'WS-' || substr(a.workspace_id::text,1,8)) AS name,
                       COUNT(*) AS events
                FROM audit_log a
                LEFT JOIN workspaces w ON w.id = a.workspace_id
                WHERE a.workspace_id IS NOT NULL
                  AND a.created_at >= NOW() - INTERVAL '7 days'
                GROUP BY a.workspace_id, w.name
                ORDER BY events DESC LIMIT 10
                """, (rs, rn) -> new WorkspaceUsage(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getLong(3)));

        List<AuditEventSummary> critiques = jdbc.query("""
                SELECT id, action, user_id, workspace_id, entity_type, created_at
                FROM audit_log
                WHERE created_at >= NOW() - INTERVAL '24 hours'
                  AND action IN ('LOGIN_FAILED','RECOVERY_CODES_REGEN','EXERCICE_UNLOCKED',
                                 'PASSWORD_CHANGED','2FA_DISABLED')
                ORDER BY created_at DESC LIMIT 20
                """, (rs, rn) -> new AuditEventSummary(
                        rs.getObject(1) instanceof Number ? null : (UUID) rs.getObject(1),
                        rs.getString(2),
                        (UUID) rs.getObject(3),
                        (UUID) rs.getObject(4),
                        rs.getString(5),
                        rs.getTimestamp(6).toInstant()));

        // Sante reelle des services : deduite du registre Eureka (une instance
        // enregistree = UP, sinon DOWN). "UNKNOWN" si le discovery est absent
        // (ex. profil de test sans Eureka) -- jamais un "UP" invente.
        Map<String, String> health = new LinkedHashMap<>();
        DiscoveryClient discovery = discoveryClient.getIfAvailable();
        for (String svc : MONITORED_SERVICES) {
            if (discovery == null) {
                health.put(svc, "UNKNOWN");
            } else {
                boolean up;
                try {
                    up = !discovery.getInstances(svc).isEmpty();
                } catch (RuntimeException e) {
                    up = false;
                }
                health.put(svc, up ? "UP" : "DOWN");
            }
        }

        // Lot 1 (2026-09-04) -- le stockage ne compte plus que le dossier juridique
        // et les depots : les tables comptable et fiscale ont ete supprimees.
        Long totalDocs = jdbc.queryForObject("""
                SELECT COALESCE(SUM(size_bytes),0) FROM (
                    SELECT size_bytes FROM dataroom_documents WHERE is_current = true
                    UNION ALL
                    SELECT size_bytes FROM dataroom_depots WHERE deleted_at IS NULL
                ) all_docs
                """, Long.class);
        Long countDocs = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM dataroom_documents WHERE is_current = true)
                     + (SELECT COUNT(*) FROM dataroom_depots WHERE deleted_at IS NULL)
                """, Long.class);
        StorageSummary storage = new StorageSummary(
                totalDocs == null ? 0 : totalDocs,
                countDocs == null ? 0 : countDocs);

        // Repartition des workspaces par forfait (essentiel / professionnel / entreprise)
        List<CategoryCount> parForfait = jdbc.query("""
                SELECT COALESCE(selected_plan, 'non_defini') AS plan, COUNT(*) AS n
                FROM workspaces
                GROUP BY COALESCE(selected_plan, 'non_defini')
                ORDER BY n DESC
                """, (rs, rn) -> new CategoryCount(rs.getString(1), rs.getLong(2)));

        // Repartition par statut (actif / essai / suspendu / desactive)
        List<CategoryCount> parStatut = jdbc.query("""
                SELECT CASE WHEN trial_status = 'TRIAL_ACTIVE' THEN 'ESSAI'
                            ELSE status END AS st,
                       COUNT(*) AS n
                FROM workspaces
                GROUP BY 1 ORDER BY n DESC
                """, (rs, rn) -> new CategoryCount(rs.getString(1), rs.getLong(2)));

        // Evolution des inscriptions (12 derniers mois, par mois de creation du workspace)
        List<MonthlyCount> signupsMensuels = jdbc.query("""
                SELECT to_char(m.mois, 'YYYY-MM') AS mois, COALESCE(w.cnt, 0) AS cnt
                FROM generate_series(
                        date_trunc('month', CURRENT_DATE) - INTERVAL '11 month',
                        date_trunc('month', CURRENT_DATE),
                        INTERVAL '1 month') AS m(mois)
                LEFT JOIN (
                    SELECT date_trunc('month', created_at) AS mois, COUNT(*) AS cnt
                    FROM workspaces
                    WHERE created_at >= date_trunc('month', CURRENT_DATE) - INTERVAL '11 month'
                    GROUP BY 1
                ) w ON w.mois = m.mois
                ORDER BY m.mois
                """, (rs, rn) -> new MonthlyCount(rs.getString(1), rs.getLong(2)));

        return new SuperAdminDashboardDto(
                activeWs == null ? 0 : activeWs,
                totalWs == null ? 0 : totalWs,
                totalUsers == null ? 0 : totalUsers,
                signups, topWs, critiques, health, storage,
                parForfait, parStatut, signupsMensuels,
                Instant.now());
    }
}

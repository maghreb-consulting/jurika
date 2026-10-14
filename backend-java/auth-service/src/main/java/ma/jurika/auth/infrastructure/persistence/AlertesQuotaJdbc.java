package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.AlertesQuotaRegistre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E9) : adaptateur JDBC du registre des alertes de quota (auth V38).
 * Appele DANS une transaction ouverte par le service, workspace pose pour la
 * RLS ; chaque requete filtre aussi explicitement sur {@code workspace_id}.
 */
@Repository
public class AlertesQuotaJdbc implements AlertesQuotaRegistre {

    private final JdbcTemplate jdbc;

    public AlertesQuotaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> cabinetsActifs() {
        return jdbc.queryForList("SELECT id FROM auth_workspaces_pour_alertes_quota()", UUID.class);
    }

    @Override
    public boolean retenirSiNouvelle(UUID workspaceId, String quota, String periode) {
        return jdbc.update("""
                INSERT INTO alertes_quota_notifiees (workspace_id, quota, periode)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, workspaceId, quota, periode) == 1;
    }

    @Override
    public List<UUID> superviseursActifs(UUID workspaceId) {
        return jdbc.queryForList("""
                SELECT id FROM users
                WHERE workspace_id = ? AND role = 'SUPERVISEUR' AND status = 'ACTIVE'
                ORDER BY created_at
                """, UUID.class, workspaceId);
    }

    @Override
    public List<SuperAdmin> superAdminsActifs() {
        return jdbc.query("SELECT id, workspace_id FROM auth_super_admins_actifs()",
                (rs, n) -> new SuperAdmin((UUID) rs.getObject("id"), (UUID) rs.getObject("workspace_id")));
    }

    @Override
    public String denominationCabinet(UUID workspaceId) {
        List<String> noms = jdbc.queryForList("SELECT name FROM workspaces WHERE id = ?", String.class, workspaceId);
        return noms.isEmpty() ? null : noms.get(0);
    }
}

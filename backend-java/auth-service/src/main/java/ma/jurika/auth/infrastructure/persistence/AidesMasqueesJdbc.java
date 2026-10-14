package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.AidesMasqueesRegistre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Lot L6 (E6) : adaptateur JDBC des aides masquees (auth V39). Appele DANS une
 * transaction ouverte par le cas d'usage, workspace pose pour la RLS ; chaque
 * requete filtre aussi explicitement sur {@code user_id} et {@code workspace_id}.
 */
@Repository
public class AidesMasqueesJdbc implements AidesMasqueesRegistre {

    private final JdbcTemplate jdbc;

    public AidesMasqueesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> cles(UUID userId, UUID workspaceId) {
        return jdbc.queryForList("""
                SELECT cle FROM aides_masquees
                WHERE user_id = ? AND workspace_id = ?
                ORDER BY cle
                """, String.class, userId, workspaceId);
    }

    @Override
    public int compter(UUID userId, UUID workspaceId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM aides_masquees WHERE user_id = ? AND workspace_id = ?",
                Integer.class, userId, workspaceId);
        return n == null ? 0 : n;
    }

    @Override
    public void masquer(UUID userId, UUID workspaceId, String cle) {
        jdbc.update("""
                INSERT INTO aides_masquees (user_id, workspace_id, cle)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, userId, workspaceId, cle);
    }

    @Override
    public void reafficher(UUID userId, UUID workspaceId, String cle) {
        jdbc.update("DELETE FROM aides_masquees WHERE user_id = ? AND workspace_id = ? AND cle = ?",
                userId, workspaceId, cle);
    }

    @Override
    public void reafficherTout(UUID userId, UUID workspaceId) {
        jdbc.update("DELETE FROM aides_masquees WHERE user_id = ? AND workspace_id = ?", userId, workspaceId);
    }
}

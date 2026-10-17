package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.PreferencesCompteRegistre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot L6b (E2) : adaptateur JDBC des preferences d'affichage du compte (auth V47). Appele DANS une
 * transaction ouverte par le cas d'usage, workspace pose pour la RLS ; chaque requete filtre aussi
 * explicitement sur {@code id} et {@code workspace_id}.
 */
@Repository
public class PreferencesCompteJdbc implements PreferencesCompteRegistre {

    private static final RowMapper<Preferences> LIGNE =
            (rs, i) -> new Preferences(rs.getString("theme"), rs.getBoolean("juri_masque"));

    private final JdbcTemplate jdbc;

    public PreferencesCompteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Preferences> lire(UUID userId, UUID workspaceId) {
        List<Preferences> l = jdbc.query(
                "SELECT theme, juri_masque FROM users WHERE id = ? AND workspace_id = ?",
                LIGNE, userId, workspaceId);
        return l.stream().findFirst();
    }

    @Override
    public Optional<Preferences> modifier(UUID userId, UUID workspaceId, String theme, Boolean juriMasque) {
        List<Preferences> l = jdbc.query("""
                UPDATE users
                   SET theme = COALESCE(?, theme),
                       juri_masque = COALESCE(?, juri_masque)
                 WHERE id = ? AND workspace_id = ?
                RETURNING theme, juri_masque
                """, LIGNE, theme, juriMasque, userId, workspaceId);
        return l.stream().findFirst();
    }
}

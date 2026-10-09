package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.port.ResolveurWorkspaceHorsContexte;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lot L0 (E13a) : appels des fonctions SECURITY DEFINER d'auth V34. */
@Component
public class ResolveurWorkspaceJdbc implements ResolveurWorkspaceHorsContexte {

    private final JdbcTemplate jdbc;

    public ResolveurWorkspaceJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> parCode(String codeWorkspace) {
        return appeler("SELECT auth_workspace_par_code(?)", codeWorkspace);
    }

    @Override
    public Optional<UUID> parEmpreinteJetonReset(String empreinte) {
        return appeler("SELECT auth_workspace_du_jeton_reset(?)", empreinte);
    }

    @Override
    public Optional<UUID> parEmpreinteJetonVerification(String empreinte) {
        return appeler("SELECT auth_workspace_du_jeton_verification(?)", empreinte);
    }

    @Override
    public Optional<UUID> parEmpreinteJetonRefresh(String empreinte) {
        return appeler("SELECT auth_workspace_du_jeton_refresh(?)", empreinte);
    }

    private Optional<UUID> appeler(String sql, String argument) {
        if (argument == null) {
            return Optional.empty();
        }
        List<UUID> resultat = jdbc.query(sql, (rs, i) -> (UUID) rs.getObject(1), argument);
        return resultat.isEmpty() ? Optional.empty() : Optional.ofNullable(resultat.get(0));
    }
}

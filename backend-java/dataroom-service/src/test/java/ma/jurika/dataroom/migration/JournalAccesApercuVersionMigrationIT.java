package ma.jurika.dataroom.migration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6b (E9) : V37 admet PREVIEW_VERSION dans le journal d'acces du client.
 *
 * <p>Avant V37, la contrainte de V21 rejetait l'apercu d'une version : la trace etait
 * perdue (avertissement seul). On rejoue V21 puis V37 sur la colonne telle que V11 la
 * cree et on insere chacune des actions emises par le code ; une action hors liste
 * reste refusee.
 */
class JournalAccesApercuVersionMigrationIT {

    private static final List<String> ACTIONS = List.of(
            "VIEW_DOSSIER", "PREVIEW_DOC", "PREVIEW_VERSION", "DOWNLOAD_DOC", "DOWNLOAD_VERSION",
            "PRINT_DOC", "DEPOT_DOC", "DEMANDE");

    @Test
    void v37_admet_l_apercu_d_une_version_et_refuse_toujours_une_action_inconnue() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement st = c.createStatement()) {
                st.execute("CREATE TABLE dataroom_client_access_log (id bigserial PRIMARY KEY, "
                        + "action VARCHAR(20) NOT NULL "
                        + "CHECK (action IN ('VIEW_DOSSIER','PREVIEW_DOC','DOWNLOAD_DOC','PRINT_DOC')))");
                st.execute(migration("V21__rg_dr_access_log_actions.sql"));
                assertThatThrownBy(() -> st.execute(
                        "INSERT INTO dataroom_client_access_log (action) VALUES ('PREVIEW_VERSION')"))
                        .as("etat d'avant V37 : l'apercu d'une version est rejete")
                        .hasMessageContaining("dataroom_client_access_log_action_check");

                st.execute(migration("V37__journal_acces_apercu_version.sql"));
                st.execute(migration("V37__journal_acces_apercu_version.sql"));
                for (String action : ACTIONS) {
                    assertThatCode(() -> st.execute(
                            "INSERT INTO dataroom_client_access_log (action) VALUES ('" + action + "')"))
                            .as(action).doesNotThrowAnyException();
                }
                assertThatThrownBy(() -> st.execute(
                        "INSERT INTO dataroom_client_access_log (action) VALUES ('AUTRE')"))
                        .hasMessageContaining("dataroom_client_access_log_action_check");
            }
        }
    }

    private static String migration(String nom) throws Exception {
        try (InputStream in = JournalAccesApercuVersionMigrationIT.class
                .getResourceAsStream("/db/migration/" + nom)) {
            if (in == null) throw new IllegalStateException("Migration absente : " + nom);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

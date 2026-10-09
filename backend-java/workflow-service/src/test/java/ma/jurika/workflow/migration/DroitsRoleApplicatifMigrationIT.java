package ma.jurika.workflow.migration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E10 : migration V14__droits_role_applicatif.sql.
 *
 * <p>Elle accorde au role d'execution {@code jurika_app} les seuls droits DML
 * (tables et sequences, existantes et futures), refuse de passer si le role est
 * absent, et ne lui donne aucun droit sur l'historique Flyway. Chaque droit est
 * verifie SEPAREMENT ({@code has_table_privilege(r, t, 'A,B')} est vrai des
 * qu'un seul des droits listes est detenu).
 */
class DroitsRoleApplicatifMigrationIT {

    private static final String MIGRATION = "/db/migration/V14__droits_role_applicatif.sql";

    @Test
    void echoue_explicitement_si_jurika_app_est_absent() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            try (Connection c = proprietaire(pg); Statement st = c.createStatement()) {
                assertThatThrownBy(() -> st.execute(migration()))
                        .hasMessageContaining("jurika_app absent");
            }
        }
    }

    @Test
    void accorde_les_droits_dml_seulement_et_reste_idempotente() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            try (Connection c = proprietaire(pg); Statement st = c.createStatement()) {
                st.execute("CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'x'");
                st.execute("CREATE TABLE existante (id bigserial PRIMARY KEY, v text)");
                st.execute("CREATE TABLE flyway_history_workflow (installed_rank int PRIMARY KEY)");
                st.execute("CREATE TABLE flyway_history_autre (installed_rank int PRIMARY KEY)");

                st.execute(migration());
                st.execute(migration());

                st.execute("CREATE TABLE future (id bigserial PRIMARY KEY, v text)");

                for (String table : new String[]{"existante", "future"}) {
                    for (String droit : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
                        assertThat(privilege(c, table, droit)).as("%s sur %s", droit, table).isTrue();
                    }
                    for (String droit : new String[]{"TRUNCATE", "REFERENCES", "TRIGGER"}) {
                        assertThat(privilege(c, table, droit)).as("%s sur %s", droit, table).isFalse();
                    }
                    assertThat(booleen(c, "SELECT has_sequence_privilege('jurika_app', '"
                            + table + "_id_seq', 'USAGE')")).as("sequence de %s", table).isTrue();
                }
                for (String historique : new String[]{"flyway_history_workflow", "flyway_history_autre"}) {
                    for (String droit : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
                        assertThat(privilege(c, historique, droit)).as("%s sur %s", droit, historique).isFalse();
                    }
                }
            }
        }
    }

    private static String migration() throws Exception {
        try (InputStream in = DroitsRoleApplicatifMigrationIT.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as(MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Connection proprietaire(PostgreSQLContainer<?> pg) throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private static boolean privilege(Connection c, String table, String droit) throws Exception {
        return booleen(c, "SELECT has_table_privilege('jurika_app', '" + table + "', '" + droit + "')");
    }

    private static boolean booleen(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getBoolean(1);
        }
    }
}

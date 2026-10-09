package ma.jurika.common.persistence;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E8 : les deux scripts qui preparent le role d'execution
 * {@code jurika_app} (non proprietaire, NOSUPERUSER, NOBYPASSRLS).
 *
 * <ul>
 *   <li>{@code infrastructure/scripts/init-db.sh} : volume neuf (image de la pile) ;</li>
 *   <li>{@code infrastructure/scripts/rattrapage-role-applicatif.sh} : base existante,
 *       idempotent, y compris quand le role existe deja avec de mauvais attributs.</li>
 * </ul>
 *
 * Chaque droit est verifie SEPAREMENT : {@code has_table_privilege(r, t, 'A,B')}
 * est vrai des qu'un seul des droits listes est detenu.
 */
class RoleApplicatifScriptsIT {

    private static final Path SCRIPTS = Path.of("..", "..", "infrastructure", "scripts");
    private static final String MOT_DE_PASSE_APP = "mdp-app-L0-test";

    @Test
    void init_db_cree_jurika_app_sans_superutilisateur_ni_contournement_de_la_rls() throws Exception {
        Path initDb = SCRIPTS.resolve("init-db.sh");
        assertThat(Files.exists(initDb)).as(initDb.toAbsolutePath().toString()).isTrue();

        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("jurika_db")
                .withUsername("jurika_user")
                .withPassword("mdp-proprietaire")
                .withEnv("JURIKA_APP_PASSWORD", MOT_DE_PASSE_APP)
                .withCopyFileToContainer(MountableFile.forHostPath(initDb),
                        "/docker-entrypoint-initdb.d/init-db.sh")) {
            pg.start();

            try (Connection c = proprietaire(pg)) {
                assertThat(attributs(c)).isEqualTo("f|f|t");
                assertThat(une(c, "SELECT count(*) FROM pg_database WHERE datname = 'jurika_billing'"))
                        .isEqualTo("1");
                // Table creee APRES l'init par le proprietaire (cas de Flyway) :
                // les droits par defaut s'appliquent.
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE apres_init (id bigserial primary key, v text)");
                }
                assertDroitsDml(c, "apres_init");
            }
            try (Connection app = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", MOT_DE_PASSE_APP)) {
                assertThat(une(app, "SELECT current_user")).isEqualTo("jurika_app");
            }
        }
    }

    @Test
    void init_db_echoue_sans_mot_de_passe_dedie() throws Exception {
        Path initDb = SCRIPTS.resolve("init-db.sh");
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            pg.copyFileToContainer(MountableFile.forHostPath(initDb), "/tmp/init-db.sh");
            ExecResult r = pg.execInContainer("env", "-u", "JURIKA_APP_PASSWORD",
                    "POSTGRES_USER=" + pg.getUsername(), "POSTGRES_DB=" + pg.getDatabaseName(),
                    "bash", "/tmp/init-db.sh");
            assertThat(r.getExitCode()).isNotZero();
            assertThat(r.getStderr()).contains("JURIKA_APP_PASSWORD est obligatoire");
        }
    }

    @Test
    void rattrapage_corrige_un_role_existant_et_reste_idempotent() throws Exception {
        Path script = SCRIPTS.resolve("rattrapage-role-applicatif.sh");
        assertThat(Files.exists(script)).as(script.toAbsolutePath().toString()).isTrue();

        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("jurika_db")) {
            pg.start();
            try (Connection c = proprietaire(pg); Statement st = c.createStatement()) {
                // Pire cas : role deja present, avec les attributs qu'on veut retirer.
                st.execute("CREATE ROLE jurika_app WITH LOGIN SUPERUSER BYPASSRLS PASSWORD 'ancien'");
                st.execute("CREATE TABLE avant (id bigserial primary key, v text)");
            }
            pg.copyFileToContainer(MountableFile.forHostPath(script), "/tmp/rattrapage.sh");

            for (int passage = 1; passage <= 2; passage++) {
                ExecResult r = rattrapage(pg, MOT_DE_PASSE_APP);
                assertThat(r.getExitCode()).as("passage %d : %s", passage, r.getStderr()).isZero();
            }

            try (Connection c = proprietaire(pg)) {
                assertThat(attributs(c)).isEqualTo("f|f|t");
                assertDroitsDml(c, "avant");
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE apres (id bigserial primary key, v text)");
                }
                assertDroitsDml(c, "apres");
            }
            try (Connection app = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", MOT_DE_PASSE_APP)) {
                assertThat(une(app, "SELECT current_user")).isEqualTo("jurika_app");
            }
        }
    }

    @Test
    void rattrapage_echoue_sans_mot_de_passe_et_ne_touche_a_rien() throws Exception {
        Path script = SCRIPTS.resolve("rattrapage-role-applicatif.sh");
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            pg.copyFileToContainer(MountableFile.forHostPath(script), "/tmp/rattrapage.sh");
            ExecResult r = pg.execInContainer("env", "-u", "JURIKA_APP_PASSWORD",
                    "PGUSER=" + pg.getUsername(), "PGPASSWORD=" + pg.getPassword(),
                    "bash", "/tmp/rattrapage.sh", pg.getDatabaseName());
            assertThat(r.getExitCode()).isNotZero();
            assertThat(r.getStderr()).contains("JURIKA_APP_PASSWORD est obligatoire");
            try (Connection c = proprietaire(pg)) {
                assertThat(une(c, "SELECT count(*) FROM pg_roles WHERE rolname = 'jurika_app'")).isEqualTo("0");
            }
        }
    }

    private static ExecResult rattrapage(PostgreSQLContainer<?> pg, String motDePasse) throws Exception {
        return pg.execInContainer("env",
                "PGUSER=" + pg.getUsername(), "PGPASSWORD=" + pg.getPassword(),
                "JURIKA_APP_PASSWORD=" + motDePasse,
                "bash", "/tmp/rattrapage.sh", pg.getDatabaseName());
    }

    private static Connection proprietaire(PostgreSQLContainer<?> pg) throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private static String attributs(Connection c) throws Exception {
        return une(c, "SELECT rolsuper::text || '|' || rolbypassrls::text || '|' || rolcanlogin::text "
                + "FROM pg_roles WHERE rolname = 'jurika_app'")
                .replace("true", "t").replace("false", "f");
    }

    private static void assertDroitsDml(Connection c, String table) throws Exception {
        for (String droit : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"}) {
            assertThat(une(c, "SELECT has_table_privilege('jurika_app', '" + table + "', '" + droit + "')"))
                    .as("%s sur %s", droit, table).isEqualTo("t");
        }
        for (String droit : new String[]{"TRUNCATE", "REFERENCES", "TRIGGER"}) {
            assertThat(une(c, "SELECT has_table_privilege('jurika_app', '" + table + "', '" + droit + "')"))
                    .as("%s sur %s (interdit)", droit, table).isEqualTo("f");
        }
        assertThat(une(c, "SELECT has_sequence_privilege('jurika_app', '" + table + "_id_seq', 'USAGE')"))
                .as("USAGE sur la sequence de %s", table).isEqualTo("t");
    }

    private static String une(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as(sql).isTrue();
            String v = rs.getString(1);
            return "true".equals(v) ? "t" : "false".equals(v) ? "f" : v;
        }
    }
}

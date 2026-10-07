package ma.jurika.auth.integration;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import ma.jurika.auth.infrastructure.persistence.UserEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E12 : preuve de la RLS de auth-service en role d'execution
 * {@code jurika_app} (non proprietaire, NOSUPERUSER, NOBYPASSRLS), avec le
 * mecanisme de production : gestionnaire de transactions de jurika-common
 * (workspace pose a l'ouverture) et garde « hors transaction ». Table temoin :
 * {@code users}.
 *
 * <p>Schema : les VRAIES migrations, dans l'ordre de la base partagee
 * jurika_db (auth), chacune avec son historique Flyway.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsRoleApplicatifIT {

    /** Workspace seme par auth V2 (JUR-DEMO1) et son employe seme par auth V3. */
    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b3");
    private static final UUID LIGNE_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000e001");
    private static final UUID LIGNE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000e002");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_rls_auth")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        migrer("classpath:db/migration", "flyway_history_auth", null);
        try (Connection owner = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = owner.createStatement()) {
            // Second cabinet : clone du workspace et de l'employe semes par auth.
            st.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS_A + "'");
            st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-RLSBB'");
            st.execute("INSERT INTO workspaces SELECT * FROM w");
            st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + USER_A + "'");
            st.execute("UPDATE u SET id = '" + USER_B + "', workspace_id = '" + WS_B
                    + "', email = 'b@rls.test', login_email = 'b@rls.test'");
            st.execute("INSERT INTO users SELECT * FROM u");
            st.execute("CREATE TEMP TABLE u2 AS SELECT * FROM users WHERE id = '33333333-3333-3333-3333-333333333333'");
            st.execute("UPDATE u2 SET id = 'aaaaaaaa-0000-0000-0000-00000000e001', email = 'a@rls.test', login_email = 'a@rls.test'");
            st.execute("INSERT INTO users SELECT * FROM u2");
            st.execute("UPDATE u2 SET id = 'bbbbbbbb-0000-0000-0000-00000000e002', workspace_id = 'bbbbbbbb-0000-0000-0000-000000000002', email = 'b2@rls.test', login_email = 'b2@rls.test'");
            st.execute("INSERT INTO users SELECT * FROM u2");
        }

        app = new HikariDataSource();
        app.setJdbcUrl(pg.getJdbcUrl());
        app.setUsername("jurika_app");
        app.setPassword("jurika_app_it");
        // Meme garde que TenantTransactionAutoConfiguration (jurika-common).
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");

        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(UserEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        emf = fabrique.getObject();
        tx = new TransactionTemplate(new TenantAwareJpaTransactionManager(emf));
        jdbc = new JdbcTemplate(app);
    }

    /**
     * Une passe Flyway sur la base partagee : historique propre au service,
     * baseline en 0 (la V1 d'un service n'est jamais sautee, cf. CLAUDE.md).
     */
    private void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(emplacement)
                .table(historique)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (cible != null) {
            config.target(cible);
        }
        config.load().migrate();
    }

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    @AfterAll
    void arreter() {
        if (emf != null) emf.close();
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void avec_le_workspace_a_seules_les_lignes_de_a_sont_visibles() {
        assertThat(idsVus(WS_A)).containsExactly(LIGNE_A);
        assertThat(idsVus(WS_B)).containsExactly(LIGNE_B);
    }

    @Test
    void ecrire_dans_le_workspace_d_un_autre_cabinet_est_refuse() {
        TenantContext.set(WS_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbc.update(
                "UPDATE users SET workspace_id = ? WHERE id = ?", WS_B, LIGNE_A)))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void sans_workspace_la_lecture_echoue_au_lieu_de_renvoyer_zero_ligne() {
        assertThatThrownBy(() -> tx.execute(s -> jdbc.queryForList("SELECT id FROM users", UUID.class)))
                .hasStackTraceContaining("hors-transaction");
        assertThatThrownBy(() -> jdbc.queryForList("SELECT id FROM users", UUID.class))
                .hasStackTraceContaining("hors-transaction");
    }

    private List<UUID> idsVus(UUID workspace) {
        TenantContext.set(workspace);
        try {
            return tx.execute(s -> jdbc.queryForList("SELECT id FROM users WHERE id IN (?, ?)",
                    UUID.class, LIGNE_A, LIGNE_B));
        } finally {
            TenantContext.clear();
        }
    }
}

package ma.jurika.auth.infrastructure.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E10b (inventaire E1, AU1). AuditLogAdapter ecrit depuis un fil
 * asynchrone, sans workspace courant. En JPA, l'identifiant IDENTITY est relu
 * par {@code INSERT ... RETURNING}, qui soumet la ligne aux politiques SELECT
 * d'audit_log : une ligne portant un workspace est REFUSEE et la trace perdue.
 * Un INSERT simple releve de la politique {@code audit_log_insert WITH CHECK (true)}.
 *
 * <p>Schema reel : toutes les migrations d'auth (V32 comprise), role
 * d'execution jurika_app avec la garde « hors transaction » de jurika-common.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditLogAdapterRlsIT {

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_audit_rls")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;

    @BeforeAll
    void demarrer() {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_history_auth")
                .load()
                .migrate();
        app = new HikariDataSource();
        app.setJdbcUrl(pg.getJdbcUrl());
        app.setUsername("jurika_app");
        app.setPassword("jurika_app_it");
        // Meme garde que TenantTransactionAutoConfiguration (jurika-common).
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");
    }

    private long compterEnProprietaire(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = owner.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    @AfterAll
    void arreter() {
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void caracterisation_l_ancienne_ecriture_jpa_perdait_la_trace() throws Exception {
        // Reproduit l'ancien AuditLogAdapter : persist JPA d'AuditLogEntity
        // (identifiant IDENTITY, relu par INSERT ... RETURNING), sur un fil sans
        // workspace courant, en role jurika_app.
        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(AuditLogEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        EntityManagerFactory emf = fabrique.getObject();
        try {
            EntityManager em = emf.createEntityManager();
            AuditLogEntity trace = new AuditLogEntity();
            trace.setWorkspaceId(UUID.randomUUID());
            trace.setAction("L0_E10B_JPA");
            em.getTransaction().begin();
            assertThatThrownBy(() -> {
                em.persist(trace);
                em.flush();
            }).hasStackTraceContaining("hors-transaction");
            em.getTransaction().rollback();
            em.close();
        } finally {
            emf.close();
        }
        // La trace n'existe pas : verifie avec le compte PROPRIETAIRE (hors RLS).
        assertThat(compterEnProprietaire("SELECT count(*) FROM audit_log WHERE action = 'L0_E10B_JPA'"))
                .isZero();
    }

    @Test
    void l_adaptateur_ecrit_la_trace_sans_workspace_courant() throws Exception {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID entite = UUID.randomUUID();
        AuditLogAdapter adapter = new AuditLogAdapter(new JdbcTemplate(app), new ObjectMapper());

        adapter.log(ws, user, "L0_E10B_AVEC_WS", "USER", entite, "10.0.0.1", "test",
                Map.of("cle", "valeur"));
        adapter.log(null, null, "L0_E10B_SANS_WS", null, null, null, null, null);

        try (Connection owner = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = owner.createStatement();
             ResultSet rs = st.executeQuery("SELECT workspace_id, user_id, entity_type, entity_id, "
                     + "ip_address, user_agent, metadata->>'cle', created_at FROM audit_log "
                     + "WHERE action = 'L0_E10B_AVEC_WS'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject(1)).isEqualTo(ws);
            assertThat(rs.getObject(2)).isEqualTo(user);
            assertThat(rs.getString(3)).isEqualTo("USER");
            assertThat(rs.getObject(4)).isEqualTo(entite);
            assertThat(rs.getString(5)).isEqualTo("10.0.0.1");
            assertThat(rs.getString(6)).isEqualTo("test");
            assertThat(rs.getString(7)).isEqualTo("valeur");
            assertThat(rs.getTimestamp(8)).isNotNull();
        }
        try (Connection owner = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = owner.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM audit_log WHERE action = 'L0_E10B_SANS_WS' "
                     + "AND workspace_id IS NULL AND metadata IS NULL")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}

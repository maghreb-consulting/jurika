package ma.jurika.ticket.integration;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.audit.AuditEventConsumer;
import ma.jurika.common.audit.AuditEventEmitter.AuditEvent;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.infrastructure.persistence.SuccursaleEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E14 : chemin ASYNCHRONE reel du journal d'audit. Le
 * consommateur RabbitMQ (AuditEventConsumer, jurika-common) recoit un message
 * sur un fil SANS workspace courant et ecrit la trace en role jurika_app, dans
 * sa transaction REQUIRES_NEW ouverte par le gestionnaire de production, avec
 * la garde « hors transaction ». Le consommateur avale toute exception : seule
 * la ligne en base prouve que la trace n'est pas perdue en silence.
 *
 * <p>Schema d'audit reel : migrations d'auth (audit_log, ses trois politiques,
 * FORCE ROW LEVEL SECURITY). Le test de bout en bout de ticket (TicketJurikaAppIT)
 * ecrit l'audit en JDBC direct ; c'est ce test-ci qui couvre la voie RabbitMQ.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditConsommateurRlsIT {

    private static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_audit_consommateur")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate nouvelleTransaction;
    private AuditEventConsumer consommateur;
    private JdbcTemplate owner;

    @BeforeAll
    void demarrer() {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("filesystem:../auth-service/src/main/resources/db/migration")
                .table("flyway_history_auth")
                .baselineOnMigrate(true).baselineVersion("0")
                .load().migrate();
        owner = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));

        app = new HikariDataSource();
        app.setJdbcUrl(pg.getJdbcUrl());
        app.setUsername("jurika_app");
        app.setPassword("jurika_app_it");
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");

        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(SuccursaleEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        emf = fabrique.getObject();

        // Comme le proxy @Transactional(propagation = REQUIRES_NEW) du consommateur.
        nouvelleTransaction = new TransactionTemplate(new TenantAwareJpaTransactionManager(emf));
        nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        consommateur = new AuditEventConsumer(new JdbcTemplate(app));
    }

    @AfterAll
    void arreter() {
        if (emf != null) emf.close();
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void message_sans_workspace_courant_trace_ecrite_avec_son_workspace() {
        TenantContext.clear();
        UUID acteur = UUID.randomUUID();
        UUID ressource = UUID.randomUUID();
        nouvelleTransaction.executeWithoutResult(s -> consommateur.onMessage(new AuditEvent(
                WS, acteur, "L0_E14_CONSOMMATEUR", "TICKET", ressource,
                Map.of("cle", "valeur"), "correlation-l0", "ticket-service")));

        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'L0_E14_CONSOMMATEUR' AND workspace_id = ? "
                        + "AND user_id = ? AND entity_id = ? AND source_service = 'ticket-service' "
                        + "AND metadata->>'cle' = 'valeur'",
                Integer.class, WS, acteur, ressource)).isEqualTo(1);
    }

    @Test
    void message_sans_workspace_du_tout_trace_ecrite() {
        TenantContext.clear();
        nouvelleTransaction.executeWithoutResult(s -> consommateur.onMessage(new AuditEvent(
                null, null, "L0_E14_SANS_WS", null, null, null, null, "ticket-service")));

        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'L0_E14_SANS_WS' AND workspace_id IS NULL",
                Integer.class)).isEqualTo(1);
    }
}

package ma.jurika.ticket.integration;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.infrastructure.DemoDataSeeder;
import ma.jurika.ticket.infrastructure.persistence.SuccursaleEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E14 (inventaire T1) : le seeder de demonstration tourne au
 * demarrage, sur le fil principal, SANS workspace courant. Il posait le
 * workspace APRES un premier comptage sur workspaces (sous RLS) et avale ses
 * erreurs ("non-blocking") : en jurika_app, le seed echouait en silence. Le
 * test l'execute en jurika_app, dans une transaction du gestionnaire de
 * production, et attend des LIGNES.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoDataSeederRlsIT {

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_seed")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;

    @BeforeAll
    void demarrer() {
        pg.start();
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        migrer("classpath:db/migration", "flyway_history_ticket", "20");
        migrer("filesystem:../dataroom-service/src/main/resources/db/migration", "flyway_history_dataroom", null);
        migrer("classpath:db/migration", "flyway_history_ticket", null);
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
    }

    private void migrer(String emplacement, String historique, String cible) {
        var config = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(emplacement).table(historique)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    @AfterAll
    void arreter() {
        if (emf != null) emf.close();
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void le_seed_de_demonstration_ecrit_ses_dossiers_et_tickets_sans_workspace_courant() {
        DemoDataSeeder seeder = new DemoDataSeeder();
        ReflectionTestUtils.setField(seeder, "em", SharedEntityManagerCreator.createSharedEntityManager(emf));
        ReflectionTestUtils.setField(seeder, "enabled", true);
        TenantContext.clear();
        // Comme au demarrage : run() est @Transactional, sans TenantContext.
        new TransactionTemplate(new TenantAwareJpaTransactionManager(emf))
                .executeWithoutResult(s -> seeder.run());

        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = '11111111-1111-1111-1111-111111111111'",
                Integer.class)).isPositive();
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = '11111111-1111-1111-1111-111111111111'",
                Integer.class)).isPositive();
    }
}

package ma.jurika.auth.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (exploration de bout en bout sur le Z440, 2026-10-16) : la page Equipe du superviseur
 * recevait un 500 sur GET /api/v1/auth/users/droit-suppression-dataroom. Le controleur appelle
 * l'adaptateur directement, sans transaction : le contexte du cabinet (RLS) n'etait pas pose et la
 * connexion gardait la sentinelle 'hors-transaction' ("invalid input syntax for type uuid").
 *
 * L'adaptateur reel, derriere le meme proxy transactionnel que dans l'application, en role
 * jurika_app sous RLS : les lectures doivent fonctionner appelees hors transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DroitSuppressionDataroomRlsIT {

    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_droit_suppression")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private DroitSuppressionDataroomRepository droits;

    @BeforeAll
    void demarrer() throws Exception {
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
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");
        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(UserEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        emf = fabrique.getObject();

        DroitSuppressionDataroomRepositoryAdapter adaptateur = new DroitSuppressionDataroomRepositoryAdapter();
        EntityManager partage = SharedEntityManagerCreator.createSharedEntityManager(emf);
        for (Field f : DroitSuppressionDataroomRepositoryAdapter.class.getDeclaredFields()) {
            if (EntityManager.class.isAssignableFrom(f.getType())) { f.setAccessible(true); f.set(adaptateur, partage); }
        }
        ProxyFactory proxy = new ProxyFactory(adaptateur);
        proxy.addAdvice(new TransactionInterceptor(new TenantAwareJpaTransactionManager(emf),
                new AnnotationTransactionAttributeSource()));
        droits = (DroitSuppressionDataroomRepository) proxy.getProxy();
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
    void la_liste_des_employes_avec_le_droit_se_lit_hors_transaction_de_l_appelant() {
        TenantContext.set(WS_A);
        assertThat(droits.employesAvecLeDroit(WS_A)).isNotNull();
    }

    @Test
    void le_droit_d_un_employe_se_lit_hors_transaction_de_l_appelant() {
        TenantContext.set(WS_A);
        assertThat(droits.lire(WS_A, UUID.fromString("33333333-3333-3333-3333-333333333333"))).isFalse();
    }
}

package ma.jurika.auth.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0 (E10d) : anti-rejeu TOTP et usage unique des codes de secours, avec les
 * VRAIES requetes des depots Spring Data, sur le schema reel d'auth (Flyway
 * jusqu'a V33), en role jurika_app sous RLS, avec le gestionnaire de
 * transactions de jurika-common (workspace pose a l'ouverture) et la garde
 * « hors transaction ».
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AntiRejeuRlsIT {

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_anti_rejeu")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private UserJpaRepository users;
    private RecoveryCodeJpaRepository codes;
    private UUID workspaceId;
    private UUID userId;

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_history_auth")
                .load()
                .migrate();
        try (Connection owner = proprietaire(); Statement st = owner.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, workspace_id FROM users ORDER BY created_at LIMIT 1")) {
            assertThat(rs.next()).as("utilisateur seme par les migrations").isTrue();
            userId = (UUID) rs.getObject(1);
            workspaceId = (UUID) rs.getObject(2);
        }

        app = new HikariDataSource();
        app.setJdbcUrl(pg.getJdbcUrl());
        app.setUsername("jurika_app");
        app.setPassword("jurika_app_it");
        app.setMaximumPoolSize(4);
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");

        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(UserEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        emf = fabrique.getObject();

        tx = new TransactionTemplate(new TenantAwareJpaTransactionManager(emf));
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaRepositoryFactory depots = new JpaRepositoryFactory(em);
        users = depots.getRepository(UserJpaRepository.class);
        codes = depots.getRepository(RecoveryCodeJpaRepository.class);
    }

    @AfterEach
    void viderContexte() {
        TenantContext.clear();
    }

    @AfterAll
    void arreter() {
        if (emf != null) emf.close();
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void un_pas_totp_rejoue_ou_anterieur_est_refuse() {
        assertThat(consommerPas(1_000L)).isEqualTo(1);
        assertThat(consommerPas(1_000L)).as("rejeu du meme pas").isZero();
        assertThat(consommerPas(999L)).as("pas anterieur").isZero();
        assertThat(consommerPas(1_001L)).as("pas suivant").isEqualTo(1);
    }

    @Test
    void deux_requetes_simultanees_avec_le_meme_pas_une_seule_acceptee() throws Exception {
        List<Integer> resultats = enParallele(() -> consommerPas(2_000L));
        assertThat(resultats).containsExactlyInAnyOrder(1, 0);
    }

    @Test
    void deux_requetes_simultanees_avec_le_meme_code_de_secours_une_seule_acceptee() throws Exception {
        UUID code = UUID.randomUUID();
        try (Connection owner = proprietaire(); Statement st = owner.createStatement()) {
            st.execute("INSERT INTO recovery_codes (id, workspace_id, user_id, code_hash) VALUES ('"
                    + code + "', '" + workspaceId + "', '" + userId + "', 'empreinte')");
        }
        List<Integer> resultats = enParallele(() -> {
            TenantContext.set(workspaceId);
            try {
                return tx.execute(s -> codes.consommer(code, Instant.now()));
            } finally {
                TenantContext.clear();
            }
        });
        assertThat(resultats).containsExactlyInAnyOrder(1, 0);
        TenantContext.set(workspaceId);
        Integer encore = tx.execute(s -> codes.consommer(code, Instant.now()));
        assertThat(encore).as("code deja utilise").isZero();
    }

    @Test
    void sans_workspace_la_requete_echoue_au_lieu_de_passer_en_silence() {
        assertThatThrownBy(() -> tx.execute(s -> users.consommerPasTotp(userId, 3_000L)))
                .hasStackTraceContaining("hors-transaction");
    }

    private int consommerPas(long pas) {
        TenantContext.set(workspaceId);
        try {
            return tx.execute(s -> users.consommerPasTotp(userId, pas));
        } finally {
            TenantContext.clear();
        }
    }

    /** Deux appels lances au meme instant (barriere), chacun dans sa transaction. */
    private static List<Integer> enParallele(Supplier<Integer> appel) throws Exception {
        CyclicBarrier depart = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> { depart.await(); return appel.get(); });
            Future<Integer> b = pool.submit(() -> { depart.await(); return appel.get(); });
            return List.of(a.get(), b.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Connection proprietaire() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }
}

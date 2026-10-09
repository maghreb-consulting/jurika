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

/**
 * Lot L0 (E12b) : usage unique ATOMIQUE des OTP SMS, des liens de verification
 * d'email et des jetons de reinitialisation de mot de passe, avec les VRAIES
 * requetes des depots Spring Data, sur le schema reel d'auth, en role
 * jurika_app sous RLS, avec le gestionnaire de transactions de jurika-common.
 * Un jeton rejoue est refuse ; deux requetes simultanees : une seule acceptee.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JetonsUsageUniqueRlsIT {

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_jetons")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private SmsOtpCodeJpaRepository otps;
    private EmailVerificationTokenJpaRepository verifications;
    private PasswordResetTokenJpaRepository reinitialisations;
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
        otps = depots.getRepository(SmsOtpCodeJpaRepository.class);
        verifications = depots.getRepository(EmailVerificationTokenJpaRepository.class);
        reinitialisations = depots.getRepository(PasswordResetTokenJpaRepository.class);
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
    void otp_sms_rejoue_refuse_et_concurrence_une_seule_acceptation() throws Exception {
        UUID rejoue = inserer("INSERT INTO sms_otp_codes (id, workspace_id, user_id, code_hash, phone_e164, purpose, expires_at) "
                + "VALUES (?, ?, ?, 'h', '+212600000000', '2FA_LOGIN', NOW() + INTERVAL '5 minutes')");
        assertThat(dansLeWorkspace(() -> otps.consommer(rejoue, Instant.now()))).isEqualTo(1);
        assertThat(dansLeWorkspace(() -> otps.consommer(rejoue, Instant.now()))).as("rejeu").isZero();

        UUID concurrent = inserer("INSERT INTO sms_otp_codes (id, workspace_id, user_id, code_hash, phone_e164, purpose, expires_at) "
                + "VALUES (?, ?, ?, 'h', '+212600000000', '2FA_LOGIN', NOW() + INTERVAL '5 minutes')");
        assertThat(enParallele(() -> dansLeWorkspace(() -> otps.consommer(concurrent, Instant.now()))))
                .containsExactlyInAnyOrder(1, 0);
    }

    @Test
    void lien_de_verification_rejoue_refuse_et_concurrence_une_seule_acceptation() throws Exception {
        UUID rejoue = inserer("INSERT INTO email_verification_tokens (id, workspace_id, user_id, token_hash, email, expires_at) "
                + "VALUES (?, ?, ?, md5(random()::text), 'v@rls.test', NOW() + INTERVAL '1 day')");
        assertThat(dansLeWorkspace(() -> verifications.consommer(rejoue, Instant.now()))).isEqualTo(1);
        assertThat(dansLeWorkspace(() -> verifications.consommer(rejoue, Instant.now()))).as("rejeu").isZero();

        UUID concurrent = inserer("INSERT INTO email_verification_tokens (id, workspace_id, user_id, token_hash, email, expires_at) "
                + "VALUES (?, ?, ?, md5(random()::text), 'v@rls.test', NOW() + INTERVAL '1 day')");
        assertThat(enParallele(() -> dansLeWorkspace(() -> verifications.consommer(concurrent, Instant.now()))))
                .containsExactlyInAnyOrder(1, 0);
    }

    @Test
    void jeton_de_reinitialisation_rejoue_refuse_et_concurrence_une_seule_acceptation() throws Exception {
        String rejoue = "empreinte-" + UUID.randomUUID();
        inserer("INSERT INTO password_reset_tokens (id, workspace_id, user_id, token_hash, expires_at) "
                + "VALUES (?, ?, ?, '" + rejoue + "', NOW() + INTERVAL '1 hour')");
        assertThat(dansLeWorkspace(() -> reinitialisations.markUsed(rejoue, Instant.now()))).isEqualTo(1);
        assertThat(dansLeWorkspace(() -> reinitialisations.markUsed(rejoue, Instant.now()))).as("rejeu").isZero();

        String concurrent = "empreinte-" + UUID.randomUUID();
        inserer("INSERT INTO password_reset_tokens (id, workspace_id, user_id, token_hash, expires_at) "
                + "VALUES (?, ?, ?, '" + concurrent + "', NOW() + INTERVAL '1 hour')");
        assertThat(enParallele(() -> dansLeWorkspace(() -> reinitialisations.markUsed(concurrent, Instant.now()))))
                .containsExactlyInAnyOrder(1, 0);
    }

    /** Insere une ligne en PROPRIETAIRE (id, workspace, utilisateur) ; renvoie son id. */
    private UUID inserer(String sql) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection owner = proprietaire(); var ps = owner.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
        return id;
    }

    private int dansLeWorkspace(Supplier<Integer> consommation) {
        TenantContext.set(workspaceId);
        try {
            return tx.execute(s -> consommation.get());
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

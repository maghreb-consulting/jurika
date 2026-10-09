package ma.jurika.dataroom.integration;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E11 : preuve de la RLS de dataroom en role d'execution
 * {@code jurika_app} (non proprietaire, NOSUPERUSER, NOBYPASSRLS), sur le schema
 * reel (vraies migrations amont, SchemaJurikaDb) et avec le mecanisme de production : gestionnaire
 * de transactions de jurika-common (workspace pose a l'ouverture) et garde
 * « hors transaction ». Les autres IT de dataroom restent en proprietaire
 * jusqu'a la bascule du service (E15).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsRoleApplicatifIT {

    private static final UUID WS_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID DOSSIER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000d001");
    private static final UUID DOSSIER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000d002");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_rls_dataroom")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private SettingsJpaRepository settings;

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        // Lot L0 (E15) : vraies migrations amont (SchemaJurikaDb), plus de tables simulees.
        SchemaJurikaDb.migrer(pg);
        JdbcTemplate owner = SchemaJurikaDb.proprietaire(pg);
        SchemaJurikaDb.workspace(owner, WS_A, "Cabinet A", "JUR-RLSAA");
        SchemaJurikaDb.workspace(owner, WS_B, "Cabinet B", "JUR-RLSBB");
        owner.update("INSERT INTO entreprise_dossiers (id, workspace_id, raison_sociale, forme_juridique) VALUES "
                + "(?, ?, 'Societe A', 'SARL'), (?, ?, 'Societe B', 'SARL')", DOSSIER_A, WS_A, DOSSIER_B, WS_B);
        owner.update("INSERT INTO dataroom_settings (dossier_id, workspace_id) VALUES (?, ?), (?, ?)",
                DOSSIER_A, WS_A, DOSSIER_B, WS_B);

        app = new HikariDataSource();
        app.setJdbcUrl(pg.getJdbcUrl());
        app.setUsername("jurika_app");
        app.setPassword("jurika_app_it");
        // Meme garde que TenantTransactionAutoConfiguration (jurika-common).
        app.setConnectionInitSql("SET app.current_workspace_id = 'hors-transaction'");

        LocalContainerEntityManagerFactoryBean fabrique = new LocalContainerEntityManagerFactoryBean();
        fabrique.setDataSource(app);
        fabrique.setPackagesToScan(SettingsEntity.class.getPackageName());
        fabrique.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        fabrique.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        fabrique.afterPropertiesSet();
        emf = fabrique.getObject();

        tx = new TransactionTemplate(new TenantAwareJpaTransactionManager(emf));
        jdbc = new JdbcTemplate(app);
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        settings = new JpaRepositoryFactory(em).getRepository(SettingsJpaRepository.class);
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
        TenantContext.set(WS_A);
        List<UUID> vus = tx.execute(s -> settings.findAll().stream().map(SettingsEntity::getDossierId).toList());
        assertThat(vus).containsExactly(DOSSIER_A);
    }

    @Test
    void avec_le_workspace_b_le_dossier_de_a_est_introuvable() {
        TenantContext.set(WS_B);
        Boolean trouve = tx.execute(s -> settings.findById(DOSSIER_A).isPresent());
        assertThat(trouve).isFalse();
    }

    @Test
    void ecrire_dans_le_workspace_d_un_autre_cabinet_est_refuse() {
        TenantContext.set(WS_A);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> jdbc.update(
                "UPDATE dataroom_settings SET workspace_id = ? WHERE dossier_id = ?", WS_B, DOSSIER_A)))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void sans_workspace_la_lecture_echoue_au_lieu_de_renvoyer_zero_ligne() {
        assertThatThrownBy(() -> tx.execute(s -> settings.findAll()))
                .hasStackTraceContaining("hors-transaction");
        assertThatThrownBy(() -> jdbc.queryForList("SELECT dossier_id FROM dataroom_settings"))
                .hasStackTraceContaining("hors-transaction");
    }
}

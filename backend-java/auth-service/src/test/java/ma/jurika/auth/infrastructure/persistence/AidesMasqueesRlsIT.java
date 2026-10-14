package ma.jurika.auth.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.common.persistence.TenantAwareJpaTransactionManager;
import ma.jurika.common.security.TenantContext;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6 (E6) : aides masquees (auth V39) avec l'adaptateur reel, en role
 * jurika_app sous RLS : un cabinet ne voit ni ne touche les aides d'un autre,
 * masquer est idempotent, reafficher supprime la ligne, et le role n'a pas le
 * droit UPDATE (inutile).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AidesMasqueesRlsIT {

    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000b3");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_aides")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private AidesMasqueesJdbc registre;

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_history_auth")
                .load()
                .migrate();
        try (Connection owner = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement st = owner.createStatement()) {
            st.execute("CREATE TEMP TABLE w AS SELECT * FROM workspaces WHERE id = '" + WS_A + "'");
            st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-AIDEB'");
            st.execute("INSERT INTO workspaces SELECT * FROM w");
            st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + USER_A + "'");
            st.execute("UPDATE u SET id = '" + USER_B + "', workspace_id = '" + WS_B
                    + "', email = 'b@aides.test', login_email = 'b@aides.test'");
            st.execute("INSERT INTO users SELECT * FROM u");
        }

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
        tx = new TransactionTemplate(new TenantAwareJpaTransactionManager(emf));
        jdbc = new JdbcTemplate(app);
        registre = new AidesMasqueesJdbc(jdbc);
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
    void masquer_lister_reafficher_pour_son_compte() {
        dans(WS_A, () -> { registre.reafficherTout(USER_A, WS_A); return null; });
        dans(WS_A, () -> { registre.masquer(USER_A, WS_A, "page-ticket"); return null; });
        dans(WS_A, () -> { registre.masquer(USER_A, WS_A, "page-ticket"); return null; });
        dans(WS_A, () -> { registre.masquer(USER_A, WS_A, "visite-employe"); return null; });
        assertThat(dans(WS_A, () -> registre.cles(USER_A, WS_A)))
                .as("masquer deux fois la meme aide ne cree pas de doublon")
                .containsExactly("page-ticket", "visite-employe");
        assertThat(dans(WS_A, () -> registre.compter(USER_A, WS_A))).isEqualTo(2);

        dans(WS_A, () -> { registre.reafficher(USER_A, WS_A, "page-ticket"); return null; });
        assertThat(dans(WS_A, () -> registre.cles(USER_A, WS_A))).containsExactly("visite-employe");

        dans(WS_A, () -> { registre.reafficherTout(USER_A, WS_A); return null; });
        assertThat(dans(WS_A, () -> registre.cles(USER_A, WS_A))).isEmpty();
    }

    @Test
    void un_cabinet_ne_voit_pas_les_aides_d_un_autre() {
        dans(WS_B, () -> { registre.masquer(USER_B, WS_B, "aide-du-cabinet-b"); return null; });
        List<String> vuesDepuisA = dans(WS_A, () -> jdbc.queryForList(
                "SELECT cle FROM aides_masquees WHERE cle = 'aide-du-cabinet-b'", String.class));
        assertThat(vuesDepuisA).isEmpty();
        assertThat(dans(WS_B, () -> registre.cles(USER_B, WS_B))).contains("aide-du-cabinet-b");
    }

    @Test
    void ecrire_une_aide_dans_un_autre_cabinet_est_refuse() {
        assertThatThrownBy(() -> dans(WS_A, () -> { registre.masquer(USER_B, WS_B, "intrusion"); return null; }))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void le_role_applicatif_n_a_pas_le_droit_update() {
        dans(WS_A, () -> { registre.masquer(USER_A, WS_A, "droit-update"); return null; });
        assertThatThrownBy(() -> dans(WS_A, () -> jdbc.update(
                "UPDATE aides_masquees SET masquee_le = now() WHERE workspace_id = ?", WS_A)))
                .hasStackTraceContaining("permission denied");
    }

    @Test
    void une_cle_mal_formee_est_refusee_par_la_base() {
        assertThatThrownBy(() -> dans(WS_A, () -> { registre.masquer(USER_A, WS_A, "cle avec espace"); return null; }))
                .hasStackTraceContaining("aides_masquees_cle_check");
    }

    @Test
    void sans_workspace_la_lecture_echoue() {
        assertThatThrownBy(() -> tx.execute(s -> registre.cles(USER_A, WS_A)))
                .hasStackTraceContaining("hors-transaction");
    }

    private <T> T dans(UUID workspace, Supplier<T> action) {
        TenantContext.set(workspace);
        try {
            return tx.execute(s -> action.get());
        } finally {
            TenantContext.clear();
        }
    }
}

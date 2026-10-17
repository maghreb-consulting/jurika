package ma.jurika.auth.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import ma.jurika.auth.domain.port.PreferencesCompteRegistre.Preferences;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L6b (E2) : preferences d'affichage du compte (auth V47, colonnes de {@code users}) avec
 * l'adaptateur reel, en role jurika_app sous RLS : valeurs par defaut, ecriture partielle, un
 * cabinet ne lit ni ne modifie le compte d'un autre, la base refuse un theme inconnu.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreferencesCompteRlsIT {

    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000012");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000c3");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_preferences")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("testcontainers-init.sql");

    private HikariDataSource app;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private JdbcTemplate proprietaire;
    private PreferencesCompteJdbc registre;

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
            st.execute("UPDATE w SET id = '" + WS_B + "', code = 'JUR-PREFB'");
            st.execute("INSERT INTO workspaces SELECT * FROM w");
            st.execute("CREATE TEMP TABLE u AS SELECT * FROM users WHERE id = '" + USER_A + "'");
            st.execute("UPDATE u SET id = '" + USER_B + "', workspace_id = '" + WS_B
                    + "', email = 'b@preferences.test', login_email = 'b@preferences.test'");
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
        proprietaire = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        registre = new PreferencesCompteJdbc(jdbc);
    }

    @AfterEach
    void vider() {
        TenantContext.clear();
        proprietaire.update("UPDATE users SET theme = 'SYSTEME', juri_masque = FALSE WHERE id IN (?, ?)", USER_A, USER_B);
    }

    @AfterAll
    void arreter() {
        if (emf != null) emf.close();
        if (app != null) app.close();
        pg.stop();
    }

    @Test
    void un_compte_existant_recoit_les_valeurs_par_defaut() {
        assertThat(dans(WS_A, () -> registre.lire(USER_A, WS_A))).contains(new Preferences("SYSTEME", false));
        assertThat(proprietaire.queryForObject(
                "SELECT count(*) FROM users WHERE theme <> 'SYSTEME' OR juri_masque", Integer.class))
                .as("aucun compte n'a une autre valeur apres la migration").isZero();
    }

    @Test
    void ecriture_partielle_le_champ_absent_est_inchange() {
        assertThat(dans(WS_A, () -> registre.modifier(USER_A, WS_A, "SOMBRE", null)))
                .contains(new Preferences("SOMBRE", false));
        assertThat(dans(WS_A, () -> registre.modifier(USER_A, WS_A, null, true)))
                .contains(new Preferences("SOMBRE", true));
        assertThat(dans(WS_A, () -> registre.modifier(USER_A, WS_A, "CLAIR", false)))
                .contains(new Preferences("CLAIR", false));
        assertThat(proprietaire.queryForMap("SELECT theme, juri_masque FROM users WHERE id = ?", USER_A))
                .containsEntry("theme", "CLAIR").containsEntry("juri_masque", false);
    }

    @Test
    void un_cabinet_ne_lit_ni_ne_modifie_le_compte_d_un_autre() {
        // Workspace pose sur A, compte de B : la RLS masque la ligne.
        assertThat(dans(WS_A, () -> registre.lire(USER_B, WS_B))).isEmpty();
        assertThat(dans(WS_A, () -> registre.modifier(USER_B, WS_B, "SOMBRE", true))).isEmpty();
        // Filtrage applicatif : compte de B annonce dans le workspace de A.
        assertThat(dans(WS_A, () -> registre.lire(USER_B, WS_A))).isEmpty();
        assertThat(dans(WS_B, () -> registre.modifier(USER_A, WS_B, "SOMBRE", true))).isEmpty();
        assertThat(proprietaire.queryForMap("SELECT theme, juri_masque FROM users WHERE id = ?", USER_B))
                .containsEntry("theme", "SYSTEME").containsEntry("juri_masque", false);
        assertThat(proprietaire.queryForMap("SELECT theme, juri_masque FROM users WHERE id = ?", USER_A))
                .containsEntry("theme", "SYSTEME").containsEntry("juri_masque", false);
    }

    @Test
    void la_base_refuse_un_theme_inconnu() {
        assertThatThrownBy(() -> dans(WS_A, () -> registre.modifier(USER_A, WS_A, "dark", null)))
                .hasStackTraceContaining("users_theme_check");
        assertThatThrownBy(() -> proprietaire.update("UPDATE users SET theme = NULL WHERE id = ?", USER_A))
                .hasStackTraceContaining("theme");
    }

    @Test
    void sans_workspace_la_lecture_echoue() {
        assertThatThrownBy(() -> tx.execute(s -> registre.lire(USER_A, WS_A)))
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

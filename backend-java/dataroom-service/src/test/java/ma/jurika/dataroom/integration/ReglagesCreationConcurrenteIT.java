package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.DataroomSettingsService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E13) : l'ouverture de la Data Room par le client declenche plusieurs
 * requetes simultanees (my-permissions, garde des permissions...) qui creent chacune
 * les reglages par defaut du dossier s'ils manquent. Avant le correctif, deux
 * creations simultanees se heurtaient sur la cle primaire (dataroom_settings_pkey)
 * et l'une repondait 500. En role d'execution jurika_app (RLS) et avec de vrais fils
 * concurrents : aucune erreur, une seule ligne, et tous lisent les memes reglages.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@ActiveProfiles("it")
class ReglagesCreationConcurrenteIT {

    private static final int FILS = 8;
    private static final int DOSSIERS = 15;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_reglages_conc")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> String.valueOf(FILS + 2));
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private DataroomSettingsService settings;
    @MockBean private ObjectStorage storage;

    private final UUID workspaceId = UUID.randomUUID();
    private final List<UUID> dossiers = new ArrayList<>();

    @BeforeEach
    void seed() {
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Concurrence",
                "JUR-" + workspaceId.toString().substring(0, 5).toUpperCase());
        for (int i = 0; i < DOSSIERS; i++) {
            UUID d = UUID.randomUUID();
            jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) "
                    + "VALUES (?, ?, ?, 'SARL', ?)", d, workspaceId, "CONC " + d, SchemaJurikaDb.EMPLOYE_SEME);
            dossiers.add(d);
        }
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    @Test
    void creations_simultanees_des_reglages_sans_erreur_et_une_seule_ligne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(FILS);
        try {
            for (UUID dossier : dossiers) {
                CyclicBarrier depart = new CyclicBarrier(FILS);
                List<Future<SettingsEntity>> appels = new ArrayList<>();
                for (int i = 0; i < FILS; i++) {
                    appels.add(pool.submit(() -> {
                        TenantContext.set(workspaceId);
                        try {
                            depart.await(10, TimeUnit.SECONDS);
                            return settings.getOrCreate(dossier);
                        } finally {
                            TenantContext.clear();
                        }
                    }));
                }
                List<UUID> jetons = new ArrayList<>();
                for (Future<SettingsEntity> f : appels) {
                    // get() relance l'erreur du fil (violation de cle avant le correctif).
                    SettingsEntity s = f.get(30, TimeUnit.SECONDS);
                    assertThat(s.getDossierId()).isEqualTo(dossier);
                    assertThat(s.getWorkspaceId()).isEqualTo(workspaceId);
                    jetons.add(s.getClientLinkToken());
                }
                assertThat(jetons).as("tous les appels lisent les memes reglages").containsOnly(jetons.get(0));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM dataroom_settings WHERE dossier_id = ?",
                        Integer.class, dossier)).isEqualTo(1);
                // Valeurs par defaut inchangees (consultation seule pour le depot).
                assertThat(jdbc.queryForMap("SELECT access_status, perm_download, perm_print, perm_depot, "
                        + "perm_consultation, perm_demandes, access_count FROM dataroom_settings WHERE dossier_id = ?",
                        dossier))
                        .containsEntry("access_status", "ACTIVE")
                        .containsEntry("perm_download", true)
                        .containsEntry("perm_print", false)
                        .containsEntry("perm_depot", false)
                        .containsEntry("perm_consultation", true)
                        .containsEntry("perm_demandes", true)
                        .containsEntry("access_count", 0);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

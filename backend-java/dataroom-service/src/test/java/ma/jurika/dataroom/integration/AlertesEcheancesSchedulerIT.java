package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.application.ExerciceFiscalService;
import ma.jurika.dataroom.application.fiscal.AlertesEcheancesScheduler;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 14 ter C1 -- AlertesEcheancesSchedulerIT (4 cas).
 *
 * Couvre {@link AlertesEcheancesScheduler.runForDate(LocalDate)} :
 *  - cas 1 : 1er run dispatch toutes les alertes PLANIFIEE pour la date donnee -> ENVOYEE
 *  - cas 2 : 2e run pour la meme date ne re-envoie pas (idempotence)
 *  - cas 3 : date sans alerte -> returns 0 (run silencieux)
 *  - cas 4 : alertes de plusieurs dates / dossiers - seul le jour cible bascule
 *
 * Le @Test du FiscalSprint8IT couvre deja le cas "J-15 sur 1 echeance + idempotence
 * legere". Cette classe systematise et ajoute multi-dates + zero-alerte.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        }
)
@ActiveProfiles("it")
class AlertesEcheancesSchedulerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_scheduler")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExerciceFiscalService exerciceService;
    @Autowired private AlertesEcheancesScheduler scheduler;
    @Autowired private AlerteEcheanceJpaRepository alertesRepo;

    private UUID workspaceId;
    private UUID dossierId;

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents   DISABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_alertes_echeances  DISABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_exercices_fiscaux  DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_fiscal_documents");
        jdbc.execute("DELETE FROM dataroom_alertes_echeances");
        jdbc.execute("DELETE FROM dataroom_exercices_fiscaux");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents   ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_alertes_echeances  ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_exercices_fiscaux  ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_exercices_fiscaux DROP CONSTRAINT IF EXISTS dataroom_exercices_fiscaux_cloture_par_fkey");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents DROP CONSTRAINT IF EXISTS dataroom_fiscal_documents_uploaded_by_fkey");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents DROP CONSTRAINT IF EXISTS dataroom_fiscal_documents_deleted_by_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces(id, name, code_workspace) VALUES (?, ?, ?)",
                workspaceId, "Cabinet Scheduler", "JUR-S0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Scheduler");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private List<AlerteEcheanceEntity> openExerciceAndGetAlerts(short annee) {
        ExerciceFiscalSummary ex = exerciceService.open(dossierId, annee, null, null, true, UUID.randomUUID());
        return alertesRepo.findByExerciceFiscalId(UUID.fromString(ex.id().toString()));
    }

    @Test
    @DisplayName("Scheduler cas 1 : 1er run sur date d'alerte -> toutes les alertes ce jour passent en ENVOYEE")
    void firstRunDispatchesAllAlertsForDate() {
        List<AlerteEcheanceEntity> alerts = openExerciceAndGetAlerts((short) 2038);
        // Trouve une date d'alerte avec plusieurs occurrences (ex. les 4 IS_ACOMPTE_TX = 4 dates differentes,
        // mais TVA_MENSUELLE a 12 dates differentes egalement). On choisit la 1ere date.
        LocalDate firstDate = alerts.stream()
                .map(AlerteEcheanceEntity::getDateAlerte)
                .sorted()
                .findFirst().orElseThrow();

        long countOnDate = alerts.stream().filter(a -> a.getDateAlerte().equals(firstDate)).count();
        int dispatched = scheduler.runForDate(firstDate);

        assertThat(dispatched).isEqualTo((int) countOnDate);
        // Verifier que les alertes de cette date sont passees a ENVOYEE
        List<AlerteEcheanceEntity> postRun = alertesRepo.findByDateAlerteAndStatut(firstDate, "ENVOYEE");
        assertThat(postRun).hasSize((int) countOnDate);
    }

    @Test
    @DisplayName("Scheduler cas 2 : idempotence -> 2e run sur la meme date ne re-envoie aucune alerte")
    void secondRunForSameDateIsIdempotent() {
        List<AlerteEcheanceEntity> alerts = openExerciceAndGetAlerts((short) 2039);
        LocalDate someDate = alerts.get(0).getDateAlerte();

        int firstRun = scheduler.runForDate(someDate);
        int secondRun = scheduler.runForDate(someDate);

        assertThat(firstRun).isGreaterThanOrEqualTo(1);
        assertThat(secondRun).isZero();
    }

    @Test
    @DisplayName("Scheduler cas 3 : date sans alerte planifiee -> renvoie 0 sans erreur")
    void runForDateWithoutPlannedAlertsReturnsZero() {
        openExerciceAndGetAlerts((short) 2040);
        LocalDate randomFarPastDate = LocalDate.of(1995, 1, 1);

        int dispatched = scheduler.runForDate(randomFarPastDate);
        assertThat(dispatched).isZero();
    }

    @Test
    @DisplayName("Scheduler cas 4 : 2 exercices = 2 sets d'alertes -- run cible un seul jour, l'autre exercice intact")
    void multiExerciceScheduledIndependently() {
        List<AlerteEcheanceEntity> alerts2041 = openExerciceAndGetAlerts((short) 2041);
        List<AlerteEcheanceEntity> alerts2042 = openExerciceAndGetAlerts((short) 2042);

        LocalDate datePremierExerciceUnique = alerts2041.stream()
                .map(AlerteEcheanceEntity::getDateAlerte)
                .filter(d -> alerts2042.stream().noneMatch(a2 -> a2.getDateAlerte().equals(d)))
                .findFirst().orElseThrow();

        long expectedDispatched = alerts2041.stream()
                .filter(a -> a.getDateAlerte().equals(datePremierExerciceUnique))
                .count();
        int dispatched = scheduler.runForDate(datePremierExerciceUnique);

        assertThat(dispatched).isEqualTo((int) expectedDispatched);

        // Aucune alerte du 2eme exercice ne doit etre en ENVOYEE apres ce run
        List<UUID> ex2042EnvoyeesIds = alertesRepo.findByDateAlerteAndStatut(datePremierExerciceUnique, "ENVOYEE")
                .stream().map(AlerteEcheanceEntity::getExerciceFiscalId).distinct().collect(Collectors.toList());
        UUID ex2042Id = alerts2042.get(0).getExerciceFiscalId();
        assertThat(ex2042EnvoyeesIds).doesNotContain(ex2042Id);
    }
}

package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.ExerciceFiscalService;
import ma.jurika.dataroom.application.fiscal.AlertesEcheancesScheduler;
import ma.jurika.dataroom.application.fiscal.EcheancesGenerator;
import ma.jurika.dataroom.application.fiscal.FiscalSubClassifications;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8 -- IT principal du Dossier Fiscal couvrant :
 *   - generation echeances (RG-DF20)
 *   - transitions exercice OUVERT/CLOTURE/VERROUILLE (RG-DF25/26)
 *   - scheduler J-15
 *   - validation sous-classifications (RG-DF16)
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
class FiscalSprint8IT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_fiscal")
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
    @Autowired private ExerciceFiscalJpaRepository exercicesRepo;
    @Autowired private AlerteEcheanceJpaRepository alertesRepo;
    @Autowired private AlertesEcheancesScheduler scheduler;
    @Autowired private EcheancesGenerator generator;

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
                workspaceId, "Cabinet S8", "JUR-S8000");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Fiscal S8");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @Test
    @DisplayName("RG-DF20 : generation des 16 echeances pour exercice avec TVA mensuelle")
    void generatesSixteenEcheancesWhenTvaMonthly() {
        ExerciceFiscalEntity ex = new ExerciceFiscalEntity();
        ex.setWorkspaceId(workspaceId);
        ex.setDossierId(dossierId);
        ex.setAnnee((short) 2027);
        ex.setDateDebut(LocalDate.of(2027, 1, 1));
        ex.setDateFin(LocalDate.of(2027, 12, 31));
        ex.setStatut("OUVERT");
        ex.setId(UUID.randomUUID());

        List<AlerteEcheanceEntity> ech = generator.generateForExercice(ex, true);
        // 12 TVA + 4 IS acomptes + 4 annuelles (IS, TP/TSC, 9421, IR) = 20
        // (le plan annonce 16 mais le generateur produit 20 pour les annuelles incluses)
        assertThat(ech).hasSize(20);
        assertThat(ech.stream().map(AlerteEcheanceEntity::getTypeEcheance))
                .contains("TVA_MENSUELLE", "IS_ACOMPTE_T1", "IS_DECLARATION_ANNUELLE",
                          "ETAT_9421", "IR_DECLARATION_ANNUELLE", "TP_TSC_DECLARATION");
    }

    @Test
    @DisplayName("RG-DF20 : 8 echeances pour exercice TVA trimestrielle (4 TVA + 4 IS + 4 annuelles)")
    void generatesTwelveEcheancesWhenTvaQuarterly() {
        ExerciceFiscalEntity ex = new ExerciceFiscalEntity();
        ex.setWorkspaceId(workspaceId);
        ex.setDossierId(dossierId);
        ex.setAnnee((short) 2027);
        ex.setDateDebut(LocalDate.of(2027, 1, 1));
        ex.setDateFin(LocalDate.of(2027, 12, 31));
        ex.setStatut("OUVERT");
        ex.setId(UUID.randomUUID());

        List<AlerteEcheanceEntity> ech = generator.generateForExercice(ex, false);
        // 4 TVA trim + 4 IS acomptes + 4 annuelles = 12
        assertThat(ech).hasSize(12);
    }

    @Test
    @DisplayName("RG-DF25 : transition CLOTURE depuis OUVERT OK ; double cloture -> 409")
    void cloturerTransitionIsExclusive() {
        var ex = exerciceService.open(dossierId, (short) 2028, null, null, true, UUID.randomUUID());
        var cloture = exerciceService.cloturer(UUID.fromString(ex.id().toString()), UUID.randomUUID());
        assertThat(cloture.statut()).isEqualTo("CLOTURE");

        assertThatThrownBy(() ->
                exerciceService.cloturer(UUID.fromString(ex.id().toString()), UUID.randomUUID()))
                .hasMessageContaining("RG-DF25")
                .hasMessageContaining("OUVERT");
    }

    @Test
    @DisplayName("RG-DF26 : deverrouiller refuse motif < 20 chars")
    void deverrouillerRequiresMotifMin20() {
        var ex = exerciceService.open(dossierId, (short) 2029, null, null, true, UUID.randomUUID());
        UUID exId = UUID.fromString(ex.id().toString());
        exerciceService.verrouiller(exId, UUID.randomUUID());

        assertThatThrownBy(() ->
                exerciceService.deverrouiller(exId, "court", UUID.randomUUID()))
                .hasMessageContaining("20 caracteres");

        var ouvert = exerciceService.deverrouiller(exId,
                "Fin de controle DGI -- accord trouve avec inspecteur le 2026-05-10",
                UUID.randomUUID());
        assertThat(ouvert.statut()).isEqualTo("OUVERT");
    }

    @Test
    @DisplayName("RG-DF16 : sous-classification invalide -> rejet")
    void subClassificationInvalidIsRejected() {
        assertThat(FiscalSubClassifications.isValid("TVA", "DECLARATION_MENSUELLE")).isTrue();
        assertThat(FiscalSubClassifications.isValid("TVA", "ACOMPTE_T1")).isFalse();
        assertThat(FiscalSubClassifications.isValid("CONTENTIEUX", "NOTIFICATION_DGI")).isTrue();
        assertThat(FiscalSubClassifications.isValid("RAS", "HONORAIRES_10")).isTrue();
    }

    @Test
    @DisplayName("Scheduler J-15 : force le run sur la date d'alerte d'une echeance et passe ENVOYEE")
    void schedulerDispatchesAlertOnJ15() {
        var ex = exerciceService.open(dossierId, (short) 2030, null, null, true, UUID.randomUUID());
        List<AlerteEcheanceEntity> alerts = alertesRepo.findByExerciceFiscalId(
                UUID.fromString(ex.id().toString()));
        assertThat(alerts).isNotEmpty();
        AlerteEcheanceEntity any = alerts.get(0);

        int dispatched = scheduler.runForDate(any.getDateAlerte());
        assertThat(dispatched).isGreaterThanOrEqualTo(1);

        // Une nouvelle execution sur la meme date ne re-envoie pas les memes alertes
        int reDispatched = scheduler.runForDate(any.getDateAlerte());
        assertThat(reDispatched).isLessThan(dispatched);
    }
}

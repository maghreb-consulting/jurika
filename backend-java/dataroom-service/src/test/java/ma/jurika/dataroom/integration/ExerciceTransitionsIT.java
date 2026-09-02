package ma.jurika.dataroom.integration;

import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.application.ExerciceFiscalService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 14 ter C1 -- ExerciceTransitionsIT (6 cas).
 *
 * Couvre la machine a etats RG-DF25 et RG-DF26 sur ExerciceFiscalService :
 *   OUVERT -> CLOTURE -> VERROUILLE -> OUVERT (avec motif >= 20 chars).
 *
 * Complementaire de FiscalSprint8IT (RG-DF25 1 cas + RG-DF26 1 cas existants) :
 * cette classe couvre TOUTES les transitions interdites ET les invariants metier
 * (annee invalide, doublon annee, deverrouillage sans motif, etc.).
 *
 * Pattern TestContainers Postgres 16 identique a FiscalSprint8IT.
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
class ExerciceTransitionsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_transitions")
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
    @Autowired private AlerteEcheanceJpaRepository alertesRepo;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID byUser;

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
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents   ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_alertes_echeances  ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_exercices_fiscaux  ENABLE ROW LEVEL SECURITY");
        // Sprint 14 ter C1 : drop FK constraints sur users pour autoriser UUID.randomUUID() en byUser
        jdbc.execute("ALTER TABLE dataroom_exercices_fiscaux DROP CONSTRAINT IF EXISTS dataroom_exercices_fiscaux_cloture_par_fkey");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents DROP CONSTRAINT IF EXISTS dataroom_fiscal_documents_uploaded_by_fkey");
        jdbc.execute("ALTER TABLE dataroom_fiscal_documents DROP CONSTRAINT IF EXISTS dataroom_fiscal_documents_deleted_by_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        byUser = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces(id, name, code_workspace) VALUES (?, ?, ?)",
                workspaceId, "Cabinet Transitions", "JUR-T0001");
        jdbc.update("INSERT INTO users(id, workspace_id, email, role) VALUES (?, ?, ?, ?)",
                byUser, workspaceId, "byuser@trans.ma", "SUPERVISEUR");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Transitions IT");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UUID openExercice(short annee, boolean tvaMensuel) {
        ExerciceFiscalSummary s = exerciceService.open(dossierId, annee, null, null, tvaMensuel, byUser);
        return UUID.fromString(s.id().toString());
    }

    @Test
    @DisplayName("RG-DF25 cas 1 : OUVERT -> CLOTURE -> VERROUILLE chemin complet succes")
    void fullLifecycleOpenClotureVerrouille() {
        UUID exId = openExercice((short) 2031, true);

        var afterCloture = exerciceService.cloturer(exId, byUser);
        assertThat(afterCloture.statut()).isEqualTo("CLOTURE");

        var afterLock = exerciceService.verrouiller(exId, byUser);
        assertThat(afterLock.statut()).isEqualTo("VERROUILLE");
    }

    @Test
    @DisplayName("RG-DF25 cas 2 : VERROUILLE -> CLOTURE interdit (INVALID_TRANSITION)")
    void cannotCloturerFromVerrouille() {
        UUID exId = openExercice((short) 2032, true);
        exerciceService.cloturer(exId, byUser);
        exerciceService.verrouiller(exId, byUser);

        // NB : message humain "RG-DF25 : seul un exercice OUVERT peut etre CLOTURE."
        // -- le code interne "INVALID_TRANSITION" est dans BusinessException.code(),
        // pas getMessage(). On verifie BusinessException + RG-DF25 + extrait du message.
        assertThatThrownBy(() -> exerciceService.cloturer(exId, byUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("RG-DF25 cas 3 : VERROUILLE -> VERROUILLE interdit (double lock)")
    void cannotDoubleVerrouille() {
        UUID exId = openExercice((short) 2033, true);
        exerciceService.verrouiller(exId, byUser); // OUVERT -> VERROUILLE OK
        assertThatThrownBy(() -> exerciceService.verrouiller(exId, byUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("RG-DF26 cas 4 : deverrouiller un exercice OUVERT -> EXERCICE_NOT_LOCKED")
    void cannotDeverrouillerWhenNotLocked() {
        UUID exId = openExercice((short) 2034, true);
        String motifValide = "Erreur de manipulation cote superviseur, retour OUVERT necessaire.";
        assertThatThrownBy(() -> exerciceService.deverrouiller(exId, motifValide, byUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("EXERCICE_NOT_LOCKED"));
    }

    @Test
    @DisplayName("RG-DF26 cas 5 : VERROUILLE -> OUVERT avec motif >= 20 chars -> succes + note metadata persistee")
    void deverrouillerOkWithLongMotifPersistsNote() {
        UUID exId = openExercice((short) 2035, true);
        exerciceService.verrouiller(exId, byUser);

        String motif = "Cloture du controle DGI, demande gerant de reouvrir exercice 2035";
        var afterUnlock = exerciceService.deverrouiller(exId, motif, byUser);
        assertThat(afterUnlock.statut()).isEqualTo("OUVERT");

        String note = jdbc.queryForObject(
                "SELECT note FROM dataroom_exercices_fiscaux WHERE id = ?",
                String.class, exId);
        assertThat(note).contains("Deverrouillage").contains(byUser.toString()).contains(motif);
    }

    @Test
    @DisplayName("RG-DF25 cas 6 : annee invalide (1999 ou 2101) + doublon annee sur meme dossier rejetes")
    void invariantsOnOpen() {
        assertThatThrownBy(() -> exerciceService.open(dossierId, (short) 1999, null, null, true, byUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Annee invalide");

        assertThatThrownBy(() -> exerciceService.open(dossierId, (short) 2101, null, null, true, byUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Annee invalide");

        exerciceService.open(dossierId, (short) 2040, null, null, true, byUser);
        assertThatThrownBy(() -> exerciceService.open(dossierId, (short) 2040, null, null, false, byUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("EXERCICE_EXISTS"));

        // RG-DF20 : ouverture genere bien les echeances en DB (verification cross-cutting)
        assertThat(alertesRepo.count()).isGreaterThanOrEqualTo(20L); // 20 echeances mensuelles + autres
    }
}

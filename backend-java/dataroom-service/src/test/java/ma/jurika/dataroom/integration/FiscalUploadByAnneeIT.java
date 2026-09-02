package ma.jurika.dataroom.integration;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.FiscalDocumentSummary;
import ma.jurika.dataroom.application.DataroomFiscalService;
import ma.jurika.dataroom.application.ExerciceFiscalService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * Prompt H (2026-06-23) — couvre {@code DataroomFiscalService.uploadByAnnee} :
 * <ul>
 *   <li>année inexistante → exercice créé à la volée (idempotent) + doc uploadé ;</li>
 *   <li>2e upload même année → réutilise l'exercice existant (pas de doublon) ;</li>
 *   <li>{@link ExerciceFiscalService#findOrCreateByAnnee} : retour idempotent.</li>
 * </ul>
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
class FiscalUploadByAnneeIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_fiscal_anee")
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
    @Autowired private DataroomFiscalService fiscal;
    @Autowired private ExerciceFiscalService exerciceService;
    @MockBean private ObjectStorage storage;

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
                workspaceId, "Cabinet Annee", "JUR-A0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Import");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[1024]);
    }

    @Test
    @DisplayName("findOrCreateByAnnee : crée l'exercice si absent, le réutilise au 2e appel")
    void findOrCreate_idempotent() {
        UUID userId = UUID.randomUUID();
        var first = exerciceService.findOrCreateByAnnee(dossierId, (short) 2022, userId);
        assertThat(first).isNotNull();
        assertThat(first.getAnnee()).isEqualTo((short) 2022);
        assertThat(first.getStatut()).isEqualTo("OUVERT");

        var second = exerciceService.findOrCreateByAnnee(dossierId, (short) 2022, userId);
        assertThat(second.getId()).isEqualTo(first.getId());

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2022",
                Long.class, dossierId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("uploadByAnnee : crée l'exercice 2023 + dépose le doc TVA dans cet exercice")
    void uploadByAnnee_creates_exercice_and_uploads() {
        UUID userId = UUID.randomUUID();
        FiscalDocumentSummary doc = fiscal.uploadByAnnee(
                dossierId, (short) 2023, "TVA", "DECLARATION_MENSUELLE",
                "TVA Janvier 2023", null, null, null, null, null,
                pdf("tva-janv.pdf"), userId, false);
        assertThat(doc.exerciceFiscalId()).isNotNull();

        // Un seul exercice 2023 existe.
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2023",
                Long.class, dossierId);
        assertThat(count).isEqualTo(1L);

        // Le doc est rattaché à l'exercice créé.
        Long docCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE dossier_id = ? AND exercice_fiscal_id = ?",
                Long.class, dossierId, doc.exerciceFiscalId());
        assertThat(docCount).isEqualTo(1L);

        // 2e upload même année → pas de nouvel exercice.
        fiscal.uploadByAnnee(dossierId, (short) 2023, "IS", "ACOMPTE_T1",
                "Acompte IS T1", null, null, null, null, null,
                pdf("is-acompte.pdf"), userId, false);
        Long stillOneExercice = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2023",
                Long.class, dossierId);
        assertThat(stillOneExercice).isEqualTo(1L);
        Long docCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE dossier_id = ?",
                Long.class, dossierId);
        assertThat(docCountAfter).isEqualTo(2L);
    }

    // ===================================================================
    // RG-DF03 (2026-06-24) — conformité de l'exercice fiscal au comptable.
    // ===================================================================

    @Test
    @DisplayName("RG-DF03 : ouverture manuelle d'une annee non tenue en compta -> rejet conformite")
    void open_strict_withoutComptable_rejected() {
        assertThatThrownBy(() ->
                exerciceService.open(dossierId, (short) 2099, null, null, true, false, UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("conforme aux annees comptables");

        // Aucun exercice ni echeance ne doit avoir ete cree.
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2099",
                Long.class, dossierId);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("RG-DF03 : ouverture conforme reprend les dates de l'ancre comptable (pas 01/01-31/12)")
    void open_strict_reusesComptableAnchorAndAlignsDates() {
        // Ancre comptable (ligne d'exercice creee cote comptable) avec periode decalee.
        UUID anchorId = UUID.randomUUID();
        jdbc.update("INSERT INTO dataroom_exercices_fiscaux "
                        + "(id, workspace_id, dossier_id, annee, date_debut, date_fin, statut, "
                        + " date_ouverture, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?::date, ?::date, 'OUVERT', now(), now(), now())",
                anchorId, workspaceId, dossierId, 2098, "2098-04-01", "2099-03-31");

        ExerciceFiscalSummary opened =
                exerciceService.open(dossierId, (short) 2098, null, null, true, false, UUID.randomUUID());

        // Reutilise l'ancre comptable (pas de nouvelle ligne) + dates alignees.
        assertThat(opened.id()).isEqualTo(anchorId);
        assertThat(opened.dateDebut()).isEqualTo("2098-04-01");
        assertThat(opened.dateFin()).isEqualTo("2099-03-31");

        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2098",
                Long.class, dossierId);
        assertThat(rows).isEqualTo(1L);

        // Le fiscal garde ses attributs propres : echeances DGI generees sur l'ancre.
        Long echeances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_alertes_echeances WHERE exercice_fiscal_id = ?",
                Long.class, anchorId);
        assertThat(echeances).isGreaterThan(0L);

        // Re-ouvrir le meme exercice fiscal -> EXERCICE_EXISTS (echeances deja generees).
        assertThatThrownBy(() ->
                exerciceService.open(dossierId, (short) 2098, null, null, true, false, UUID.randomUUID()))
                .isInstanceOf(ma.jurika.common.exception.BusinessException.class);
    }

    @Test
    @DisplayName("RG-DF03 : autoCreateComptable=true (import/creation) cree l'ancre + echeances sans bloquer")
    void open_autoCreateComptable_createsAnchorAndEcheances() {
        ExerciceFiscalSummary opened =
                exerciceService.open(dossierId, (short) 2097, null, null, true, true, UUID.randomUUID());
        assertThat(opened.statut()).isEqualTo("OUVERT");
        assertThat(opened.annee()).isEqualTo((short) 2097);

        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_exercices_fiscaux WHERE dossier_id = ? AND annee = 2097",
                Long.class, dossierId);
        assertThat(rows).isEqualTo(1L);

        Long echeances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_alertes_echeances WHERE exercice_fiscal_id = ?",
                Long.class, opened.id());
        assertThat(echeances).isGreaterThan(0L);
    }
}

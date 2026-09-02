package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.application.DataroomFiscalService;
import ma.jurika.dataroom.application.ExerciceFiscalService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 14 ter C1 -- RetentionPurgeIT (4 cas).
 *
 * Couvre DataroomFiscalService.purgeExpiredDocuments() :
 *   RG-DF18 + CGI Art. 211 (retention 10 ans documents fiscaux).
 *
 * Cas couverts :
 *   1. Document soft-deleted createdAt > 10 ans + 3 jours -> purge effective
 *   2. Document soft-deleted createdAt < 10 ans -> preserve
 *   3. Document NON soft-deleted createdAt > 10 ans -> preserve (only soft-deleted purges)
 *   4. Plusieurs documents melanges -> compteur retourne exact + lignes DB conforme
 *
 * Pre-requis : init script testcontainers-init.sql + DataroomFiscalService autowire.
 * NB : MinioObjectStorage.delete jette si la cle n'existe pas, mais purgeExpiredDocuments
 * catch silencieusement -> robustness verifiee implicitement.
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
class RetentionPurgeIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_purge")
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

    private UUID workspaceId;
    private UUID dossierId;
    private UUID exerciceId;

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
                workspaceId, "Cabinet Retention", "JUR-R0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Retention");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");

        // Cree un exercice valide pour FK
        var ex = exerciceService.open(dossierId, (short) 2017, null, null, true, UUID.randomUUID());
        exerciceId = UUID.fromString(ex.id().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Insert direct via JdbcTemplate pour bypasser @PrePersist (qui auto-set createdAt = now).
     * Permet d'inserer des createdAt > 10 ans dans le passe.
     */
    private UUID insertFiscalDoc(Instant createdAt, boolean isDeleted, String label) {
        UUID id = UUID.randomUUID();
        // Check constraint dataroom_fiscal_documents_check : is_deleted=true requiere deleted_at NOT NULL
        Timestamp deletedAt = isDeleted ? Timestamp.from(Instant.now()) : null;
        UUID deletedBy = isDeleted ? UUID.randomUUID() : null;
        jdbc.update("""
            INSERT INTO dataroom_fiscal_documents
              (id, workspace_id, dossier_id, exercice_fiscal_id, categorie,
               sous_classification, title, object_key, filename, size_bytes,
               is_deleted, deleted_at, deleted_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'TVA', 'DECLARATION_MENSUELLE', ?,
                    ?, 'test.pdf', 1024, ?, ?, ?, ?, ?)
            """,
            id, workspaceId, dossierId, exerciceId, label,
            "ws/" + workspaceId + "/dossier/" + dossierId + "/fiscal/" + exerciceId + "/TVA/" + id + "_test.pdf",
            isDeleted,
            deletedAt,
            deletedBy,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt));
        return id;
    }

    @Test
    @DisplayName("RG-DF18 cas 1 : document soft-deleted createdAt > 10 ans + 3 jours -> purge")
    void purgesSoftDeletedOlderThan10Years() {
        Instant veryOld = Instant.now().minus(11 * 365L, ChronoUnit.DAYS);
        UUID oldId = insertFiscalDoc(veryOld, true, "doc-11-ans");

        int purged = fiscal.purgeExpiredDocuments();

        assertThat(purged).isEqualTo(1);
        Long remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE id = ?", Long.class, oldId);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("RG-DF18 cas 2 : document soft-deleted createdAt < 10 ans -> preserve")
    void preservesSoftDeletedYoungerThan10Years() {
        Instant recent = Instant.now().minus(5 * 365L, ChronoUnit.DAYS);
        UUID recentId = insertFiscalDoc(recent, true, "doc-5-ans");

        int purged = fiscal.purgeExpiredDocuments();

        assertThat(purged).isZero();
        Long remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE id = ?", Long.class, recentId);
        assertThat(remaining).isEqualTo(1L);
    }

    @Test
    @DisplayName("CGI Art. 211 cas 3 : document NON soft-deleted createdAt > 10 ans -> preserve (never auto-purge)")
    void doesNotPurgeNonSoftDeletedEvenIfVeryOld() {
        Instant veryOld = Instant.now().minus(15 * 365L, ChronoUnit.DAYS);
        UUID id = insertFiscalDoc(veryOld, false, "doc-15-ans-actif");

        int purged = fiscal.purgeExpiredDocuments();

        assertThat(purged).isZero();
        Long remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE id = ?", Long.class, id);
        assertThat(remaining).isEqualTo(1L);
    }

    @Test
    @DisplayName("RG-DF18 cas 4 : mix 3 documents (2 purgeables + 1 jeune + 1 actif) -> count = 2, jeune+actif preserves")
    void mixedScenarioReturnsExactCount() {
        Instant old11 = Instant.now().minus(11 * 365L, ChronoUnit.DAYS);
        Instant old12 = Instant.now().minus(12 * 365L, ChronoUnit.DAYS);
        Instant recent = Instant.now().minus(2 * 365L, ChronoUnit.DAYS);
        Instant veryOldActive = Instant.now().minus(20 * 365L, ChronoUnit.DAYS);

        UUID purgeable1 = insertFiscalDoc(old11, true, "purge-1");
        UUID purgeable2 = insertFiscalDoc(old12, true, "purge-2");
        UUID youngDeleted = insertFiscalDoc(recent, true, "jeune-deleted");
        UUID veryOldActiveId = insertFiscalDoc(veryOldActive, false, "tres-vieux-actif");

        int purged = fiscal.purgeExpiredDocuments();

        assertThat(purged).isEqualTo(2);

        Long survivors = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE id IN (?, ?)",
                Long.class, youngDeleted, veryOldActiveId);
        assertThat(survivors).isEqualTo(2L);

        Long purgees = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE id IN (?, ?)",
                Long.class, purgeable1, purgeable2);
        assertThat(purgees).isZero();
    }
}

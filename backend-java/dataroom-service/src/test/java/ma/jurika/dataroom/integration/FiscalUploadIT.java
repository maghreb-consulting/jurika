package ma.jurika.dataroom.integration;

import ma.jurika.common.exception.BusinessException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Sprint 14 ter C1 -- FiscalUploadIT (7 cas).
 *
 * Couvre DataroomFiscalService.upload() (13 parametres) :
 *   - RG-DF04 (CLIENT ne peut pas uploader CONTENTIEUX)
 *   - RG-DF07 (extension fichier autorisee)
 *   - RG-DF08 (taille max 15 Mo -- NB plan disait 50 mais code = 15)
 *   - RG-DF16 (sous-classification valide)
 *   - RG-DF23 (commentaire >= 20 chars sur CONTENTIEUX)
 *   - RG-DF25 (exercice VERROUILLE -> upload interdit)
 *
 * ObjectStorage est mocke (@MockBean) pour ne pas dependre d'un MinIO reel.
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
class FiscalUploadIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_upload")
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
    private UUID exerciceIdOuvert;

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
                workspaceId, "Cabinet Upload", "JUR-U0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Upload");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");

        ExerciceFiscalSummary ex = exerciceService.open(dossierId, (short) 2050, null, null, true, UUID.randomUUID());
        exerciceIdOuvert = UUID.fromString(ex.id().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MockMultipartFile pdf(String filename, long size) {
        byte[] content = new byte[(int) Math.min(size, 1024)];
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    @Test
    @DisplayName("RG-DF16 cas 1 : upload TVA + sous-classif valide -> ligne DB + storage.upload appele")
    void uploadTvaSuccessPersistsAndCallsStorage() {
        UUID uploaderId = UUID.randomUUID();
        FiscalDocumentSummary doc = fiscal.upload(dossierId, exerciceIdOuvert,
                "TVA", "DECLARATION_MENSUELLE",
                "TVA Janvier 2050", null, null, null, null, null,
                pdf("tva-janvier.pdf", 1024),
                uploaderId, false);

        assertThat(doc).isNotNull();
        assertThat(doc.categorie()).isEqualTo("TVA");
        assertThat(doc.sousClassification()).isEqualTo("DECLARATION_MENSUELLE");

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(storage, times(1)).upload(keyCap.capture(), any(InputStream.class), anyLong(), anyString());
        assertThat(keyCap.getValue())
                .contains("ws/" + workspaceId)
                .contains("dossier/" + dossierId)
                .contains("fiscal/" + exerciceIdOuvert)
                .contains("/TVA/")
                .endsWith("_tva-janvier.pdf");

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_fiscal_documents WHERE dossier_id = ?",
                Long.class, dossierId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("RG-DF04 cas 2 : CLIENT ne peut pas uploader CONTENTIEUX -> AccessDeniedException")
    void clientCannotUploadContentieux() {
        String longComment = "Mise en demeure DGI requise pour suivi controle fiscal 2050 -- ATTENTION";
        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "CONTENTIEUX", "NOTIFICATION_DGI",
                "Notif DGI", longComment, null, null, null, null,
                pdf("notif-dgi.pdf", 1024),
                UUID.randomUUID(), /* isClientRole */ true))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("RG-DF04");
    }

    @Test
    @DisplayName("RG-DF16 cas 3 : sous-classification invalide -> ValidationException")
    void invalidSousClassificationIsRejected() {
        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "TVA", "NOT_A_VALID_SUBCLASSIFICATION",
                "Doc", null, null, null, null, null,
                pdf("doc.pdf", 1024),
                UUID.randomUUID(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RG-DF16");
    }

    @Test
    @DisplayName("RG-DF08 cas 4 : taille > 15 Mo -> ValidationException FILE_TOO_LARGE")
    void fileTooLargeIsRejected() {
        // MockMultipartFile.getSize() returns content.length ; on triche en passant un
        // MockMultipartFile avec 16 Mo de bytes
        byte[] big = new byte[16 * 1024 * 1024];
        MockMultipartFile huge = new MockMultipartFile("file", "huge.pdf", "application/pdf", big);
        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "TVA", "DECLARATION_MENSUELLE",
                "Huge", null, null, null, null, null,
                huge, UUID.randomUUID(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("RG-DF25 cas 5 : exercice VERROUILLE -> upload interdit EXERCICE_LOCKED")
    void uploadOnVerrouilleExerciceIsRejected() {
        exerciceService.verrouiller(exerciceIdOuvert, UUID.randomUUID());

        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "TVA", "DECLARATION_MENSUELLE",
                "TVA", null, null, null, null, null,
                pdf("tva.pdf", 1024),
                UUID.randomUUID(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RG-DF25")
                .hasMessageContaining("verrouille");
    }

    @Test
    @DisplayName("RG-DF07 cas 6 : extension .exe non autorisee -> ValidationException FILE_TYPE_NOT_ALLOWED")
    void extensionNotAllowedIsRejected() {
        MockMultipartFile exe = new MockMultipartFile("file", "malware.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A});
        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "TVA", "DECLARATION_MENSUELLE",
                "Exe", null, null, null, null, null,
                exe, UUID.randomUUID(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("RG-DF23 cas 7 : CONTENTIEUX sans commentaire (ou < 20 chars) -> ValidationException")
    void contentieuxRequiresCommentMin20Chars() {
        assertThatThrownBy(() -> fiscal.upload(dossierId, exerciceIdOuvert,
                "CONTENTIEUX", "NOTIFICATION_DGI",
                "Notif courte", "trop court", null, null, null, null,
                pdf("notif.pdf", 1024),
                UUID.randomUUID(), /* isClientRole */ false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RG-DF23");
    }
}

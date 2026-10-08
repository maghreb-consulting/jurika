package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierTicket;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E20 (perimetre § D, P9) : filtre workspace EXPLICITE dans
 * dataroom, en plus de la RLS (les deux sont toujours exiges). L'application
 * se connecte ici en PROPRIETAIRE, que la RLS ne filtre pas : seul le filtre
 * applicatif protege. Workspace courant A ; les donnees visees sont celles du
 * cabinet B.
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
class FiltreWorkspaceDataroomIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_filtre_ws")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // Lot L0 (E15) : l'application tourne en role d'execution jurika_app (la
        // RLS s'applique) ; Flyway migre avec le proprietaire.
        // E20 : PROPRIETAIRE (hors RLS), pour eprouver le filtre applicatif seul.
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    /** Preparation et assertions en PROPRIETAIRE, hors RLS (lot L0). */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private DataroomJuridiqueService juridique;
    @MockBean private ObjectStorage storage;
    @Autowired private ma.jurika.dataroom.application.SearchJuridiqueDocumentsUseCase recherche;
    @Autowired private ma.jurika.dataroom.application.PreviewDocumentUseCase apercu;
    @Autowired private ma.jurika.dataroom.application.DataroomSettingsService reglages;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID ticketId;
    private UUID employeId;

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_visibilite_evenements");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_documents "
                + "DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");
        jdbc.execute("ALTER TABLE dataroom_visibilite_evenements "
                + "DROP CONSTRAINT IF EXISTS dataroom_visibilite_evenements_acteur_id_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        employeId = UUID.randomUUID();

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Suppression", "JUR-S0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique) VALUES (?, ?, ?, 'SARL')",
                dossierId, workspaceId, "PARACOSME");
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '33333333-3333-3333-3333-333333333333')
                """, ticketId, workspaceId, "T-2026-00841", "Creation SARL PARACOSME",
                "CREATION", "DEROULEMENT_DEMARCHE", dossierId,
                OffsetDateTime.parse("2026-09-01T09:00:00Z"));

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UUID docB;
    private final UUID workspaceA = UUID.randomUUID();

    /** Le cabinet B porte le dossier et le document ; A est le workspace courant. */
    private void preparer() {
        org.mockito.Mockito.when(storage.download(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                new ObjectStorage.DownloadResult(new java.io.ByteArrayInputStream("%PDF".getBytes()), 4, "application/pdf"));
        SchemaJurikaDb.workspace(jdbc, workspaceA, "Cabinet A", "JUR-A0001");
        TenantContext.set(workspaceId);
        try {
            var f = new MockMultipartFile("file", "statuts.pdf", "application/pdf", "%PDF".getBytes());
            docB = juridique.uploadVersion(dossierId, "AUTRE", "Statuts B", ticketId, f, employeId).id();
        } finally {
            TenantContext.clear();
        }
    }

    private <T> T dansA(java.util.function.Supplier<T> appel) {
        preparer();
        TenantContext.set(workspaceA);
        try {
            return appel.get();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void la_recherche_ne_rend_rien_d_un_autre_workspace() {
        var sortie = dansA(() -> recherche.execute(new ma.jurika.dataroom.api.dto.DataroomDtos.SearchJuridiqueInput(
                dossierId, null, null, null, null,
                ma.jurika.dataroom.api.dto.DataroomDtos.VersionScope.ALL, 50, 0)));
        assertThat(sortie.items()).isEmpty();
        assertThat(sortie.total()).isZero();
    }

    @Test
    void l_apercu_d_un_document_d_un_autre_workspace_est_introuvable() {
        var employeA = new ma.jurika.common.security.AuthenticatedUser(
                employeId, workspaceA, "e@a.test", ma.jurika.common.security.Role.EMPLOYE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansA(() -> apercu.execute(docB, employeA)))
                .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
    }

    @Test
    void le_telechargement_d_un_document_d_un_autre_workspace_est_introuvable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dansA(() -> juridique.loadForDownload(docB)))
                .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
    }

    @Test
    void l_export_n_inclut_rien_d_un_autre_workspace() throws Exception {
        byte[] zip = dansA(() -> juridique.exportSelectionAsZip(dossierId, List.of(docB), true));
        List<String> noms = new java.util.ArrayList<>();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) noms.add(e.getName());
        }
        assertThat(noms).containsExactly("MANIFEST.txt");
    }

    @Test
    void les_reglages_d_un_autre_workspace_ne_sont_ni_lus_ni_crees() {
        preparer();
        // Reglages existants dans B : invisibles depuis A.
        jdbc.update("INSERT INTO dataroom_settings(dossier_id, workspace_id, access_status) VALUES (?, ?, 'SUSPENDED')",
                dossierId, workspaceId);
        TenantContext.set(workspaceA);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reglages.getOrCreate(dossierId))
                    .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataroom_settings WHERE dossier_id = ?",
                Integer.class, dossierId)).isEqualTo(1);
    }
}

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
 * Lot L0, etape E15 (inventaire W1) : routes WOPI appelees par Collabora SANS
 * jeton JWT, en role d'execution jurika_app (RLS active, garde « hors
 * transaction »). Le workspace vient de la seance designee par le jeton opaque
 * (ContexteWopiConfig) ; sans lui, la recherche du document sous RLS echouait
 * et Collabora ne pouvait ni ouvrir, ni verrouiller, ni enregistrer.
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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class WopiJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_wopi")
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
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
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
    @MockBean private ma.jurika.dataroom.infrastructure.wopi.CollaboraDiscovery discovery;
    @Autowired private ma.jurika.dataroom.application.WopiService wopi;
    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;

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

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Wopi", "JUR-W0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')",
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

    /** Ouvre une seance comme le ferait l'employe (workspace pose par son JWT). */
    private String ouvrirSeance(UUID documentId) {
        org.mockito.Mockito.when(discovery.urlEditeurDocx()).thenReturn("http://collabora.test/edit?");
        TenantContext.set(workspaceId);
        try {
            return wopi.ouvrir(documentId, employeId, "Employe Test", ma.jurika.common.security.Role.EMPLOYE)
                    .accessToken();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID deposerActe() {
        TenantContext.set(workspaceId);
        try {
            var docx = new MockMultipartFile("file", "acte.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx".getBytes());
            return juridique.uploadVersion(dossierId, "AUTRE", "Acte a editer", ticketId, docx, employeId).id();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void collabora_lit_et_verrouille_le_document_avec_le_seul_jeton_de_seance() throws Exception {
        UUID documentId = deposerActe();
        String jeton = ouvrirSeance(documentId);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/dataroom/wopi/files/" + documentId).param("access_token", jeton))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.BaseFileName").exists());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/dataroom/wopi/files/" + documentId).param("access_token", jeton)
                        .header("X-WOPI-Override", "LOCK").header("X-WOPI-Lock", "verrou-l0"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataroom_wopi_verrous WHERE document_id = ?",
                Integer.class, documentId)).isEqualTo(1);
    }

    @Test
    void jeton_inconnu_refuse() throws Exception {
        UUID documentId = deposerActe();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/dataroom/wopi/files/" + documentId).param("access_token", "jeton-inconnu"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}

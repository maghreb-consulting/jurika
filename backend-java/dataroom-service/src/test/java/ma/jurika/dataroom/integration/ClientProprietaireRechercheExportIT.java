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
 * Lot L0, etape E19 (RG-DR-07) : pour un CLIENT, la recherche et l'export ZIP
 * du Dossier Juridique sont limites a SON dossier et a ses documents VISIBLES.
 * Role d'execution jurika_app, vraies migrations ; controleur appele avec le
 * principal et le workspace poses comme par JwtAuthFilter.
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
class ClientProprietaireRechercheExportIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_client_rech")
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
    @Autowired private ma.jurika.dataroom.api.JuridiqueController controleur;

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

        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Clients", "JUR-C0001");
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
        TenantContext.clear();
        clients();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private final UUID clientA = UUID.randomUUID();
    private final UUID clientB = UUID.randomUUID();
    private UUID dossierB;
    private UUID ticketB;
    private UUID docVisibleA;
    private UUID docMasqueA;
    private UUID docVisibleB;

    /** Appele a la fin de seed() : deux @BeforeEach n'ont pas d'ordre garanti. */
    private void clients() {
        org.mockito.Mockito.when(storage.download(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                new ObjectStorage.DownloadResult(new java.io.ByteArrayInputStream("%PDF".getBytes()), 4, "application/pdf"));
        SchemaJurikaDb.utilisateur(jdbc, clientA, workspaceId);
        SchemaJurikaDb.utilisateur(jdbc, clientB, workspaceId);
        jdbc.update("UPDATE entreprise_dossiers SET client_id = ? WHERE id = ?", clientA, dossierId);
        dossierB = UUID.randomUUID();
        ticketB = UUID.randomUUID();
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, client_id, responsable_id) VALUES (?, ?, ?, 'SARL', ?, '33333333-3333-3333-3333-333333333333')",
                dossierB, workspaceId, "BETA", clientB);
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, '33333333-3333-3333-3333-333333333333')
                """, ticketB, workspaceId, "T-2026-00842", "Creation SARL BETA",
                "CREATION", "DEROULEMENT_DEMARCHE", dossierB,
                OffsetDateTime.parse("2026-09-01T09:00:00Z"));
        docVisibleA = deposer(dossierId, ticketId, "Statuts A", true);
        docMasqueA = deposer(dossierId, ticketId, "Note interne A", false);
        docVisibleB = deposer(dossierB, ticketB, "Statuts B", true);
    }

    private UUID deposer(UUID dossier, UUID ticket, String titre, boolean visible) {
        TenantContext.set(workspaceId);
        UUID id;
        try {
            // Noms distincts : deux entrees ZIP de meme nom font echouer tout l'export
            // (defaut hors perimetre, porte au backlog).
            var docx = new MockMultipartFile("file", titre.replace(' ', '_') + ".pdf", "application/pdf", "%PDF".getBytes());
            id = juridique.uploadVersion(dossier, "AUTRE", titre, ticket, docx, employeId).id();
        } finally {
            TenantContext.clear();
        }
        jdbc.update("UPDATE dataroom_documents SET visible_client = ? WHERE id = ?", visible, id);
        return id;
    }

    private <T> T enClient(UUID client, java.util.function.Function<ma.jurika.common.security.AuthenticatedUser, T> appel) {
        var principal = new ma.jurika.common.security.AuthenticatedUser(
                client, workspaceId, client + "@client.test", ma.jurika.common.security.Role.CLIENT);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_CLIENT"))));
        TenantContext.set(workspaceId);
        try {
            return appel.apply(principal);
        } finally {
            TenantContext.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private List<UUID> recherche(UUID client, UUID dossier) {
        return enClient(client, u -> controleur.searchJuridique(u, dossier, null, null, null, null,
                ma.jurika.dataroom.api.dto.DataroomDtos.VersionScope.CURRENT, 50, 0))
                .items().stream().map(ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary::id).toList();
    }

    private List<String> entreesZip(UUID client, UUID dossier, List<UUID> ids) throws Exception {
        byte[] zip = enClient(client, u -> controleur.exportJuridiqueZip(u, dossier,
                new ma.jurika.dataroom.api.dto.DataroomDtos.BulkExportZipRequest(ids, false))).getBody();
        List<String> noms = new java.util.ArrayList<>();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) noms.add(e.getName());
        }
        return noms;
    }

    @Test
    void la_recherche_du_client_ne_rend_que_les_documents_visibles_de_son_dossier() {
        assertThat(recherche(clientA, dossierId)).containsExactly(docVisibleA);
    }

    @Test
    void la_recherche_sur_le_dossier_d_un_autre_client_est_refusee() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recherche(clientA, dossierB))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void l_export_du_client_exclut_les_documents_masques() throws Exception {
        List<String> noms = entreesZip(clientA, dossierId, List.of(docVisibleA, docMasqueA));
        assertThat(noms.stream().filter(n -> !n.equals("MANIFEST.txt"))).hasSize(1);
    }

    @Test
    void l_export_sur_le_dossier_d_un_autre_client_est_refuse() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entreesZip(clientA, dossierB, List.of(docVisibleB)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}

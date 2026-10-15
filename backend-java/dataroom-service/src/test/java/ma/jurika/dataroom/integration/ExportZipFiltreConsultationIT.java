package ma.jurika.dataroom.integration;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.BulkExportZipRequest;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E2, RG-DR-07, RG-CLI-01, RG-DOS-01) : l'export ZIP du Dossier Juridique
 * applique COTE SERVEUR le meme filtre que la consultation. Un document que la
 * vue ne montre pas a cet utilisateur (brouillon de generation, document masque
 * au client, document d'un autre dossier) n'entre jamais dans l'archive, quelle
 * que soit la selection envoyee. Deux documents de meme nom ne font plus echouer
 * l'export (backlog L0, "duplicate entry"), et une piece illisible est nommee au
 * MANIFEST au lieu de sortir vide.
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
class ExportZipFiltreConsultationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_export_zip")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql")
            .withReuse(false);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        SchemaJurikaDb.migrer(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "jurika_app");
        registry.add("spring.datasource.password", () -> "jurika_app_it");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private DataroomJuridiqueService juridique;
    @MockBean private ObjectStorage storage;
    @org.springframework.boot.test.mock.mockito.SpyBean
    private ma.jurika.dataroom.application.access.ClientAccessLogger accessLogger;
    @Autowired private ma.jurika.dataroom.api.JuridiqueController controleur;

    private static final UUID RESPONSABLE = SchemaJurikaDb.EMPLOYE_SEME;
    private final UUID autreEmploye = UUID.randomUUID();
    private final UUID superviseur = UUID.randomUUID();
    private final UUID clientA = UUID.randomUUID();
    private final UUID clientB = UUID.randomUUID();

    private UUID workspaceId;
    private UUID dossierA;
    private UUID ticketA;
    private UUID dossierB;
    private UUID ticketB;
    private UUID docVisibleA;
    private UUID docMasqueA;
    private UUID brouillonA;
    private UUID docVisibleB;

    /** Contenu servi par le stockage, par cle d'objet ; une cle absente = lecture impossible. */
    private final Map<String, byte[]> contenus = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_visibilite_evenements");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM dataroom_settings");
        jdbc.execute("DELETE FROM tickets");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE dataroom_documents "
                + "DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");
        jdbc.execute("ALTER TABLE dataroom_visibilite_evenements "
                + "DROP CONSTRAINT IF EXISTS dataroom_visibilite_evenements_acteur_id_fkey");

        workspaceId = UUID.randomUUID();
        dossierA = UUID.randomUUID();
        ticketA = UUID.randomUUID();
        dossierB = UUID.randomUUID();
        ticketB = UUID.randomUUID();
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Export", "JUR-E0001");
        SchemaJurikaDb.utilisateur(jdbc, clientA, workspaceId);
        SchemaJurikaDb.utilisateur(jdbc, clientB, workspaceId);
        dossier(dossierA, "ALPHA", clientA);
        dossier(dossierB, "BETA", clientB);
        ticket(ticketA, dossierA, "T-2026-00901");
        ticket(ticketB, dossierB, "T-2026-00902");

        contenus.clear();
        org.mockito.Mockito.when(storage.download(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            byte[] b = contenus.get((String) inv.getArgument(0));
            if (b == null) throw new IllegalStateException("objet absent du stockage");
            return new ObjectStorage.DownloadResult(new ByteArrayInputStream(b), b.length, "application/pdf");
        });

        docVisibleA = deposer(dossierA, ticketA, "STATUTS", "Statuts", "statuts.pdf", true);
        docMasqueA = deposer(dossierA, ticketA, "AUTRE", "Note interne", "note.pdf", false);
        docVisibleB = deposer(dossierB, ticketB, "STATUTS", "Statuts B", "statuts_b.pdf", true);
        brouillonA = brouillon(dossierA, ticketA, "PV_AGO", "PV en cours", "pv.pdf");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void dossier(UUID id, String nom, UUID client) {
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, client_id, responsable_id) "
                + "VALUES (?, ?, ?, 'SARL', ?, ?)", id, workspaceId, nom, client, RESPONSABLE);
    }

    private void ticket(UUID id, UUID dossier, String ref) {
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut,
                                    dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, ?, 'CREATION', 'DEROULEMENT_DEMARCHE', ?, ?, ?)
                """, id, workspaceId, ref, "Creation " + ref, dossier,
                OffsetDateTime.parse("2026-09-01T09:00:00Z"), RESPONSABLE);
    }

    private UUID deposer(UUID dossier, UUID ticket, String type, String titre, String fichier, boolean visible) {
        TenantContext.set(workspaceId);
        UUID id;
        try {
            var f = new MockMultipartFile("file", fichier, "application/pdf", ("%PDF " + titre).getBytes(StandardCharsets.UTF_8));
            id = juridique.uploadVersion(dossier, type, titre, ticket, f, RESPONSABLE).id();
        } finally {
            TenantContext.clear();
        }
        jdbc.update("UPDATE dataroom_documents SET visible_client = ? WHERE id = ?", visible, id);
        servir(id, "%PDF " + titre);
        return id;
    }

    private UUID brouillon(UUID dossier, UUID ticket, String type, String titre, String fichier) {
        TenantContext.set(workspaceId);
        UUID id;
        try {
            var f = new MockMultipartFile("file", fichier, "application/pdf", "%PDF brouillon".getBytes(StandardCharsets.UTF_8));
            id = juridique.enregistrerBrouillon(dossier, ticket, type, titre, f, RESPONSABLE).id();
        } finally {
            TenantContext.clear();
        }
        // Le type porte la visibilite client par defaut : le brouillon la porte deja.
        assertThat(jdbc.queryForObject("SELECT visible_client FROM dataroom_documents WHERE id = ?", Boolean.class, id))
                .isTrue();
        servir(id, "%PDF brouillon");
        return id;
    }

    private void servir(UUID docId, String contenu) {
        String cle = jdbc.queryForObject("SELECT object_key FROM dataroom_documents WHERE id = ?", String.class, docId);
        contenus.put(cle, contenu.getBytes(StandardCharsets.UTF_8));
    }

    private AuthenticatedUser principal(UUID id, Role role) {
        return new AuthenticatedUser(id, workspaceId, id + "@test.ma", role);
    }

    private <T> T en(AuthenticatedUser u, Function<AuthenticatedUser, T> appel) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        u, null, List.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_" + u.role().name()))));
        TenantContext.set(workspaceId);
        try {
            return appel.apply(u);
        } finally {
            TenantContext.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    /** Entrees de l'archive (nom -> contenu), MANIFEST compris. */
    private Map<String, String> zip(AuthenticatedUser u, UUID dossier, List<UUID> ids, boolean anciennes) throws Exception {
        byte[] zip = en(u, x -> controleur.exportJuridiqueZip(x, dossier,
                new BulkExportZipRequest(ids, anciennes))).getBody();
        Map<String, String> out = new LinkedHashMap<>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                out.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static List<String> documents(Map<String, String> entrees) {
        List<String> l = new ArrayList<>(entrees.keySet());
        l.remove("MANIFEST.txt");
        return l;
    }

    // ------------------------------------------------------------------ client

    @Test
    void le_client_ne_recoit_jamais_un_brouillon_meme_avec_les_anciennes_versions() throws Exception {
        Map<String, String> z = zip(principal(clientA, Role.CLIENT), dossierA,
                List.of(docVisibleA, docMasqueA, brouillonA, docVisibleB), true);
        assertThat(documents(z)).containsExactly("STATUTS/statuts.pdf");
        assertThat(z.values()).noneMatch(c -> c.contains("brouillon"));
    }

    @Test
    void le_client_ne_recoit_pas_un_document_d_un_autre_dossier_glisse_dans_la_selection() throws Exception {
        Map<String, String> z = zip(principal(clientA, Role.CLIENT), dossierA, List.of(docVisibleB), true);
        assertThat(documents(z)).isEmpty();
    }

    @Test
    void le_client_sans_droit_de_telechargement_est_refuse() {
        jdbc.update("INSERT INTO dataroom_settings(dossier_id, workspace_id, perm_download) VALUES (?, ?, false)",
                dossierA, workspaceId);
        assertThatThrownBy(() -> zip(principal(clientA, Role.CLIENT), dossierA, List.of(docVisibleA), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void le_client_sans_droit_de_consultation_est_refuse() {
        jdbc.update("INSERT INTO dataroom_settings(dossier_id, workspace_id, perm_consultation) VALUES (?, ?, false)",
                dossierA, workspaceId);
        assertThatThrownBy(() -> zip(principal(clientA, Role.CLIENT), dossierA, List.of(docVisibleA), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void l_export_du_client_est_journalise_document_par_document() throws Exception {
        zip(principal(clientA, Role.CLIENT), dossierA, List.of(docVisibleA, docMasqueA), false);
        org.mockito.Mockito.verify(accessLogger).log(org.mockito.ArgumentMatchers.eq(dossierA),
                org.mockito.ArgumentMatchers.eq(docVisibleA), org.mockito.ArgumentMatchers.eq("DOWNLOAD_DOC"),
                org.mockito.ArgumentMatchers.any(AuthenticatedUser.class));
        org.mockito.Mockito.verify(accessLogger, org.mockito.Mockito.never()).log(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(docMasqueA), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(AuthenticatedUser.class));
    }

    // ------------------------------------------------------------------ employes, superviseur

    @Test
    void l_employe_responsable_recoit_les_documents_masques_mais_pas_les_brouillons() throws Exception {
        Map<String, String> z = zip(principal(RESPONSABLE, Role.EMPLOYE), dossierA,
                List.of(docVisibleA, docMasqueA, brouillonA, docVisibleB), true);
        assertThat(documents(z)).containsExactlyInAnyOrder("STATUTS/statuts.pdf", "AUTRE/note.pdf");
    }

    @Test
    void un_employe_qui_n_est_pas_responsable_du_dossier_est_refuse() {
        assertThatThrownBy(() -> zip(principal(autreEmploye, Role.EMPLOYE), dossierA, List.of(docVisibleA), false))
                .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
    }

    @Test
    void le_superviseur_recoit_les_documents_du_dossier_mais_pas_les_brouillons() throws Exception {
        Map<String, String> z = zip(principal(superviseur, Role.SUPERVISEUR), dossierA,
                List.of(docVisibleA, docMasqueA, brouillonA), true);
        assertThat(documents(z)).containsExactlyInAnyOrder("STATUTS/statuts.pdf", "AUTRE/note.pdf");
    }

    // ------------------------------------------------------------------ noms, lecture

    @Test
    void deux_documents_de_meme_nom_sortent_sous_deux_noms_distincts() throws Exception {
        UUID bis = deposer(dossierA, ticketA, "STATUTS", "Statuts mis a jour", "statuts.pdf", true);
        Map<String, String> z = zip(principal(RESPONSABLE, Role.EMPLOYE), dossierA, List.of(docVisibleA, bis), false);
        assertThat(documents(z)).containsExactly("STATUTS/statuts.pdf", "STATUTS/statuts (2).pdf");
        assertThat(z.get("STATUTS/statuts.pdf")).isEqualTo("%PDF Statuts");
        assertThat(z.get("STATUTS/statuts (2).pdf")).isEqualTo("%PDF Statuts mis a jour");
    }

    @Test
    void une_piece_illisible_est_nommee_au_manifest_et_ne_sort_pas_vide() throws Exception {
        String cle = jdbc.queryForObject("SELECT object_key FROM dataroom_documents WHERE id = ?", String.class, docMasqueA);
        contenus.remove(cle);
        Map<String, String> z = zip(principal(RESPONSABLE, Role.EMPLOYE), dossierA, List.of(docVisibleA, docMasqueA), false);
        assertThat(documents(z)).containsExactly("STATUTS/statuts.pdf");
        assertThat(z.get("MANIFEST.txt")).contains("Documents inclus     : 1").contains("note.pdf");
    }
}

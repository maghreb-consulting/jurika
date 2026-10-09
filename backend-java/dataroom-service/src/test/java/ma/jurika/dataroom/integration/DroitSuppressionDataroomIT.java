package ma.jurika.dataroom.integration;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.JuridiqueController;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L1, etape E8 (RG-DR-06, CDC 3.2, RG-DOS-01) : en role d'execution jurika_app
 * (RLS), un employe ne supprime un document que s'il est responsable du dossier ET
 * que le superviseur lui a accorde le droit (auth V35) ; la suppression est logique
 * (le document reste en base, marque remplace) et tracee.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@ActiveProfiles("it")
class DroitSuppressionDataroomIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_droit_suppr")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql");

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

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    @Autowired private DataroomJuridiqueService juridique;
    @Autowired private JuridiqueController controleur;
    @MockBean private ObjectStorage storage;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID responsable = UUID.randomUUID();
    private final UUID autre = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private UUID document;

    @BeforeEach
    void seed() {
        jdbc.execute("ALTER TABLE dataroom_documents DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");
        SchemaJurikaDb.workspace(jdbc, workspaceId, "Cabinet Droits", "JUR-" + workspaceId.toString().substring(0, 5).toUpperCase());
        SchemaJurikaDb.utilisateur(jdbc, responsable, workspaceId);
        SchemaJurikaDb.utilisateur(jdbc, autre, workspaceId);
        jdbc.update("UPDATE users SET role = 'EMPLOYE' WHERE id IN (?, ?)", responsable, autre);
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) "
                + "VALUES (?, ?, ?, 'SARL', ?)", dossierId, workspaceId, "DROITS " + dossierId, responsable);
        jdbc.update("INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, dossier_id, cree_par_id) "
                        + "VALUES (?, ?, ?, 'T', 'CREATION', 'DEROULEMENT_DEMARCHE', ?, ?)",
                ticketId, workspaceId, "T-L1-" + ticketId.toString().substring(0, 6), dossierId, responsable);
        TenantContext.set(workspaceId);
        try {
            document = juridique.uploadVersion(dossierId, "AUTRE", "Piece", ticketId,
                    new MockMultipartFile("file", "piece.pdf", "application/pdf", "%PDF".getBytes()), responsable).id();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void supprimer(UUID employe) {
        AuthenticatedUser principal = new AuthenticatedUser(employe, workspaceId, employe + "@rls.test", Role.EMPLOYE);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYE"))));
        TenantContext.set(workspaceId);
        try {
            controleur.deleteJuridique(principal, document);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean enVigueur() {
        return jdbc.queryForObject("SELECT replaced_at IS NULL FROM dataroom_documents WHERE id = ?",
                Boolean.class, document);
    }

    @Test
    void sans_droit_le_responsable_ne_supprime_pas() {
        assertThatThrownBy(() -> supprimer(responsable)).isInstanceOf(AccessDeniedException.class);
        assertThat(enVigueur()).isTrue();
    }

    @Test
    void avec_le_droit_le_responsable_supprime_logiquement_et_c_est_trace() {
        jdbc.update("UPDATE users SET droit_suppression_dataroom = TRUE WHERE id = ?", responsable);
        supprimer(responsable);
        assertThat(enVigueur()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dataroom_documents WHERE id = ?", Integer.class, document))
                .as("suppression logique : la ligne reste").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'DOCUMENT_DELETED' "
                + "AND entity_id = ?", Integer.class, document)).isEqualTo(1);
    }

    @Test
    void un_autre_employe_meme_avec_le_droit_ne_voit_pas_le_document() {
        jdbc.update("UPDATE users SET droit_suppression_dataroom = TRUE WHERE id = ?", autre);
        assertThatThrownBy(() -> supprimer(autre)).isInstanceOf(NotFoundException.class);
        assertThat(enVigueur()).isTrue();
    }

    // ---- Lot L1, etape E9 (RG-DOS-01) : acces directs par UUID ----

    private Object enEmploye(UUID employe, java.util.function.Function<AuthenticatedUser, Object> appel) {
        AuthenticatedUser principal = new AuthenticatedUser(employe, workspaceId, employe + "@rls.test", Role.EMPLOYE);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYE"))));
        TenantContext.set(workspaceId);
        try {
            return appel.apply(principal);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void un_employe_non_responsable_n_accede_a_rien_par_uuid() {
        org.mockito.Mockito.when(storage.download(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                new ObjectStorage.DownloadResult(new java.io.ByteArrayInputStream("%PDF".getBytes()), 4, "application/pdf"));
        List<java.util.function.Function<AuthenticatedUser, Object>> acces = List.of(
                u -> controleur.juridiqueView(u, dossierId, null, null, null),
                u -> controleur.previewJuridique(u, document),
                u -> controleur.downloadJuridique(u, document),
                u -> controleur.listVersions(u, document),
                u -> controleur.listBrouillons(u, ticketId));
        for (var a : acces) {
            assertThatThrownBy(() -> enEmploye(autre, a)).isInstanceOf(NotFoundException.class);
        }
        // Le responsable, lui, y accede.
        for (var a : acces) {
            enEmploye(responsable, a);
        }
    }
}

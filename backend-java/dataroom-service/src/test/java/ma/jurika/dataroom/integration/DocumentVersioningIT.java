package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.application.DataroomJuridiqueService;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 2026-06-23 — Versioning explicite des documents juridiques.
 *
 * <p>Couvre les 5 flux ajoutés à {@link DataroomJuridiqueService} :
 * <ol>
 *   <li>{@code replaceAsNewVersion} archive la version précédente + active la
 *       nouvelle + persiste le motif ;</li>
 *   <li>{@code listVersions} retourne le lignage complet (active + historique)
 *       trié desc par version ;</li>
 *   <li>{@code restoreVersion} swap les flags is_current entre la version
 *       ciblée et l'ancienne active (pas d'INSERT) ;</li>
 *   <li>{@code loadVersionForDownload} accepte une version arbitraire si elle
 *       appartient au même slot ;</li>
 *   <li>{@code uploadVersion(replacePrevious=false)} crée un Document distinct,
 *       même titre identique — pas de déplacement.</li>
 * </ol>
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
class DocumentVersioningIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_versioning")
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
    @Autowired private DataroomJuridiqueService juridique;
    @MockBean private ObjectStorage storage;

    private UUID workspaceId;
    private UUID dossierId;
    private UUID uploaderId;

    @BeforeEach
    void seed() {
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        // FK uploaded_by -> users : on droppe en IT pour pouvoir générer des UUID aléatoires.
        jdbc.execute("ALTER TABLE dataroom_documents DROP CONSTRAINT IF EXISTS dataroom_documents_uploaded_by_fkey");

        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        uploaderId = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces(id, name, code_workspace) VALUES (?, ?, ?)",
                workspaceId, "Cabinet Versioning", "JUR-V0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierId, workspaceId, "SARL Versioning");

        TenantContext.set(workspaceId);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceId + "', false)");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes());
    }

    /** Crée un Document logique V1 via uploadVersion(replacePrevious=true) — démarre le slot. */
    private DocumentSummary createInitial(String title, String content) {
        return juridique.uploadVersion(dossierId, "STATUTS", title, null,
                pdf("v1-" + title + ".pdf", content), uploaderId, true);
    }

    // ─── 1. replaceAsNewVersion ──────────────────────────────────────────

    @Test
    @DisplayName("replaceAsNewVersion archive l'ancienne, active la nouvelle, persiste le motif")
    void replaceArchivesPreviousActivatesNewWithMotif() {
        DocumentSummary v1 = createInitial("Statuts SARL", "v1-content");
        assertThat(v1.version()).isEqualTo((short) 1);
        assertThat(v1.current()).isTrue();

        DocumentSummary v2 = juridique.replaceAsNewVersion(v1.id(),
                pdf("v2.pdf", "v2-content"),
                "Modification : Transfert du siège", uploaderId);

        assertThat(v2.version()).isEqualTo((short) 2);
        assertThat(v2.current()).isTrue();
        assertThat(v2.motif()).isEqualTo("Modification : Transfert du siège");
        // Le slot est le même : dossier+type+title.
        assertThat(v2.dossierId()).isEqualTo(v1.dossierId());
        assertThat(v2.documentType()).isEqualTo(v1.documentType());
        assertThat(v2.title()).isEqualTo(v1.title());

        // L'ancienne en historique avec motif.
        Boolean v1Current = jdbc.queryForObject(
                "SELECT is_current FROM dataroom_documents WHERE id = ?",
                Boolean.class, v1.id());
        String v1Motif = jdbc.queryForObject(
                "SELECT motif FROM dataroom_documents WHERE id = ?",
                String.class, v1.id());
        assertThat(v1Current).isFalse();
        assertThat(v1Motif).isEqualTo("Modification : Transfert du siège");
    }

    @Test
    @DisplayName("replaceAsNewVersion refuse si la cible n'est plus la version active")
    void replaceRejectsNonCurrentTarget() {
        DocumentSummary v1 = createInitial("Statuts", "v1");
        juridique.replaceAsNewVersion(v1.id(), pdf("v2.pdf", "v2"), null, uploaderId);
        // v1 est maintenant historique : on ne doit plus pouvoir cibler v1.
        assertThatThrownBy(() -> juridique.replaceAsNewVersion(v1.id(),
                pdf("v3.pdf", "v3"), null, uploaderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n'est plus la version active");
    }

    // ─── 2. listVersions ─────────────────────────────────────────────────

    @Test
    @DisplayName("listVersions retourne le lignage complet trié par version desc")
    void listVersionsReturnsLineageDesc() {
        DocumentSummary v1 = createInitial("Statuts", "v1");
        DocumentSummary v2 = juridique.replaceAsNewVersion(v1.id(),
                pdf("v2.pdf", "v2"), "motif 1->2", uploaderId);
        DocumentSummary v3 = juridique.replaceAsNewVersion(v2.id(),
                pdf("v3.pdf", "v3"), "motif 2->3", uploaderId);

        // Peu importe par quel id on demande, on remonte le slot.
        List<DocumentSummary> lineage = juridique.listVersions(v1.id());
        assertThat(lineage).hasSize(3);
        assertThat(lineage.get(0).version()).isEqualTo((short) 3);
        assertThat(lineage.get(0).current()).isTrue();
        assertThat(lineage.get(0).id()).isEqualTo(v3.id());
        assertThat(lineage.get(1).version()).isEqualTo((short) 2);
        assertThat(lineage.get(1).current()).isFalse();
        assertThat(lineage.get(1).motif()).isEqualTo("motif 2->3");
        assertThat(lineage.get(2).version()).isEqualTo((short) 1);
        assertThat(lineage.get(2).current()).isFalse();
        assertThat(lineage.get(2).motif()).isEqualTo("motif 1->2");
    }

    // ─── 3. restoreVersion ───────────────────────────────────────────────

    @Test
    @DisplayName("restoreVersion swap is_current (pas d'INSERT) + motif sur l'ancienne courante")
    void restoreVersionSwapsCurrentFlags() {
        DocumentSummary v1 = createInitial("Statuts", "v1");
        DocumentSummary v2 = juridique.replaceAsNewVersion(v1.id(),
                pdf("v2.pdf", "v2"), "v2 initiale", uploaderId);

        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_documents", Long.class);

        DocumentSummary restored = juridique.restoreVersion(v2.id(), v1.id(),
                "Retour à v1 (erreur dans v2)");

        long after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_documents", Long.class);
        assertThat(after).as("pas de nouvelle ligne").isEqualTo(before);
        assertThat(restored.id()).isEqualTo(v1.id());
        assertThat(restored.current()).isTrue();

        Boolean v1Current = jdbc.queryForObject(
                "SELECT is_current FROM dataroom_documents WHERE id = ?",
                Boolean.class, v1.id());
        Boolean v2Current = jdbc.queryForObject(
                "SELECT is_current FROM dataroom_documents WHERE id = ?",
                Boolean.class, v2.id());
        String v2Motif = jdbc.queryForObject(
                "SELECT motif FROM dataroom_documents WHERE id = ?",
                String.class, v2.id());
        assertThat(v1Current).isTrue();
        assertThat(v2Current).isFalse();
        assertThat(v2Motif).isEqualTo("Retour à v1 (erreur dans v2)");
    }

    @Test
    @DisplayName("restoreVersion idempotent si la cible est déjà active")
    void restoreVersionIdempotent() {
        DocumentSummary v1 = createInitial("Statuts", "v1");
        DocumentSummary res = juridique.restoreVersion(v1.id(), v1.id(), "noop");
        assertThat(res.id()).isEqualTo(v1.id());
        assertThat(res.current()).isTrue();
    }

    @Test
    @DisplayName("restoreVersion refuse une version d'un autre slot")
    void restoreVersionRejectsCrossSlot() {
        DocumentSummary statuts = createInitial("Statuts", "s");
        DocumentSummary pv = juridique.uploadVersion(dossierId, "PV_MODIFICATION",
                "PV AGE", null, pdf("pv.pdf", "pv"), uploaderId, true);
        assertThatThrownBy(() -> juridique.restoreVersion(statuts.id(), pv.id(), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("même Document logique");
    }

    // ─── 4. loadVersionForDownload ───────────────────────────────────────

    @Test
    @DisplayName("loadVersionForDownload accepte une version historique du même slot")
    void downloadVersionAcceptsHistoricalVersion() {
        DocumentSummary v1 = createInitial("Statuts", "v1");
        DocumentSummary v2 = juridique.replaceAsNewVersion(v1.id(),
                pdf("v2.pdf", "v2"), null, uploaderId);

        DocumentEntity old = juridique.loadVersionForDownload(v2.id(), v1.id());
        assertThat(old.getId()).isEqualTo(v1.id());
        assertThat(old.isCurrent()).isFalse();
    }

    @Test
    @DisplayName("loadVersionForDownload refuse une version d'un autre slot")
    void downloadVersionRejectsCrossSlot() {
        DocumentSummary statuts = createInitial("Statuts", "s");
        DocumentSummary pv = juridique.uploadVersion(dossierId, "PV_MODIFICATION",
                "PV AGE", null, pdf("pv.pdf", "pv"), uploaderId, true);
        assertThatThrownBy(() -> juridique.loadVersionForDownload(statuts.id(), pv.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("même Document logique");
    }

    // ─── 5. Fix DR1 — UN SEUL courant par slot (2026-08-17) ──────────────

    @Test
    @DisplayName("DR1 — re-déposer le MÊME slot crée une version, jamais un second courant")
    void redepotMemeSlotCreeUneVersion() {
        DocumentSummary v1 = createInitial("Statuts", "v1");

        // ATTENTE CORRIGÉE. Ce test exigeait auparavant que `replacePrevious=false`
        // crée un SECOND document courant portant le même (type + titre), en assumant
        // une sémantique « nouveau document ». C'était le défaut DR1 : la fiche
        // affichait alors « deux Statuts en vigueur » côte à côte, sans qu'aucun des
        // deux ne soit désigné comme l'acte applicable. Le dépôt d'un slot déjà occupé
        // est désormais routé vers le VERSIONING, quelle que soit l'origine du dépôt.
        DocumentSummary v2 = juridique.uploadVersion(dossierId, "STATUTS",
                "Statuts", null, pdf("other.pdf", "other"), uploaderId, false);

        assertThat(v2.id()).isNotEqualTo(v1.id());
        assertThat(v2.version()).isEqualTo((short) 2);
        assertThat(v2.current()).isTrue();

        // Le précédent bascule en historique — il reste consultable, plus applicable.
        Boolean v1Current = jdbc.queryForObject(
                "SELECT is_current FROM dataroom_documents WHERE id = ?",
                Boolean.class, v1.id());
        assertThat(v1Current).isFalse();

        long courants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_documents WHERE dossier_id = ? AND is_current",
                Long.class, dossierId);
        assertThat(courants).isEqualTo(1L);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_documents WHERE dossier_id = ?",
                Long.class, dossierId);
        assertThat(total).isEqualTo(2L);
    }

    @Test
    @DisplayName("DR1 — un TITRE différent reste un document distinct (une CIN par personne)")
    void titreDifferentResteUnDocumentDistinct() {
        // Le slot inclut le titre : sans cela, la CIN de la 2e personne deviendrait une
        // « version » de celle de la 1re et disparaîtrait de la vue (cf. défaut A5).
        DocumentSummary a = juridique.uploadVersion(dossierId, "CIN_NOUVELLE",
                "CIN nouvelle - Ahmed ALAOUI", null, pdf("a.pdf", "a"), uploaderId, false);
        DocumentSummary b = juridique.uploadVersion(dossierId, "CIN_NOUVELLE",
                "CIN nouvelle - Salma BENJELLOUN", null, pdf("b.pdf", "b"), uploaderId, false);

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(a.version()).isEqualTo((short) 1);
        assertThat(b.version()).isEqualTo((short) 1);
        long courants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dataroom_documents WHERE dossier_id = ? AND is_current",
                Long.class, dossierId);
        assertThat(courants).isEqualTo(2L);
    }
}

package ma.jurika.dataroom.integration;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierFiscalDetailedView;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 14 ter C1 -- FiscalListAndFilterIT (6 cas).
 *
 * Couvre DataroomFiscalService.list() / detailedView() / softDelete() :
 *   - cas 1 : list par categorie filtre seulement les bons docs (TVA != IS)
 *   - cas 2 : detailedView retourne FiscalCategoryCount pour les 7 categories CGI
 *   - cas 3 : softDelete masque le doc de la liste (deleted = true exclu)
 *   - cas 4 : soft-deleted documents exclus de countByCategorie
 *   - cas 5 : RLS cross-workspace (B ne voit pas docs de A) -- via TenantContext switch
 *   - cas 6 : multi-exercices, list n'inclut que l'exercice cible
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
class FiscalListAndFilterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_listfilter")
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

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID dossierA;
    private UUID dossierB;
    private UUID exerciceA;

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

        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
        dossierA = UUID.randomUUID();
        dossierB = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces(id, name, code_workspace) VALUES (?, ?, ?)",
                workspaceA, "Cabinet A", "JUR-A0001");
        jdbc.update("INSERT INTO workspaces(id, name, code_workspace) VALUES (?, ?, ?)",
                workspaceB, "Cabinet B", "JUR-B0001");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierA, workspaceA, "SARL A");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale) VALUES (?, ?, ?)",
                dossierB, workspaceB, "SARL B");

        TenantContext.set(workspaceA);
        jdbc.execute("SELECT set_config('app.current_workspace_id', '" + workspaceA + "', false)");

        ExerciceFiscalSummary ex = exerciceService.open(dossierA, (short) 2060, null, null, true, UUID.randomUUID());
        exerciceA = UUID.fromString(ex.id().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[]{1, 2, 3});
    }

    private UUID upload(UUID dossier, UUID exercice, String categorie, String subClass, String title) {
        FiscalDocumentSummary d = fiscal.upload(dossier, exercice, categorie, subClass, title,
                /* commentaire */ "CONTENTIEUX".equals(categorie)
                        ? "Commentaire long suffisant pour passer la validation 20 chars"
                        : null,
                null, null, null, null,
                pdf(title + ".pdf"),
                UUID.randomUUID(), /* isClientRole */ false);
        return d.id();
    }

    @Test
    @DisplayName("Cas 1 : filtre par categorie -- list('TVA') ne retourne que les docs TVA")
    void filterByCategoryReturnsOnlyMatchingDocs() {
        upload(dossierA, exerciceA, "TVA", "DECLARATION_MENSUELLE", "tva-1");
        upload(dossierA, exerciceA, "TVA", "DECLARATION_TRIMESTRIELLE", "tva-2");
        upload(dossierA, exerciceA, "IS", "DECLARATION_ANNUELLE", "is-1");
        upload(dossierA, exerciceA, "ATTESTATIONS", "ATTESTATION_TVA", "att-1");

        List<FiscalDocumentSummary> tvaOnly = fiscal.list(dossierA, exerciceA, "TVA");
        assertThat(tvaOnly).hasSize(2);
        assertThat(tvaOnly).allMatch(d -> "TVA".equals(d.categorie()));
    }

    @Test
    @DisplayName("Cas 2 : detailedView retourne FiscalCategoryCount pour toutes les categories CGI (compteurs corrects)")
    void detailedViewReturnsCategoryCountsForAllSevenCgiCategories() {
        upload(dossierA, exerciceA, "TVA", "DECLARATION_MENSUELLE", "tva-x");
        upload(dossierA, exerciceA, "TVA", "DECLARATION_TRIMESTRIELLE", "tva-y");
        upload(dossierA, exerciceA, "IS", "DECLARATION_ANNUELLE", "is-x");

        DossierFiscalDetailedView view = fiscal.detailedView(dossierA, exerciceA);
        // Prompt G (2026-06-23) a ajoute la categorie fourre-tout "AUTRE" -> 8 categories.
        int expected = ma.jurika.dataroom.application.DataroomFiscalService.CATEGORIES_CGI.size();
        assertThat(view.categoriesCgi()).hasSize(expected);
        assertThat(view.compteurs()).hasSize(expected);
        var byCat = view.compteurs().stream()
                .collect(Collectors.toMap(c -> c.categorie(), c -> c.total()));
        assertThat(byCat).containsEntry("TVA", 2L);
        assertThat(byCat).containsEntry("IS", 1L);
        assertThat(byCat).containsEntry("IR", 0L);
        assertThat(byCat).containsEntry("CONTENTIEUX", 0L);
    }

    @Test
    @DisplayName("Cas 3 : softDelete -> doc disparait de list() (deleted=true exclu)")
    void softDeleteHidesDocFromList() {
        UUID id = upload(dossierA, exerciceA, "TVA", "DECLARATION_MENSUELLE", "to-delete");
        assertThat(fiscal.list(dossierA, exerciceA, "TVA")).hasSize(1);

        fiscal.softDelete(id, UUID.randomUUID());
        assertThat(fiscal.list(dossierA, exerciceA, "TVA")).isEmpty();
    }

    @Test
    @DisplayName("Cas 4 : doc soft-deleted exclu de countByCategorie/detailedView")
    void softDeletedDocsExcludedFromCounts() {
        UUID id1 = upload(dossierA, exerciceA, "IS", "DECLARATION_ANNUELLE", "is-keeper");
        UUID id2 = upload(dossierA, exerciceA, "IS", "DECLARATION_ANNUELLE", "is-to-delete");
        fiscal.softDelete(id2, UUID.randomUUID());

        DossierFiscalDetailedView view = fiscal.detailedView(dossierA, exerciceA);
        long isCount = view.compteurs().stream()
                .filter(c -> "IS".equals(c.categorie()))
                .mapToLong(c -> c.total()).sum();
        assertThat(isCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("Cas 5 : RLS cross-workspace -- workspace B ne voit aucun doc de workspace A")
    void rlsBlocksCrossWorkspaceListing() {
        // Upload sous workspace A
        upload(dossierA, exerciceA, "TVA", "DECLARATION_MENSUELLE", "wsA-tva");
        assertThat(fiscal.list(dossierA, exerciceA, "TVA")).hasSize(1);

        // L'utilisateur testcontainer est superuser et bypass RLS, on doit donc
        // executer la lecture sous le role NOSUPERUSER NOBYPASSRLS app_no_super
        // (cree dans testcontainers-init.sql) pour valider que la policy
        // fiscal_isolation filtre vraiment cote DB.
        Long visibleFromB = queryCountUnderWorkspace(workspaceB, dossierA);
        assertThat(visibleFromB).isZero();

        // Sanity : sous workspaceA la ligne reste visible.
        Long visibleFromA = queryCountUnderWorkspace(workspaceA, dossierA);
        assertThat(visibleFromA).isEqualTo(1L);
    }

    /**
     * COUNT(*) direct via JDBC en mode non-superuser pour que la RLS
     * {@code fiscal_isolation} s'applique reellement (l'utilisateur testcontainer
     * standard est superuser et bypass toutes les policies).
     */
    private Long queryCountUnderWorkspace(UUID workspaceId, UUID dossierId) {
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                // Grant lecture une seule fois -- idempotent (IF NOT EXISTS impossible ici).
                st.execute("GRANT SELECT ON dataroom_fiscal_documents TO app_no_super");
                st.execute("SET LOCAL ROLE app_no_super");
                st.execute("SET LOCAL app.current_workspace_id = '" + workspaceId + "'");
                try (var ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM dataroom_fiscal_documents " +
                                "WHERE dossier_id = ? AND is_deleted = false")) {
                    ps.setObject(1, dossierId);
                    try (var rs = ps.executeQuery()) {
                        rs.next();
                        long n = rs.getLong(1);
                        conn.commit();
                        return n;
                    }
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
    }

    @Test
    @DisplayName("Cas 6 : multi-exercices sur meme dossier -- list filtre uniquement l'exercice cible")
    void listFiltersByExercice() {
        ExerciceFiscalSummary ex2 = exerciceService.open(dossierA, (short) 2061, null, null, true, UUID.randomUUID());
        UUID exerciceA2 = UUID.fromString(ex2.id().toString());

        upload(dossierA, exerciceA, "TVA", "DECLARATION_MENSUELLE", "ex2060-tva");
        upload(dossierA, exerciceA2, "TVA", "DECLARATION_MENSUELLE", "ex2061-tva");

        List<FiscalDocumentSummary> ex2060 = fiscal.list(dossierA, exerciceA, "TVA");
        List<FiscalDocumentSummary> ex2061 = fiscal.list(dossierA, exerciceA2, "TVA");

        assertThat(ex2060).hasSize(1);
        assertThat(ex2061).hasSize(1);
        assertThat(ex2060.get(0).exerciceFiscalId()).isEqualTo(exerciceA);
        assertThat(ex2061.get(0).exerciceFiscalId()).isEqualTo(exerciceA2);
    }
}

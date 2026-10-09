package ma.jurika.dataroom.integration;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 7 / TASK 7.2 -- IT multi-tenant : verifie l'isolation RLS PostgreSQL
 * sur dataroom_documents.
 *
 * Pourquoi cet IT est crucial :
 *   Sans RLS, un acces direct a la table renverrait toutes les rangees du
 *   schema. Avec RLS et la policy `dataroom_docs_isolation` (V5), seules
 *   les rangees du workspace_id courant (positionne via
 *   `SET app.current_workspace_id = <uuid>`) sont visibles.
 *
 * Le test ne passe pas par MockMvc : il valide le contrat SQL direct, ce
 * qui est plus robuste pour cette regression de securite.
 *
 * Note Sprint 7 : 3 autres IT prevus par le plan (Rbac, Suspension,
 * Permissions) seront ajoutes en suite -- ils necessitent un setup MockMvc
 * + JWT + AuthenticatedUser plus consequent. Voir DataroomRbacIT.todo
 * (placeholder) dans le meme package.
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
class DataroomMultitenancyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_dataroom")
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
        // Pas de MinIO ni de Redis dans cet IT : on neutralise via env factices,
        // les beans ne sont pas reellement appeles dans ces tests SQL.
        registry.add("jurika.minio.endpoint", () -> "http://localhost:9099");
        registry.add("jurika.minio.access-key", () -> "test");
        registry.add("jurika.minio.secret-key", () -> "test-secret");
        registry.add("jurika.minio.bucket", () -> "jurika-it");
    }

    /** Preparation et assertions en PROPRIETAIRE, hors RLS (lot L0). */
    private final JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID dossierA;
    private UUID dossierB;

    @BeforeEach
    void seed() {
        // Reset RLS bypass settings
        jdbc.execute("SELECT set_config('app.current_workspace_id', '', false)");

        // Nettoyage (clear bypass pour pouvoir TRUNCATE / DELETE -- on desactive RLS)
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        jdbc.execute("DELETE FROM dataroom_documents");
        jdbc.execute("DELETE FROM entreprise_dossiers");
        jdbc.execute("DELETE FROM workspaces");
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");

        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
        dossierA = UUID.randomUUID();
        dossierB = UUID.randomUUID();

        // Workspaces
        SchemaJurikaDb.workspace(jdbc, workspaceA, "Cabinet A", "JUR-AAAAA");
        SchemaJurikaDb.workspace(jdbc, workspaceB, "Cabinet B", "JUR-BBBBB");

        // Dossiers
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')",
                dossierA, workspaceA, "SARL Test A");
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')",
                dossierB, workspaceB, "SARL Test B");

        // Documents -- 3 pour A, 2 pour B (avec bypass RLS via DISABLE)
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type, title,
                                                version, is_current, object_key, filename, size_bytes)
                VALUES (?, ?, ?, 'STATUTS', ?, 1, true, ?, ?, 1024)
                """,
                    UUID.randomUUID(), workspaceA, dossierA, "Statuts A " + i,
                    "ws/" + workspaceA + "/doc-" + i, "statuts-a-" + i + ".pdf");
        }
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type, title,
                                                version, is_current, object_key, filename, size_bytes)
                VALUES (?, ?, ?, 'STATUTS', ?, 1, true, ?, ?, 1024)
                """,
                    UUID.randomUUID(), workspaceB, dossierB, "Statuts B " + i,
                    "ws/" + workspaceB + "/doc-" + i, "statuts-b-" + i + ".pdf");
        }
        jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
    }

    // ------------------------------------------------------------------
    // Diagnostic 2026-07 : l'utilisateur de connexion testcontainer est
    // SUPERUSER et bypasse TOUTES les policies RLS. Compter les lignes sous
    // cette connexion ne teste donc PAS l'isolation (elle renvoie toujours les
    // 5 lignes du seed) -- c'est ce qui faisait echouer 4/6 de facon
    // pre-existante. La policy V5 est correcte ; il faut simplement exercer la
    // lecture sous un role NOSUPERUSER NOBYPASSRLS (app_no_super, cree dans
    // testcontainers-init.sql), exactement comme FiscalListAndFilterIT cas 5.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RLS : workspace A voit ses 3 docs uniquement (sous role non-superuser)")
    void rlsScopesToCurrentWorkspaceA() {
        assertThat(countUnderWorkspace(workspaceA.toString())).isEqualTo(3L);
    }

    @Test
    @DisplayName("RLS : lecture croisee interdite -- workspace B ne voit que ses 2 docs, jamais ceux de A")
    void rlsScopesToCurrentWorkspaceB() {
        assertThat(countUnderWorkspace(workspaceB.toString())).isEqualTo(2L);
    }

    @Test
    @DisplayName("RLS : sans app.current_workspace_id positionne -> 0 ligne visible")
    void rlsBlocksWhenWorkspaceUnset() {
        // string vide -> NULLIF dans la policy retourne NULL -> jamais egal
        assertThat(countUnderWorkspace("")).isEqualTo(0L);
    }

    @Test
    @DisplayName("RLS : changement de workspace -> les rangees A ne fuient pas vers B")
    void rlsSwitchesAcrossWorkspaces() {
        assertThat(countUnderWorkspace(workspaceA.toString())).isEqualTo(3L);
        assertThat(countUnderWorkspace(workspaceB.toString())).isEqualTo(2L);
    }

    @Test
    @DisplayName("RLS : ecriture croisee interdite -- inserer un doc du workspace A depuis le contexte B est rejete (WITH CHECK)")
    void rlsBlocksCrossWorkspaceWrite() {
        // La policy WITH CHECK rejette l'INSERT ; Spring encapsule la PSQLException,
        // le message RLS est donc dans la cause -> on verifie la stack trace complete.
        assertThatThrownBy(() -> insertUnderWorkspace(workspaceB.toString(), workspaceA, dossierA))
                .hasStackTraceContaining("row-level security");

        // Sanity : sous son propre contexte, l'insertion passe (WITH CHECK satisfait).
        insertUnderWorkspace(workspaceA.toString(), workspaceA, dossierA);
        assertThat(countUnderWorkspace(workspaceA.toString())).isEqualTo(4L);
    }

    /**
     * COUNT(*) sur dataroom_documents sous le role NOSUPERUSER NOBYPASSRLS
     * {@code app_no_super}, avec {@code app.current_workspace_id} positionne, pour que
     * la policy {@code dataroom_docs_isolation} s'applique reellement. {@code workspaceValue}
     * vide teste le cas "GUC non positionne -> aucune ligne".
     */
    private Long countUnderWorkspace(String workspaceValue) {
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("GRANT SELECT ON dataroom_documents TO app_no_super");
                st.execute("SET LOCAL ROLE app_no_super");
                st.execute("SET LOCAL app.current_workspace_id = '" + workspaceValue + "'");
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM dataroom_documents")) {
                    rs.next();
                    long n = rs.getLong(1);
                    conn.commit();
                    return n;
                }
            } finally {
                conn.setAutoCommit(prev);
            }
        });
    }

    /**
     * INSERT d'un document sous {@code app_no_super} avec le GUC positionne a
     * {@code gucWorkspace}, en tentant d'ecrire une ligne pour {@code rowWorkspace}.
     * Si les deux different, la clause {@code WITH CHECK} de la policy rejette l'insert.
     */
    private void insertUnderWorkspace(String gucWorkspace, UUID rowWorkspace, UUID dossier) {
        jdbc.execute((java.sql.Connection conn) -> {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var st = conn.createStatement()) {
                st.execute("GRANT SELECT, INSERT ON dataroom_documents TO app_no_super");
                st.execute("SET LOCAL ROLE app_no_super");
                st.execute("SET LOCAL app.current_workspace_id = '" + gucWorkspace + "'");
                try (var ps = conn.prepareStatement("""
                        INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type,
                                title, version, is_current, object_key, filename, size_bytes)
                        VALUES (?, ?, ?, 'STATUTS', 'x-write', 1, true, ?, 'x-write.pdf', 1024)
                        """)) {
                    UUID id = UUID.randomUUID();
                    ps.setObject(1, id);
                    ps.setObject(2, rowWorkspace);
                    ps.setObject(3, dossier);
                    ps.setString(4, "ws/" + rowWorkspace + "/" + id);
                    ps.executeUpdate();
                }
                conn.commit();
                return null;
            } finally {
                conn.setAutoCommit(prev);
            }
        });
    }

    /**
     * 2026-06-16 / V16 — Migration additive de la contrainte CHECK pour
     * autoriser CIN_NOUVELLE, CIN_ANCIENNE, CN.
     * <p>
     * Avant V16, l'INSERT cote DataroomIdentityArchiver echouait avec
     * {@code dataroom_documents_document_type_check} : la liste autorisee
     * issue de V5 ne contenait que les types juridiques historiques.
     * Ce test prouve que les 3 nouveaux types sont desormais acceptes
     * par la base apres V16.
     */
    @Test
    @DisplayName("V16 : les 3 nouveaux document_type identite sont acceptes par la contrainte")
    void identityDocumentTypesAcceptedAfterV16() {
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        try {
            for (String type : new String[] {"CIN_NOUVELLE", "CIN_ANCIENNE", "CN"}) {
                UUID id = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type, title,
                                                    version, is_current, object_key, filename, size_bytes)
                    VALUES (?, ?, ?, ?, ?, 1, true, ?, ?, 1024)
                    """,
                        id, workspaceA, dossierA, type, "Test " + type,
                        "ws/" + workspaceA + "/" + id + "_" + type + ".pdf",
                        "doc_" + type + ".pdf");
            }
            // Verification : on retrouve bien les 3 nouvelles lignes
            // (en plus des 3 STATUTS du setUp).
            Long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dataroom_documents WHERE workspace_id = ?",
                    Long.class, workspaceA);
            assertThat(total).isEqualTo(6L);
            Long identity = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dataroom_documents "
                            + "WHERE workspace_id = ? "
                            + "AND document_type IN ('CIN_NOUVELLE', 'CIN_ANCIENNE', 'CN')",
                    Long.class, workspaceA);
            assertThat(identity).isEqualTo(3L);
        } finally {
            jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        }
    }

    /**
     * 2026-06-16 / V16 — Garde-fou : la contrainte CHECK reste stricte, un
     * type inventé est toujours refuse (pas de regression "tout permis").
     */
    @Test
    @DisplayName("V16 : un document_type hors liste reste rejete par la contrainte")
    void unknownDocumentTypeStillRejected() {
        jdbc.execute("ALTER TABLE dataroom_documents DISABLE ROW LEVEL SECURITY");
        try {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type, title,
                                                    version, is_current, object_key, filename, size_bytes)
                    VALUES (?, ?, ?, 'TYPE_INEXISTANT', ?, 1, true, ?, ?, 1024)
                    """,
                    id, workspaceA, dossierA, "Test", "ws/x/y", "x.pdf"))
                    .hasMessageContaining("dataroom_documents_document_type_check");
        } finally {
            jdbc.execute("ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY");
        }
    }
}

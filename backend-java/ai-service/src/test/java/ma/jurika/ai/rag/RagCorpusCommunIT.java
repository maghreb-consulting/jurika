package ma.jurika.ai.rag;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5 E8 (CDC 3.1, RG-COM-02, backlog "Chatbot (lot L5)") : corpus commun du chatbot.
 *
 * <p>Schema : les VRAIES migrations, dans l'ordre de la base partagee jurika_db (auth, puis
 * ai jusqu'a V15, etat d'avant le lot, puis V16). Sont prouves :
 * <ul>
 *   <li>la reprise de V16 : les sources du super-admin deviennent communes, celles d'un cabinet
 *       sont archivees (jamais lues) ;</li>
 *   <li>le filtrage applicatif (connexion proprietaire, comme ai-service aujourd'hui) : la
 *       question d'un cabinet lit la source commune et jamais un passage propre a un cabinet ;</li>
 *   <li>la RLS en role d'execution {@code jurika_app} : les cabinets A et B lisent la source
 *       commune et rien d'autre ; l'ecriture exige le reglage d'administration pose par
 *       {@link SourceIngestionService}.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagCorpusCommunIT {

    /** Workspace et super-admin semes par auth V2 (le super-admin est rattache a JUR-DEMO1). */
    private static final UUID WS_DEMO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUPER_ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WS_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID EMPLOYE_B = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1");
    private static final UUID DOC_ADMIN = UUID.fromString("cccccccc-0000-0000-0000-0000000000d1");
    private static final UUID DOC_CABINET_B = UUID.fromString("cccccccc-0000-0000-0000-0000000000d2");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("jurika_it_rag_commun")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("rag-commun-it-init.sql");

    private JdbcTemplate owner;

    @BeforeAll
    void demarrer() throws Exception {
        pg.start();
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth", null);
        // Etat d'avant le lot : ai jusqu'a V15, sources rangees par workspace.
        migrer("classpath:db/migration", "flyway_history_ai", "15");

        DriverManagerDataSource ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        owner = new JdbcTemplate(ds);

        // Source deposee par le super-admin, dans SON workspace (celui du cabinet de demonstration).
        owner.update("INSERT INTO rag_corpus_chunks (source, article, chunk_text, workspace_id, uploaded_by, doc_id, chunk_index) "
                        + "VALUES ('Loi 5-96 (source commune)', 'Passage 1', ?, ?, ?, ?, 0)",
                "La cession de parts sociales a des tiers requiert l'agrement de la majorite des associes.",
                WS_DEMO, SUPER_ADMIN, DOC_ADMIN);
        // Source deposee par un employe du cabinet B avant le lot L0 E24.
        owner.update("INSERT INTO rag_corpus_chunks (source, article, chunk_text, workspace_id, uploaded_by, doc_id, chunk_index) "
                        + "VALUES ('Note interne du cabinet B', 'Passage 1', ?, ?, ?, ?, 0)",
                "Cession de parts sociales du client Zorglub : agrement obtenu le mois dernier.",
                WS_B, EMPLOYE_B, DOC_CABINET_B);

        migrer("classpath:db/migration", "flyway_history_ai", null);
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    private void migrer(String location, String table, String cible) {
        var conf = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(location)
                .table(table)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (cible != null) conf = conf.target(cible);
        conf.load().migrate();
    }

    private Connection app() throws SQLException {
        return DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", "jurika_app_it");
    }

    // ---------------------------------------------------------------- reprise V16

    @Test
    void reprise_source_du_super_admin_commune_et_source_de_cabinet_archivee() {
        Map<String, Object> admin = owner.queryForMap(
                "SELECT portee, workspace_id, uploaded_by FROM rag_corpus_chunks WHERE doc_id = ?", DOC_ADMIN);
        assertThat(admin.get("portee")).isEqualTo("COMMUNE");
        assertThat(admin.get("workspace_id")).isNull();
        assertThat(admin.get("uploaded_by")).isEqualTo(SUPER_ADMIN);

        Map<String, Object> cabinet = owner.queryForMap(
                "SELECT portee, workspace_id FROM rag_corpus_chunks WHERE doc_id = ?", DOC_CABINET_B);
        assertThat(cabinet.get("portee")).isEqualTo("ARCHIVEE");
        assertThat(cabinet.get("workspace_id")).isEqualTo(WS_B);
    }

    @Test
    void une_source_commune_ne_peut_pas_porter_de_workspace() {
        assertThatThrownBy(() -> owner.update(
                "INSERT INTO rag_corpus_chunks (source, chunk_text, portee, workspace_id) VALUES ('x', 'y', 'COMMUNE', ?)",
                WS_A)).hasMessageContaining("ck_rag_chunks_commune_sans_workspace");
    }

    // ----------------------------------------------------- filtrage applicatif

    @Test
    void la_question_d_un_cabinet_lit_la_source_commune_et_jamais_celle_d_un_cabinet() {
        CorpusRetriever retriever = new CorpusRetriever(owner);
        List<Map<String, Object>> hits = retriever.searchHybrid("cession de parts sociales agrement", 10);

        assertThat(hits).extracting(h -> h.get("source")).contains("Loi 5-96 (source commune)")
                .doesNotContain("Note interne du cabinet B");
        // Meme le terme propre au cabinet B ne ramene rien.
        assertThat(retriever.searchHybrid("Zorglub", 10)).isEmpty();

        Map<String, Object> reponse = new RagService(retriever, false, 5).ask("Zorglub agrement obtenu");
        assertThat(String.valueOf(reponse.get("reponse"))).doesNotContain("Zorglub");
    }

    // -------------------------------------------------------------- RLS jurika_app

    @Test
    void rls_les_cabinets_a_et_b_lisent_la_source_commune_et_rien_d_autre() throws Exception {
        for (UUID ws : List.of(WS_A, WS_B)) {
            try (Connection c = app(); Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_workspace_id', '" + ws + "', false)");
                var rs = st.executeQuery("SELECT DISTINCT doc_id FROM rag_corpus_chunks WHERE doc_id IS NOT NULL");
                List<UUID> docs = new java.util.ArrayList<>();
                while (rs.next()) docs.add((UUID) rs.getObject(1));
                assertThat(docs).as("cabinet " + ws).contains(DOC_ADMIN).doesNotContain(DOC_CABINET_B);
            }
        }
    }

    @Test
    void rls_un_cabinet_ne_depose_ni_ne_retire_aucune_source() throws Exception {
        try (Connection c = app(); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_workspace_id', '" + WS_B + "', false)");
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO rag_corpus_chunks (source, chunk_text, portee) VALUES ('x', 'y', 'COMMUNE')"))
                    .hasMessageContaining("row-level security");
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO rag_corpus_chunks (source, chunk_text, workspace_id) VALUES ('x', 'y', '" + WS_B + "')"))
                    .hasMessageContaining("row-level security");
            assertThat(st.executeUpdate("DELETE FROM rag_corpus_chunks WHERE doc_id = '" + DOC_ADMIN + "'")).isZero();
            // Aucune politique UPDATE : refus par defaut, meme sous le reglage d'administration.
            st.execute("SELECT set_config('app.rag_sources_admin', 'true', false)");
            assertThat(st.executeUpdate(
                    "UPDATE rag_corpus_chunks SET source = 'pirate' WHERE doc_id = '" + DOC_ADMIN + "'")).isZero();
        }
        assertThat(owner.queryForObject("SELECT source FROM rag_corpus_chunks WHERE doc_id = ?", String.class, DOC_ADMIN))
                .isEqualTo("Loi 5-96 (source commune)");
    }

    @Test
    void rls_l_ingestion_du_super_admin_passe_en_role_d_execution() throws Exception {
        try (Connection c = app()) {
            SingleConnectionDataSource ds = new SingleConnectionDataSource(c, true);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            SourceIngestionService ingestion = new SourceIngestionService(jdbc, new DocumentTextExtractor(), new TextChunker());

            SourceIngestionService.IngestResult r = ingestion.ingestFile(SUPER_ADMIN,
                    "Le gerant est nomme par les associes.".getBytes(StandardCharsets.UTF_8), "gerance.txt", "text/plain");
            assertThat(ingestion.listSources()).extracting(s -> s.get("source")).contains("gerance.txt");
            // Le reglage d'administration etait local a la transaction : il n'a pas fui.
            assertThat(jdbc.queryForObject("SELECT current_setting('app.rag_sources_admin', true)", String.class))
                    .isNotEqualTo("true");
            assertThat(ingestion.deleteSource(r.docId())).isEqualTo(r.chunks());
        }
    }
}

package ma.jurika.ai.rag;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT bout-en-bout du pipeline RAG "sources fiables" sur PostgreSQL reel
 * (pgvector) : ingestion -> ligne creee + search_vector rempli -> retrieval
 * FTS dans le corpus commun -> citation. Couvre aussi la gestion (list / delete).
 * Lot L5 E8 : les sources deposees forment le corpus commun (lu par tous les
 * cabinets) ; l'isolation entre cabinets et la RLS sont prouvees par RagCorpusCommunIT.
 *
 * <p>Nomme {@code *Test} pour etre execute par surefire ({@code mvn test}).
 * {@code disabledWithoutDocker = true} : si Docker est absent, la classe est
 * ignoree proprement (le build reste vert) ; quand Docker tourne, elle prouve
 * le comportement reel.
 */
@Testcontainers(disabledWithoutDocker = true)
class RagCorpusSourcesIngestionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("jurika_it_ai")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("rag-it-init.sql")
            .withReuse(false);

    private static JdbcTemplate jdbc;
    private static SourceIngestionService ingestion;
    private static CorpusRetriever retriever;
    private static RagService rag;

    @BeforeAll
    static void setup() {
        // Schema : on rejoue les migrations Flyway de l'ai-service (V10/V13/V14).
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);

        ingestion = new SourceIngestionService(jdbc, new DocumentTextExtractor(), new TextChunker());
        retriever = new CorpusRetriever(jdbc);
        rag = new RagService(retriever, false, 5);
    }

    @Test
    void ingestionCreatesCommonRowsWithGeneratedSearchVector() {
        UUID user = UUID.randomUUID();
        byte[] bytes = ("Le capital social de la SARL doit etre libere a hauteur de 25% minimum "
                + "a la souscription, le solde dans un delai de cinq ans.").getBytes(StandardCharsets.UTF_8);

        SourceIngestionService.IngestResult result =
                ingestion.ingestFile(user, bytes, "regle-capital.txt", "text/plain");

        assertThat(result.chunks()).isGreaterThanOrEqualTo(1);

        // Lot L5 E8 : portee commune, sans workspace, auteur conserve.
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE doc_id = ? AND portee = 'COMMUNE' "
                        + "AND workspace_id IS NULL AND uploaded_by = ?",
                Integer.class, result.docId(), user);
        assertThat(rows).isEqualTo(result.chunks());

        // search_vector (colonne GENERATED) est bien rempli pour chaque ligne.
        Integer nullVectors = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE doc_id = ? AND search_vector IS NULL",
                Integer.class, result.docId());
        assertThat(nullVectors).isZero();
    }

    @Test
    void retrievalFindsCommonPassageButNeverAnArchivedOne() {
        ingestion.ingestText(UUID.randomUUID(), "Dissolution",
                "La dissolution de la societe est decidee en assemblee generale extraordinaire "
                        + "puis suivie d'une phase de liquidation menee par un liquidateur designe.");
        // Passage depose par un cabinet avant le lot L0 E24 : archive, jamais lu.
        jdbc.update("INSERT INTO rag_corpus_chunks (source, article, chunk_text, workspace_id, doc_id, chunk_index) "
                        + "VALUES ('Note du cabinet B', 'Passage 1', ?, ?, ?, 0)",
                "Le liquidateur Zorglub du cabinet B suit la liquidation interne.",
                UUID.randomUUID(), UUID.randomUUID());

        List<Map<String, Object>> hits = retriever.search("liquidation liquidateur", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(h -> h.get("source")).contains("Dissolution")
                .doesNotContain("Note du cabinet B");
        assertThat(retriever.search("Zorglub", 5)).isEmpty();
    }

    @Test
    void ragAskCitesIngestedSource() {
        ingestion.ingestText(UUID.randomUUID(), "Succursale etrangere",
                "La succursale d'une societe etrangere requiert des statuts apostilles "
                        + "et l'inscription au registre du commerce du lieu de la succursale.");

        Map<String, Object> answer = rag.ask("succursale apostille registre");

        assertThat(answer.get("ragMode")).isEqualTo("extraits");
        assertThat(answer.get("reponse")).asString().contains("statuts apostilles");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) answer.get("sources");
        assertThat(sources).extracting(s -> s.get("reference")).contains("Succursale etrangere");
        assertThat((Integer) answer.get("chunksRetrieved")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void askWithoutRelevantSourceSaysSo() {
        Map<String, Object> answer = rag.ask("Quelle est la procedure de nantissement du fonds xylophone ?");
        assertThat(answer.get("reponse")).isEqualTo(RagService.REPONSE_SANS_SOURCE);
        assertThat((List<?>) answer.get("sources")).isEmpty();
        assertThat((Integer) answer.get("chunksRetrieved")).isZero();
    }

    /**
     * Voie vectorielle bout-en-bout AVEC embedding mocke (aucun appel reseau) :
     * l'ingestion remplit la colonne {@code embedding} (VECTOR(768) depuis V15) puis
     * {@link CorpusRetriever#searchByVector} et {@link CorpusRetriever#searchHybrid}
     * retrouvent le chunk via cosine, dans le corpus commun.
     */
    @Test
    void vectorIngestionAndSearchWithMockedEmbeddingClient() {
        RagProperties props = new RagProperties(true, 5,
                new RagProperties.Embed("gemini", "http://unused/v1", "TEST-KEY",
                        "text-embedding-004", 768, 30),
                new RagProperties.Chat(null, null, null, null, -1.0, 0));
        // Stub : renvoie un vecteur 768-d constant, sans jamais toucher le reseau.
        RagEmbeddingClient stub = new RagEmbeddingClient(props, new RestTemplate()) {
            @Override public boolean isReady() { return true; }
            @Override public List<float[]> embedBatch(List<String> texts) {
                return texts.stream().map(t -> fixedVector()).toList();
            }
            @Override public Optional<float[]> embed(String text) {
                return Optional.of(fixedVector());
            }
        };

        SourceIngestionService vecIngestion =
                new SourceIngestionService(jdbc, new DocumentTextExtractor(), new TextChunker(), stub);
        CorpusRetriever vecRetriever = new CorpusRetriever(jdbc, stub);

        vecIngestion.ingestText(UUID.randomUUID(), "Fusion",
                "La fusion-absorption de deux societes anonymes entraine transmission universelle du patrimoine.");

        // L'embedding est bien persiste (colonne non NULL).
        Integer withVector = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE source = 'Fusion' AND embedding IS NOT NULL",
                Integer.class);
        assertThat(withVector).isGreaterThanOrEqualTo(1);

        // Recherche vectorielle : retrouve le passage du corpus commun.
        List<Map<String, Object>> hits = vecRetriever.searchByVector("fusion societes", 5);
        assertThat(hits).extracting(h -> h.get("source")).contains("Fusion");

        // Hybride : renvoie aussi le chunk (voie vecteur disponible).
        assertThat(vecRetriever.searchHybrid("fusion societes", 5)).isNotEmpty();
    }

    /** Vecteur 768-d deterministe et normalise, pour un cosine stable en test. */
    private static float[] fixedVector() {
        float[] v = new float[768];
        for (int i = 0; i < v.length; i++) {
            v[i] = (i % 7) + 1; // valeurs non nulles, memes pour chunk et requete -> cosine = 1
        }
        return v;
    }

    @Test
    void listAndDeleteCommonSources() {
        SourceIngestionService.IngestResult a =
                ingestion.ingestText(UUID.randomUUID(), "Doc A", "Texte de la source A sur la SARL.");
        UUID archive = UUID.randomUUID();
        jdbc.update("INSERT INTO rag_corpus_chunks (source, article, chunk_text, workspace_id, doc_id, chunk_index) "
                + "VALUES ('Archive', 'Passage 1', 'Texte archive', ?, ?, 0)", UUID.randomUUID(), archive);

        assertThat(ingestion.listSources()).extracting(s -> s.get("source")).contains("Doc A")
                .doesNotContain("Archive");

        // Un passage archive n'est jamais retire par la gestion des sources communes.
        assertThat(ingestion.deleteSource(archive)).isZero();

        assertThat(ingestion.deleteSource(a.docId())).isEqualTo(a.chunks());
        assertThat(ingestion.listSources()).extracting(s -> s.get("source")).doesNotContain("Doc A");
    }
}

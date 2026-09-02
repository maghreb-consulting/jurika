package ma.jurika.ai.rag;

import ma.jurika.ai.infrastructure.ChatbotKnowledgeBase;
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
 * FTS scope au workspace -> citation. Couvre aussi l'isolation cross-workspace
 * et la gestion (list / delete).
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
        rag = new RagService(new ChatbotKnowledgeBase(), retriever, false, 5);
    }

    @Test
    void ingestionCreatesRowsWithGeneratedSearchVector() {
        UUID ws = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        byte[] bytes = ("Le capital social de la SARL doit etre libere a hauteur de 25% minimum "
                + "a la souscription, le solde dans un delai de cinq ans.").getBytes(StandardCharsets.UTF_8);

        SourceIngestionService.IngestResult result =
                ingestion.ingestFile(ws, user, bytes, "regle-capital.txt", "text/plain");

        assertThat(result.chunks()).isGreaterThanOrEqualTo(1);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE workspace_id = ? AND doc_id = ?",
                Integer.class, ws, result.docId());
        assertThat(rows).isEqualTo(result.chunks());

        // search_vector (colonne GENERATED) est bien rempli pour chaque ligne.
        Integer nullVectors = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE doc_id = ? AND search_vector IS NULL",
                Integer.class, result.docId());
        assertThat(nullVectors).isZero();
    }

    @Test
    void retrievalFindsIngestedPassageScopedToWorkspace() {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ingestion.ingestText(wsA, user, "Dissolution",
                "La dissolution de la societe est decidee en assemblee generale extraordinaire "
                        + "puis suivie d'une phase de liquidation menee par un liquidateur designe.");

        // Workspace A retrouve son passage...
        List<Map<String, Object>> hitsA = retriever.search("liquidation liquidateur", 5, wsA);
        assertThat(hitsA).isNotEmpty();
        assertThat(String.valueOf(hitsA.get(0).get("chunk_text"))).contains("liquidateur");

        // ...mais le workspace B (isolation) ne voit rien.
        List<Map<String, Object>> hitsB = retriever.search("liquidation liquidateur", 5, wsB);
        assertThat(hitsB).isEmpty();

        // workspaceId null => aucune source (pas de fuite globale).
        assertThat(retriever.search("liquidation", 5, null)).isEmpty();
    }

    @Test
    void ragAskCitesIngestedSourceAndKeepsKbFallback() {
        UUID ws = UUID.randomUUID();
        ingestion.ingestText(ws, UUID.randomUUID(), "Succursale etrangere",
                "La succursale d'une societe etrangere requiert des statuts apostilles "
                        + "et l'inscription au registre du commerce du lieu de la succursale.");

        Map<String, Object> answer = rag.ask("succursale apostille registre", ws);

        assertThat(answer.get("reponse")).asString().isNotBlank(); // KB toujours presente
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) answer.get("sources");
        boolean citesUpload = sources.stream()
                .anyMatch(s -> "Succursale etrangere".equals(s.get("reference")));
        assertThat(citesUpload).isTrue();
        assertThat((Integer) answer.get("chunksRetrieved")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void askWithoutAnySourceStillAnswersFromKb() {
        UUID ws = UUID.randomUUID(); // workspace vierge, aucune source
        Map<String, Object> answer = rag.ask("Comment creer une SARL au Maroc ?", ws);
        assertThat(answer.get("reponse")).asString().contains("SARL");
        assertThat((Integer) answer.get("chunksRetrieved")).isZero();
    }

    /**
     * Voie vectorielle bout-en-bout AVEC embedding mocke (aucun appel reseau) :
     * l'ingestion remplit la colonne {@code embedding} (VECTOR(768) depuis V15) puis
     * {@link CorpusRetriever#searchByVector} et {@link CorpusRetriever#searchHybrid}
     * retrouvent le chunk via cosine, toujours scopes au workspace.
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

        UUID ws = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        vecIngestion.ingestText(ws, UUID.randomUUID(), "Fusion",
                "La fusion-absorption de deux societes anonymes entraine transmission universelle du patrimoine.");

        // L'embedding est bien persiste (colonne non NULL).
        Integer withVector = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_corpus_chunks WHERE workspace_id = ? AND embedding IS NOT NULL",
                Integer.class, ws);
        assertThat(withVector).isGreaterThanOrEqualTo(1);

        // Recherche vectorielle : retrouve le passage, scope au workspace.
        List<Map<String, Object>> hits = vecRetriever.searchByVector("fusion societes", 5, ws);
        assertThat(hits).isNotEmpty();
        assertThat(String.valueOf(hits.get(0).get("chunk_text"))).contains("fusion");

        // Isolation multi-tenant + workspace null.
        assertThat(vecRetriever.searchByVector("fusion societes", 5, other)).isEmpty();
        assertThat(vecRetriever.searchByVector("fusion", 5, null)).isEmpty();

        // Hybride : renvoie aussi le chunk (voie vecteur disponible).
        assertThat(vecRetriever.searchHybrid("fusion societes", 5, ws)).isNotEmpty();
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
    void listAndDeleteAreWorkspaceScoped() {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();
        SourceIngestionService.IngestResult a =
                ingestion.ingestText(wsA, UUID.randomUUID(), "Doc A", "Texte de la source A sur la SARL.");

        assertThat(ingestion.listSources(wsA)).hasSize(1);
        assertThat(ingestion.listSources(wsB)).isEmpty();

        // Un autre workspace ne peut pas supprimer la source de A.
        assertThat(ingestion.deleteSource(wsB, a.docId())).isZero();
        assertThat(ingestion.listSources(wsA)).hasSize(1);

        // Le proprietaire supprime bien sa source.
        assertThat(ingestion.deleteSource(wsA, a.docId())).isEqualTo(a.chunks());
        assertThat(ingestion.listSources(wsA)).isEmpty();
    }
}

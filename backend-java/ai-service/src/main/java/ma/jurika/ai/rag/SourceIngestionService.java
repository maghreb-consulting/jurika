package ma.jurika.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ingestion de documents sources fiables dans le corpus RAG ({@code rag_corpus_chunks}).
 *
 * <p>Pipeline : extraction texte (PDF/DOCX/TXT) -> chunking -> insertion dans le
 * <b>corpus commun</b> (lot L5 E8, CDC 3.1 et RG-COM-02) : une source deposee par le
 * super-admin est lue par tous les cabinets ({@code portee = 'COMMUNE'}, sans
 * workspace). Aucun cabinet ne depose de source (garde du controleur).
 *
 * <p>L'embedding vectoriel reste optionnel (rempli seulement si {@code jurika.rag.enabled} et un provider d'embedding est cable) ; le
 * Full-Text Search via {@code search_vector} (colonne generee) fonctionne sans
 * aucune cle LLM externe.
 *
 * <p>Ajout et retrait passent dans une transaction qui pose
 * {@code app.rag_sources_admin = 'true'} (local a la transaction) : c'est la
 * condition des politiques RLS d'ecriture de V16 pour le role jurika_app.
 */
@Service
public class SourceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SourceIngestionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Garde-fou taille (caracteres extraits) pour eviter une ingestion abusive. */
    private static final int MAX_EXTRACTED_CHARS = 2_000_000;

    private final JdbcTemplate jdbc;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    /** Optionnel : null quand la voie vectorielle n'est pas câblée (tests FTS, contexte hors Spring). */
    private final RagEmbeddingClient embeddingClient;

    @Autowired
    public SourceIngestionService(JdbcTemplate jdbc,
                                  DocumentTextExtractor extractor,
                                  TextChunker chunker,
                                  RagEmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
    }

    /** Constructeur FTS-only (sans embeddings) — utilisé par les tests et le repli sans clé. */
    public SourceIngestionService(JdbcTemplate jdbc,
                                  DocumentTextExtractor extractor,
                                  TextChunker chunker) {
        this(jdbc, extractor, chunker, null);
    }

    /** Resultat d'une ingestion : identifiant du document + nombre de chunks crees. */
    public record IngestResult(UUID docId, String source, int chunks) {}

    /** Ingestion d'un fichier binaire (pdf/docx/txt). */
    public IngestResult ingestFile(UUID uploadedBy,
                                   byte[] bytes, String filename, String contentType) {
        String text = extractor.extract(bytes, filename, contentType);
        String source = (filename == null || filename.isBlank()) ? "document" : filename;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", filename);
        meta.put("contentType", contentType);
        meta.put("kind", extractor.detect(filename, contentType).name());
        return persist(uploadedBy, source, text, meta);
    }

    /** Ingestion d'un texte colle (sans fichier). */
    public IngestResult ingestText(UUID uploadedBy, String title, String text) {
        String source = (title == null || title.isBlank()) ? "Note libre" : title;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "TEXT");
        return persist(uploadedBy, source, text, meta);
    }

    private IngestResult persist(UUID uploadedBy,
                                 String source, String text, Map<String, Object> meta) {
        if (uploadedBy == null) throw new IllegalArgumentException("auteur du depot requis");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Aucun texte exploitable dans le document fourni.");
        }
        if (text.length() > MAX_EXTRACTED_CHARS) {
            text = text.substring(0, MAX_EXTRACTED_CHARS);
        }

        List<String> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Le document ne contient aucun passage indexable.");
        }

        UUID docId = UUID.randomUUID();
        String metaJson = writeJson(meta);

        // Embeddings (optionnels) : calcules en un seul appel batch si la voie
        // vectorielle est prete (rag.enabled + cle). Sinon liste vide -> NULL,
        // le Full-Text Search continue de fonctionner sans aucun appel reseau.
        List<String> vectorLiterals = computeVectorLiterals(chunks);

        // On insere le vecteur via ?::vector ("[v1,v2,...]") ou NULL::vector.
        String sql = """
                INSERT INTO rag_corpus_chunks
                    (source, article, chunk_text, embedding, metadata,
                     portee, workspace_id, uploaded_by, doc_id, chunk_index)
                VALUES (?, ?, ?, ?::vector, ?::jsonb, 'COMMUNE', NULL, ?, ?, ?)
                """;

        int embedded = (int) vectorLiterals.stream().filter(v -> v != null).count();
        enModeAdministration(() -> {
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.update(sql,
                        source, "Passage " + (i + 1), chunks.get(i), vectorLiterals.get(i), metaJson,
                        uploadedBy, docId, i);
            }
            return null;
        });

        log.info("RAG source commune ingeree : doc={} source='{}' chunks={} embeddings={}",
                docId, source, chunks.size(), embedded);
        return new IngestResult(docId, source, chunks.size());
    }

    /**
     * Calcule les litteraux pgvector pour chaque chunk. Renvoie une liste alignee
     * sur {@code chunks} contenant soit {@code "[...]"} soit {@code null} (=> NULL SQL).
     * Ne fait aucun appel reseau si la voie vectorielle est desactivee.
     */
    private List<String> computeVectorLiterals(List<String> chunks) {
        List<String> literals = new java.util.ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) literals.add(null);

        if (embeddingClient == null || !embeddingClient.isReady()) {
            return literals; // FTS-only : embeddings NULL.
        }

        List<float[]> vectors = embeddingClient.embedBatch(chunks);
        // embedBatch renvoie une liste vide en cas d'erreur (jamais throw) ->
        // on retombe proprement sur des embeddings NULL.
        if (vectors.size() != chunks.size()) {
            if (!vectors.isEmpty()) {
                log.warn("Embeddings : {} vecteurs pour {} chunks — ingestion en FTS seul",
                        vectors.size(), chunks.size());
            }
            return literals;
        }
        for (int i = 0; i < vectors.size(); i++) {
            literals.set(i, RagEmbeddingClient.toVectorLiteral(vectors.get(i)));
        }
        return literals;
    }

    /** Liste les sources communes (documents), regroupees par doc_id. */
    public List<Map<String, Object>> listSources() {
        String sql = """
                SELECT doc_id,
                       MIN(source)               AS source,
                       COUNT(*)                  AS chunks,
                       MIN(uploaded_by::text)    AS uploaded_by,
                       MIN(created_at)           AS created_at
                FROM rag_corpus_chunks
                WHERE portee = 'COMMUNE' AND doc_id IS NOT NULL
                GROUP BY doc_id
                ORDER BY MIN(created_at) DESC
                """;
        return jdbc.queryForList(sql);
    }

    /** Retire une source commune (tous ses passages). Un passage archive n'est jamais touche. */
    public int deleteSource(UUID docId) {
        if (docId == null) return 0;
        Integer n = enModeAdministration(() -> jdbc.update(
                "DELETE FROM rag_corpus_chunks WHERE portee = 'COMMUNE' AND doc_id = ?", docId));
        return n == null ? 0 : n;
    }

    /**
     * Execute l'ecriture dans une transaction qui pose {@code app.rag_sources_admin}
     * (reglage local : il disparait au commit et ne fuit pas dans le pool).
     */
    private <T> T enModeAdministration(java.util.function.Supplier<T> ecriture) {
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.rag_sources_admin', 'true', true)", String.class);
            return ecriture.get();
        });
    }

    private String writeJson(Map<String, Object> meta) {
        try {
            return JSON.writeValueAsString(meta);
        } catch (Exception ex) {
            return "{}";
        }
    }
}

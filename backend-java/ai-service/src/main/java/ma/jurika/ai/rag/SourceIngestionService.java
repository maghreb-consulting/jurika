package ma.jurika.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ingestion de documents sources fiables dans le corpus RAG ({@code rag_corpus_chunks}).
 *
 * <p>Pipeline : extraction texte (PDF/DOCX/TXT) -> chunking -> insertion scopee
 * au workspace courant. L'embedding vectoriel reste optionnel (rempli seulement
 * si {@code jurika.rag.enabled} et un provider d'embedding est cable) ; le
 * Full-Text Search via {@code search_vector} (colonne generee) fonctionne sans
 * aucune cle LLM externe.
 *
 * <p>Les methodes prennent {@code workspaceId} / {@code uploadedBy} en parametres
 * explicites (pas de lecture du SecurityContext) afin de rester testables hors
 * conteneur Spring et de garantir le scoping multi-tenant a chaque appel.
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
    public IngestResult ingestFile(UUID workspaceId, UUID uploadedBy,
                                   byte[] bytes, String filename, String contentType) {
        String text = extractor.extract(bytes, filename, contentType);
        String source = (filename == null || filename.isBlank()) ? "document" : filename;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", filename);
        meta.put("contentType", contentType);
        meta.put("kind", extractor.detect(filename, contentType).name());
        return persist(workspaceId, uploadedBy, source, text, meta);
    }

    /** Ingestion d'un texte colle (sans fichier). */
    public IngestResult ingestText(UUID workspaceId, UUID uploadedBy, String title, String text) {
        String source = (title == null || title.isBlank()) ? "Note libre" : title;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "TEXT");
        return persist(workspaceId, uploadedBy, source, text, meta);
    }

    private IngestResult persist(UUID workspaceId, UUID uploadedBy,
                                 String source, String text, Map<String, Object> meta) {
        if (workspaceId == null) throw new IllegalArgumentException("workspaceId requis");
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
                     workspace_id, uploaded_by, doc_id, chunk_index)
                VALUES (?, ?, ?, ?::vector, ?::jsonb, ?, ?, ?, ?)
                """;

        int embedded = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String article = "Passage " + (i + 1);
            String vectorLiteral = vectorLiterals.get(i);
            if (vectorLiteral != null) embedded++;
            jdbc.update(sql,
                    source, article, chunkText, vectorLiteral, metaJson,
                    workspaceId, uploadedBy, docId, i);
        }

        log.info("RAG source ingeree : workspace={} doc={} source='{}' chunks={} embeddings={}",
                workspaceId, docId, source, chunks.size(), embedded);
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

    /** Liste les sources (documents) du workspace, regroupees par doc_id. */
    public List<Map<String, Object>> listSources(UUID workspaceId) {
        if (workspaceId == null) return List.of();
        String sql = """
                SELECT doc_id,
                       MIN(source)               AS source,
                       COUNT(*)                  AS chunks,
                       MIN(uploaded_by::text)    AS uploaded_by,
                       MIN(created_at)           AS created_at
                FROM rag_corpus_chunks
                WHERE workspace_id = ? AND doc_id IS NOT NULL
                GROUP BY doc_id
                ORDER BY MIN(created_at) DESC
                """;
        return jdbc.queryForList(sql, workspaceId);
    }

    /** Supprime un document (tous ses chunks) du workspace. Scope tenant = isolation. */
    public int deleteSource(UUID workspaceId, UUID docId) {
        if (workspaceId == null || docId == null) return 0;
        return jdbc.update(
                "DELETE FROM rag_corpus_chunks WHERE workspace_id = ? AND doc_id = ?",
                workspaceId, docId);
    }

    private String writeJson(Map<String, Object> meta) {
        try {
            return JSON.writeValueAsString(meta);
        } catch (Exception ex) {
            return "{}";
        }
    }
}

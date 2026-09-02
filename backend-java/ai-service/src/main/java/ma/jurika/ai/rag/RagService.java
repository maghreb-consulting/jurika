package ma.jurika.ai.rag;

import ma.jurika.ai.infrastructure.ChatbotKnowledgeBase;
import ma.jurika.common.audit.Auditable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service RAG (Retrieval-Augmented Generation) pour le chatbot juridique.
 *
 * <p>Strategie en modes cumulatifs :
 * <ol>
 *     <li><b>KB local</b> : interroge {@link ChatbotKnowledgeBase} (19 entrees hardcodees) — synthese.</li>
 *     <li><b>Retrieval FTS / vectoriel</b> : {@link CorpusRetriever#searchHybrid} sur
 *         {@code rag_corpus_chunks}, strictement scope au workspace (isolation multi-tenant).</li>
 *     <li><b>Generation LLM ancree</b> (si {@code jurika.rag.enabled=true} + cle chat + chunks
 *         recuperes) : {@link RagChatClient} produit une reponse d'assistant fondee UNIQUEMENT
 *         sur le contexte recupere, avec repli automatique KB+FTS a la moindre indisponibilite.</li>
 * </ol>
 */
@Service
public class RagService {

    private final ChatbotKnowledgeBase kb;
    private final CorpusRetriever retriever;
    /** Optionnel : null quand la voie chat n'est pas câblée (tests, repli sans clé). */
    private final RagChatClient chatClient;
    private final boolean ragLlmEnabled;
    private final int retrieveTopK;

    /** Nombre max de chunks injectes dans le prompt (borne la taille du contexte). */
    private static final int MAX_CONTEXT_CHUNKS = 6;
    private static final String SYSTEM_PROMPT =
            "Tu es l'assistant juridique de JURIKA (droit des societes au Maroc). "
            + "Reponds en francais, de maniere claire et concise, UNIQUEMENT a partir du CONTEXTE fourni. "
            + "Si l'information ne figure pas dans le contexte, dis-le explicitement et invite a contacter "
            + "un expert du cabinet — n'invente jamais. Cite les sources utilisees (reference + article).";

    @Autowired
    public RagService(ChatbotKnowledgeBase kb,
                      CorpusRetriever retriever,
                      RagChatClient chatClient,
                      @Value("${jurika.rag.enabled:false}") boolean ragLlmEnabled,
                      @Value("${jurika.rag.top-k:5}") int retrieveTopK) {
        this.kb = kb;
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.ragLlmEnabled = ragLlmEnabled;
        this.retrieveTopK = retrieveTopK;
    }

    /** Constructeur KB+FTS-only (sans generation LLM) — utilise par les tests et le repli sans cle. */
    public RagService(ChatbotKnowledgeBase kb,
                      CorpusRetriever retriever,
                      boolean ragLlmEnabled,
                      int retrieveTopK) {
        this(kb, retriever, null, ragLlmEnabled, retrieveTopK);
    }

    @Auditable(action = "CHATBOT_QUERY")
    public Map<String, Object> ask(String question, UUID workspaceId) {
        Map<String, Object> response = new HashMap<>();

        // 1. KB matching (synthese) — toujours disponible, meme sans aucune source.
        ChatbotKnowledgeBase.Answer kbAnswer = kb.ask(question);

        // 2. Retrieval depuis le corpus du workspace (sources uploadees).
        //    Hybride : vecteur (cosine) si la voie embeddings est prete, sinon FTS.
        List<Map<String, Object>> chunks = retriever.searchHybrid(question, retrieveTopK, workspaceId);
        List<Map<String, Object>> chunkCitations = retriever.asCitations(chunks);

        // 3. Generation LLM ancree si possible ; sinon repli KB+FTS (aucun appel reseau).
        boolean llmAnswered = false;
        if (ragLlmEnabled && chatClient != null && chatClient.isReady() && !chunks.isEmpty()) {
            Optional<String> generated = chatClient.complete(SYSTEM_PROMPT, buildUserPrompt(question, chunks));
            if (generated.isPresent()) {
                response.put("reponse", generated.get());
                response.put("confidence", retrievalConfidence(chunks));
                // Les citations = strictement les chunks recuperes (contexte de la reponse).
                response.put("sources", new ArrayList<>(chunkCitations));
                response.put("ragMode", "llm");
                llmAnswered = true;
            }
        }

        // 4. Repli KB+FTS (comportement historique) si le LLM n'a pas repondu.
        if (!llmAnswered) {
            response.put("reponse", kbAnswer.reponse());
            response.put("confidence", kbAnswer.confidence());
            List<Map<String, Object>> citations = new ArrayList<>(chunkCitations);
            for (ChatbotKnowledgeBase.Source s : kbAnswer.sources()) {
                Map<String, Object> citation = new HashMap<>();
                citation.put("reference", s.reference());
                citation.put("article", s.article());
                citation.put("extract", s.topic());
                citation.put("rank", null);
                citations.add(citation);
            }
            response.put("sources", citations);
            response.put("ragMode", "fts");
        }

        response.put("chunksRetrieved", chunks.size());

        if (!llmAnswered && chunks.isEmpty() && kbAnswer.confidence() < 0.3) {
            response.put("note",
                    "Aucune correspondance trouvee dans la base juridique. Reformulez votre question "
                            + "ou contactez un expert juridique du cabinet.");
        }

        return response;
    }

    /**
     * Construit le message utilisateur : CONTEXTE (chunks avec leur reference/article) + QUESTION.
     * Le contexte ne contient que les chunks du workspace courant -> scope tenant preserve.
     */
    private String buildUserPrompt(String question, List<Map<String, Object>> chunks) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("CONTEXTE (passages du corpus juridique du cabinet) :\n\n");
        int n = Math.min(chunks.size(), MAX_CONTEXT_CHUNKS);
        for (int i = 0; i < n; i++) {
            Map<String, Object> c = chunks.get(i);
            String ref = String.valueOf(c.getOrDefault("source", "Source"));
            Object article = c.get("article");
            sb.append('[').append(i + 1).append("] ").append(ref);
            if (article != null) sb.append(" — ").append(article);
            sb.append('\n').append(String.valueOf(c.getOrDefault("chunk_text", ""))).append("\n\n");
        }
        sb.append("QUESTION :\n").append(question).append('\n');
        return sb.toString();
    }

    /**
     * Confiance derivee du score de retrieval : on prend le meilleur {@code rank} disponible
     * (vecteur : 1 - distance cosine ; FTS : ts_rank) puis on borne dans [0.6, 0.95] — plancher
     * justifie car la reponse LLM est ancree sur des chunks reellement recuperes.
     */
    private double retrievalConfidence(List<Map<String, Object>> chunks) {
        double topRank = chunks.stream()
                .map(c -> c.get("rank"))
                .filter(r -> r instanceof Number)
                .mapToDouble(r -> ((Number) r).doubleValue())
                .max()
                .orElse(0.0);
        double base = topRank > 0.0 ? topRank : 0.7;
        return Math.max(0.6, Math.min(0.95, base));
    }
}

package ma.jurika.ai.rag;

import ma.jurika.common.audit.Auditable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service RAG (Retrieval-Augmented Generation) du chatbot juridique.
 *
 * <p>RG-COM-02 (lot L5 E8) : le chatbot repond <b>uniquement</b> a partir des sources
 * fiables deposees par le super-admin (corpus commun), et cite ses sources. Trois cas :
 * <ol>
 *     <li><b>aucun passage pertinent</b> : la reponse le dit, sans source, sans appel au
 *         modele ({@code ragMode = "aucune-source"}) ;</li>
 *     <li><b>passages + generation disponible</b> ({@code jurika.rag.enabled=true} + cle) :
 *         {@link RagChatClient} redige une reponse fondee sur ces seuls passages
 *         ({@code ragMode = "llm"}) ;</li>
 *     <li><b>passages sans generation</b> (drapeau coupe, cle absente, fournisseur en
 *         panne) : la reponse restitue les passages eux-memes, cites
 *         ({@code ragMode = "extraits"}).</li>
 * </ol>
 * L'ancienne base de connaissances codee en dur (ChatbotKnowledgeBase, 19 fiches non
 * deposees par le super-admin, citees comme sources) est retiree : elle repondait meme
 * sans aucune source.
 *
 * <p>La question arrive deja filtree (RG-COM-04, {@link FiltreDonneesPersonnelles}) ; le
 * filtre est rejoue ici, dernier point avant l'envoi au modele.
 */
@Service
public class RagService {

    /** Reponse donnee quand les sources ne contiennent rien sur la question (RG-COM-02). */
    public static final String REPONSE_SANS_SOURCE =
            "Les sources fiables déposées sur la plateforme ne contiennent pas de réponse à cette "
                    + "question. Reformulez-la avec d\u2019autres termes, ou interrogez un juriste du cabinet.";

    private static final String INTRO_EXTRAITS =
            "Voici les passages des sources fiables qui se rapportent à votre question :";

    private final CorpusRetriever retriever;
    /** Optionnel : null quand la voie chat n'est pas câblée (tests, repli sans clé). */
    private final RagChatClient chatClient;
    private final boolean ragLlmEnabled;
    private final int retrieveTopK;

    /** Nombre max de chunks injectes dans le prompt (borne la taille du contexte). */
    private static final int MAX_CONTEXT_CHUNKS = 6;
    /** Longueur maximale d'un passage restitue sans generation. */
    private static final int MAX_EXTRAIT = 600;
    private static final String SYSTEM_PROMPT =
            "Tu es l\u2019assistant juridique de JURIKA (droit des soci\u00e9t\u00e9s au Maroc). "
            + "R\u00e9ponds en fran\u00e7ais, de mani\u00e8re claire et concise, UNIQUEMENT \u00e0 partir du CONTEXTE fourni. "
            + "Si l\u2019information ne figure pas dans le contexte, dis-le explicitement et invite \u00e0 interroger "
            + "un juriste du cabinet \u2014 n\u2019invente jamais. Cite les sources utilis\u00e9es (r\u00e9f\u00e9rence + article).";

    @Autowired
    public RagService(CorpusRetriever retriever,
                      RagChatClient chatClient,
                      @Value("${jurika.rag.enabled:false}") boolean ragLlmEnabled,
                      @Value("${jurika.rag.top-k:5}") int retrieveTopK) {
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.ragLlmEnabled = ragLlmEnabled;
        this.retrieveTopK = retrieveTopK;
    }

    /** Constructeur sans generation (tests, repli sans cle). */
    public RagService(CorpusRetriever retriever, boolean ragLlmEnabled, int retrieveTopK) {
        this(retriever, null, ragLlmEnabled, retrieveTopK);
    }

    @Auditable(action = "CHATBOT_QUERY")
    public Map<String, Object> ask(String question) {
        // RG-COM-04 : dernier controle avant le modele (le controleur a deja refuse en 422).
        List<FiltreDonneesPersonnelles.Nature> natures = FiltreDonneesPersonnelles.detecter(question);
        if (!natures.isEmpty()) {
            throw new DonneePersonnelleDetecteeException(natures);
        }

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> chunks = retriever.searchHybrid(question, retrieveTopK);
        response.put("chunksRetrieved", chunks.size());

        if (chunks.isEmpty()) {
            response.put("reponse", REPONSE_SANS_SOURCE);
            response.put("confidence", 0.0);
            response.put("sources", new ArrayList<>());
            response.put("ragMode", "aucune-source");
            return response;
        }

        List<Map<String, Object>> citations = retriever.asCitations(chunks);
        response.put("sources", new ArrayList<>(citations));
        response.put("confidence", retrievalConfidence(chunks));

        if (ragLlmEnabled && chatClient != null && chatClient.isReady()) {
            Optional<String> generated = chatClient.complete(SYSTEM_PROMPT, buildUserPrompt(question, chunks));
            if (generated.isPresent()) {
                response.put("reponse", generated.get());
                response.put("ragMode", "llm");
                return response;
            }
        }

        response.put("reponse", extraits(chunks));
        response.put("ragMode", "extraits");
        return response;
    }

    /** Reponse sans generation : les passages eux-memes, numerotes comme leurs citations. */
    private String extraits(List<Map<String, Object>> chunks) {
        StringBuilder sb = new StringBuilder(INTRO_EXTRAITS).append("\n\n");
        int n = Math.min(chunks.size(), MAX_CONTEXT_CHUNKS);
        for (int i = 0; i < n; i++) {
            Map<String, Object> c = chunks.get(i);
            String texte = String.valueOf(c.getOrDefault("chunk_text", "")).strip();
            if (texte.length() > MAX_EXTRAIT) texte = texte.substring(0, MAX_EXTRAIT - 1) + "…";
            sb.append(i + 1).append(". **").append(String.valueOf(c.getOrDefault("source", "Source")));
            Object article = c.get("article");
            if (article != null) sb.append(" — ").append(article);
            sb.append("** : ").append(texte).append("\n\n");
        }
        return sb.toString().strip();
    }

    /**
     * Construit le message utilisateur : CONTEXTE (chunks avec leur reference/article) + QUESTION.
     * Le contexte ne contient que des passages du corpus commun.
     */
    private String buildUserPrompt(String question, List<Map<String, Object>> chunks) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("CONTEXTE (passages des sources fiables de la plateforme) :\n\n");
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
     * justifie car la reponse est ancree sur des passages reellement recuperes.
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

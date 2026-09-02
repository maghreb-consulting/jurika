package ma.jurika.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Propriétés de configuration RAG (prefix {@code jurika.rag}).
 *
 * <p>Coexiste avec les {@code @Value("${jurika.rag.enabled}")} historiques de
 * {@link RagService} ; cette record centralise en plus le bloc embeddings
 * ({@code jurika.rag.embed.*}) requis par la recherche vectorielle.
 *
 * <p>Toutes les valeurs sont surchargées par env vars :
 * {@code RAG_EMBED_PROVIDER}, {@code RAG_EMBED_BASE_URL}, {@code RAG_EMBED_API_KEY},
 * {@code RAG_EMBED_MODEL}, {@code RAG_EMBED_DIMENSIONS}, {@code RAG_EMBED_TIMEOUT}.
 *
 * <p><b>Non-régression</b> : par défaut {@code enabled=false} et {@code embed.apiKey}
 * vide -> {@link #embedEnabled()} renvoie false -> aucun appel réseau, le RAG reste
 * en FTS seul (comportement actuel). Aucune clé n'est committée.
 *
 * @param enabled Active la voie LLM/vectorielle (défaut false). Combiné à la présence
 *                d'une clé (embeddings / chat) pour décider si l'on calcule/interroge.
 * @param topK    Nombre de chunks retournés par le retrieval (défaut 5).
 * @param embed   Bloc de configuration du provider d'embeddings.
 * @param chat    Bloc de configuration du provider de génération de réponse (chat).
 */
@ConfigurationProperties(prefix = "jurika.rag")
public record RagProperties(
        boolean enabled,
        int topK,
        @NestedConfigurationProperty Embed embed,
        @NestedConfigurationProperty Chat chat
) {

    public RagProperties {
        if (topK <= 0) topK = 5;
        if (embed == null) embed = new Embed(null, null, null, null, 0, 0);
        if (chat == null) chat = new Chat(null, null, null, null, -1.0, 0);
    }

    /** True si la voie vectorielle est réellement exploitable (flag ON + clé présente). */
    public boolean embedEnabled() {
        return enabled && embed != null && embed.hasApiKey();
    }

    /** True si la génération de réponse LLM est réellement exploitable (flag ON + clé présente). */
    public boolean chatEnabled() {
        return enabled && chat != null && chat.hasApiKey();
    }

    /**
     * Configuration du provider d'embeddings (API OpenAI-compatible {@code /embeddings}).
     * Défauts orientés Gemini text-embedding-004 (768 dimensions, gratuit, CNDP : à
     * n'utiliser que sur corpus non sensible — la clé reste optionnelle).
     *
     * @param provider       Identifiant logique (gemini / openai / custom).
     * @param baseUrl        URL de base OpenAI-compatible (sans {@code /embeddings}).
     * @param apiKey         Clé API (vide par défaut = voie vectorielle désactivée).
     * @param model          Nom du modèle d'embeddings.
     * @param dimensions     Dimension des vecteurs produits (doit matcher la colonne SQL).
     * @param timeoutSeconds Timeout HTTP (connect + read) en secondes.
     */
    public record Embed(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            int timeoutSeconds
    ) {
        public Embed {
            if (provider == null || provider.isBlank()) provider = "gemini";
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
            }
            if (apiKey == null) apiKey = "";
            if (model == null || model.isBlank()) model = "text-embedding-004";
            if (dimensions <= 0) dimensions = 768;
            if (timeoutSeconds <= 0) timeoutSeconds = 30;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Configuration du provider de génération de réponse (chat, API OpenAI-compatible
     * {@code /chat/completions}, TEXTE libre). Défauts orientés Gemini gemini-1.5-flash
     * (gratuit). La clé reste optionnelle (vide = voie chat désactivée -> repli KB+FTS).
     *
     * @param provider       Identifiant logique (gemini / openai / groq / custom).
     * @param baseUrl        URL de base OpenAI-compatible (sans {@code /chat/completions}).
     * @param apiKey         Clé API (vide par défaut = voie chat désactivée).
     * @param model          Nom du modèle de chat.
     * @param temperature    Température (0.0-2.0 ; défaut 0.2 pour une réponse ancrée stable).
     * @param timeoutSeconds Timeout HTTP (connect + read) en secondes.
     */
    public record Chat(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            int timeoutSeconds
    ) {
        public Chat {
            if (provider == null || provider.isBlank()) provider = "gemini";
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
            }
            if (apiKey == null) apiKey = "";
            if (model == null || model.isBlank()) model = "gemini-1.5-flash";
            if (temperature < 0.0d) temperature = 0.2d;
            if (temperature > 2.0d) temperature = 2.0d;
            if (timeoutSeconds <= 0) timeoutSeconds = 30;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}

package ma.jurika.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Client d'embeddings appelant une API OpenAI-compatible
 * ({@code POST {baseUrl}/embeddings}), miroir de
 * {@code ma.jurika.ai.llm.OpenAiCompatibleLlmProvider} mais dédié aux vecteurs.
 *
 * <p>Body envoyé :
 * <pre>{ "model": "text-embedding-004", "input": ["texte 1", "texte 2", ...] }</pre>
 * Header {@code Authorization: Bearer {apiKey}}. Réponse parsée :
 * {@code data[].embedding} -> {@code float[]}.
 *
 * <p><b>Robustesse (jamais throw)</b> : clé absente, voie désactivée, erreur HTTP,
 * réseau ou parsing -> renvoie une liste/Optional vide + {@code log.warn}. Aucun
 * appel réseau n'est émis quand la voie vectorielle est désactivée
 * ({@link RagProperties#embedEnabled()} == false).
 */
@Component
public class RagEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(RagEmbeddingClient.class);

    private final RagProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public RagEmbeddingClient(RagProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(props.embed().timeoutSeconds());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        log.info("RagEmbeddingClient — enabled={} provider={} baseUrl={} model={} dims={} apiKey={}",
                props.embedEnabled(), props.embed().provider(), props.embed().baseUrl(),
                props.embed().model(), props.embed().dimensions(),
                props.embed().hasApiKey() ? "PRESENT" : "ABSENT");
    }

    /** Constructeur de test — injection RestTemplate directe (MockRestServiceServer). */
    RagEmbeddingClient(RagProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    /** True si la voie vectorielle est exploitable (flag ON + clé présente). */
    public boolean isReady() {
        return props.embedEnabled();
    }

    /** Dimension attendue des vecteurs (miroir de la colonne SQL). */
    public int dimensions() {
        return props.embed().dimensions();
    }

    /**
     * Embedding d'un seul texte. Renvoie {@link Optional#empty()} si voie désactivée,
     * texte vide, ou toute erreur (jamais d'exception propagée).
     */
    public Optional<float[]> embed(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        List<float[]> out = embedBatch(List.of(text));
        return out.isEmpty() ? Optional.empty() : Optional.ofNullable(out.get(0));
    }

    /**
     * Embedding d'un lot de textes en un seul appel réseau. Renvoie une liste
     * <b>alignée</b> sur {@code texts} en cas de succès, ou une liste vide si la voie
     * est désactivée / toute erreur. Ne throw jamais.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        if (!isReady()) return List.of();

        String url = trimTrailingSlash(props.embed().baseUrl()) + "/embeddings";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.embed().model());
        body.put("input", texts);
        // `dimensions` doit etre transmis, sinon les modeles recents renvoient leur
        // taille native (gemini-embedding-001 : 3072) alors que la colonne SQL est
        // en vector(768) — l'insertion echoue et le corpus reste sans vecteur.
        if (props.embed().dimensions() > 0) {
            body.put("dimensions", props.embed().dimensions());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.embed().apiKey());

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isBlank()) {
                log.warn("Embeddings : réponse vide (status={}) — repli FTS", resp.getStatusCode());
                return List.of();
            }
            return parseEmbeddings(raw);
        } catch (HttpStatusCodeException e) {
            log.warn("Embeddings HTTP {} sur {} : {} — repli FTS", e.getStatusCode().value(), url,
                    safeShort(e.getResponseBodyAsString(), 300));
            return List.of();
        } catch (RuntimeException e) {
            log.warn("Embeddings erreur réseau / inattendue sur {} : {} — repli FTS", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Formatte un vecteur en littéral pgvector {@code "[v1,v2,...]"} à passer via
     * {@code ?::vector}. Renvoie null si le vecteur est absent (=> insertion NULL).
     */
    public static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) return null;
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            sj.add(Float.toString(v));
        }
        return sj.toString();
    }

    // ------------------------------------------------------------------------
    //  Parsing réponse OpenAI-compatible : { "data": [ { "embedding": [...] } ] }
    // ------------------------------------------------------------------------

    private List<float[]> parseEmbeddings(String body) {
        try {
            Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
            Object dataObj = root.get("data");
            if (!(dataObj instanceof List<?> data) || data.isEmpty()) {
                log.warn("Embeddings : réponse sans 'data' — repli FTS");
                return List.of();
            }
            List<float[]> out = new ArrayList<>(data.size());
            for (Object item : data) {
                if (!(item instanceof Map<?, ?> map)) return List.of();
                Object emb = map.get("embedding");
                if (!(emb instanceof List<?> values) || values.isEmpty()) return List.of();
                float[] vec = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    Object n = values.get(i);
                    if (!(n instanceof Number num)) return List.of();
                    vec[i] = num.floatValue();
                }
                out.add(vec);
            }
            return out;
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("Embeddings : parsing réponse échec ({}) — repli FTS", e.getMessage());
            return List.of();
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

package ma.jurika.ai.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.llm.schema.DocumentSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider LLM générique appelant n'importe quelle API OpenAI-compatible
 * (Groq, OpenAI, Ollama, vLLM, LM Studio, Together, etc.).
 * <p>
 * Endpoint utilisé : {@code POST {baseUrl}/chat/completions} avec body
 * <pre>
 * {
 *   "model": "...",
 *   "temperature": 0.0,
 *   "response_format": { "type": "json_object" },
 *   "messages": [
 *     { "role": "system", "content": "<prompt système strict JSON>" },
 *     { "role": "user",   "content": "TEXTE EXTRAIT:\n\n..." }
 *   ]
 * }
 * </pre>
 * <p>
 * Activation : {@code jurika.llm.enabled=true} ET clé API positionnée (sinon fallback Noop côté
 * orchestrateur via {@link LlmProperties#hasApiKey()}, mais ce provider sera tout de même créé).
 * <p>
 * Robustesse : aucune exception propagée — toute erreur HTTP / réseau / parsing produit un
 * {@link LlmExtractionResult#degraded(String, String, String)} avec warning explicite.
 */
@Component
@ConditionalOnProperty(name = "jurika.llm.enabled", havingValue = "true")
public class OpenAiCompatibleLlmProvider implements LlmExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmProvider.class);

    private final LlmProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public OpenAiCompatibleLlmProvider(LlmProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(props.timeoutSeconds());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        log.info("OpenAiCompatibleLlmProvider actif — provider={} baseUrl={} model={} apiKey={}",
                props.provider(),
                props.baseUrl(),
                props.model(),
                props.hasApiKey() ? "PRESENT" : "ABSENT");
    }

    /** Constructeur de test — injection RestTemplate directe (utilisé par {@code OpenAiCompatibleLlmProviderTest}). */
    OpenAiCompatibleLlmProvider(LlmProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    @Override
    public LlmExtractionResult extract(String text, DocumentSchema schema) {
        if (schema == null) {
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Schéma absent — extraction impossible");
        }
        if (!props.hasApiKey() && !isLocalProvider()) {
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Clé API LLM absente (LLM_API_KEY non définie) — saisie manuelle requise");
        }
        if (text == null || text.isBlank()) {
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Texte source vide — rien à extraire");
        }

        String url = trimTrailingSlash(props.baseUrl()) + "/chat/completions";
        Map<String, Object> body = buildRequestBody(text, schema);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (props.hasApiKey()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
        }

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isBlank()) {
                return LlmExtractionResult.degraded(props.provider(), props.model(),
                        "Réponse LLM vide (status=" + resp.getStatusCode() + ")");
            }
            return parseResponse(raw, schema);
        } catch (HttpStatusCodeException e) {
            log.warn("LLM HTTP {} sur {} : {}", e.getStatusCode().value(), url,
                    safeShort(e.getResponseBodyAsString(), 300));
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Erreur LLM HTTP " + e.getStatusCode().value() + " — saisie manuelle requise");
        } catch (RuntimeException e) {
            log.warn("LLM erreur réseau / inattendue sur {} : {}", url, e.getMessage());
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Erreur réseau LLM (" + e.getClass().getSimpleName() + ") — saisie manuelle requise");
        }
    }

    // ------------------------------------------------------------------------
    //  Construction prompt + body
    // ------------------------------------------------------------------------

    private Map<String, Object> buildRequestBody(String text, DocumentSchema schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("temperature", props.temperature());
        body.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(schema)));
        messages.add(Map.of("role", "user", "content", "TEXTE EXTRAIT:\n\n" + text));
        body.put("messages", messages);

        return body;
    }

    static String buildSystemPrompt(DocumentSchema schema) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("Tu es un extracteur de données structurées. Voici un document de type ")
                .append(schema.typeCode()).append(" (").append(schema.description()).append(").\n")
                .append("Extrais UNIQUEMENT les champs suivants. Renvoie un objet JSON strict ")
                .append("(pas de markdown, pas de commentaire, pas de texte hors JSON).\n\n")
                .append("Champs attendus (nom: type — description) :\n");
        for (DocumentSchema.FieldDef f : schema.fields()) {
            sb.append("- ").append(f.name())
                    .append(": ").append(f.type())
                    .append(" — ").append(f.description());
            if (f.required()) sb.append(" (OBLIGATOIRE)");
            sb.append('\n');
        }
        sb.append("\nSi un champ n'est pas trouvé dans le texte, retourne null pour ce champ ")
                .append("(ne devine pas).\n")
                .append("Pour les dates, utilise STRICTEMENT le format ISO YYYY-MM-DD.\n")
                .append("Pour les nombres, retourne un entier ou décimal JSON natif (pas de String).\n")
                .append("Réponds par un unique objet JSON avec exactement les clés listées ci-dessus.");
        return sb.toString();
    }

    // ------------------------------------------------------------------------
    //  Parsing réponse OpenAI-compatible
    // ------------------------------------------------------------------------

    private LlmExtractionResult parseResponse(String body, DocumentSchema schema) {
        try {
            Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
            Object choicesObj = root.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return LlmExtractionResult.degraded(props.provider(), props.model(),
                        "Réponse LLM sans 'choices' — saisie manuelle requise");
            }
            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> firstMap)) {
                return LlmExtractionResult.degraded(props.provider(), props.model(),
                        "Réponse LLM 'choices[0]' invalide");
            }
            Object message = firstMap.get("message");
            if (!(message instanceof Map<?, ?> messageMap)) {
                return LlmExtractionResult.degraded(props.provider(), props.model(),
                        "Réponse LLM 'message' absent");
            }
            Object content = messageMap.get("content");
            if (!(content instanceof String contentStr) || contentStr.isBlank()) {
                return LlmExtractionResult.degraded(props.provider(), props.model(),
                        "Contenu LLM vide");
            }
            Map<String, Object> raw = parseJsonObjectLoose(contentStr);
            Map<String, Object> fields = projectOnSchema(raw, schema);
            return new LlmExtractionResult(
                    fields,
                    Collections.emptyMap(),
                    props.provider(),
                    props.model(),
                    false,
                    null
            );
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("LLM parsing réponse échec : {}", e.getMessage());
            return LlmExtractionResult.degraded(props.provider(), props.model(),
                    "Parsing réponse LLM échec — saisie manuelle requise");
        }
    }

    /** Parse JSON robuste : tente parse direct, puis tente d'extraire le 1er objet {{...}}. */
    private Map<String, Object> parseJsonObjectLoose(String content) throws java.io.IOException {
        String trimmed = content.trim();
        try {
            return mapper.readValue(trimmed, new TypeReference<>() {});
        } catch (RuntimeException | java.io.IOException ignored) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String sub = trimmed.substring(start, end + 1);
                return mapper.readValue(sub, new TypeReference<>() {});
            }
            throw new java.io.IOException("Pas d'objet JSON dans la réponse");
        }
    }

    /** Garde seulement les clés du schéma ; null autorisé pour les champs absents. */
    private Map<String, Object> projectOnSchema(Map<String, Object> raw, DocumentSchema schema) {
        Map<String, Object> out = new HashMap<>();
        for (DocumentSchema.FieldDef f : schema.fields()) {
            out.put(f.name(), raw.get(f.name()));
        }
        return out;
    }

    // ------------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------------

    private boolean isLocalProvider() {
        // Ollama / LM Studio / vLLM local n'exigent pas de clé API.
        String p = props.provider() == null ? "" : props.provider().toLowerCase();
        return p.contains("ollama") || p.contains("local") || p.contains("lmstudio") || p.contains("vllm");
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

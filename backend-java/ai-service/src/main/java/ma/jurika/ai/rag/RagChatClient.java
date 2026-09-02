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

/**
 * Client de génération de réponse (chat) appelant une API OpenAI-compatible
 * ({@code POST {baseUrl}/chat/completions}), miroir de
 * {@code ma.jurika.ai.llm.OpenAiCompatibleLlmProvider} mais en <b>TEXTE LIBRE</b>
 * (pas de {@code response_format} json_object) : la réponse est destinée à
 * l'utilisateur final du chatbot.
 *
 * <p>Body envoyé :
 * <pre>{ "model": "...", "temperature": 0.2, "messages": [ {system}, {user} ] }</pre>
 * Header {@code Authorization: Bearer {apiKey}}. Réponse parsée :
 * {@code choices[0].message.content} -> {@code String}.
 *
 * <p><b>Robustesse (jamais throw)</b> : clé absente, voie désactivée, erreur HTTP,
 * réseau ou parsing -> {@link Optional#empty()} + {@code log.warn}, ce qui laisse
 * {@link RagService} retomber sur la réponse KB+FTS. Aucun appel réseau n'est émis
 * quand la voie chat est désactivée ({@link RagProperties#chatEnabled()} == false).
 * La clé n'est jamais journalisée (seul son état PRESENT/ABSENT l'est).
 */
@Component
public class RagChatClient {

    private static final Logger log = LoggerFactory.getLogger(RagChatClient.class);

    private final RagProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public RagChatClient(RagProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(props.chat().timeoutSeconds());
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        log.info("RagChatClient — enabled={} provider={} baseUrl={} model={} apiKey={}",
                props.chatEnabled(), props.chat().provider(), props.chat().baseUrl(),
                props.chat().model(), props.chat().hasApiKey() ? "PRESENT" : "ABSENT");
    }

    /** Constructeur de test — injection RestTemplate directe (MockRestServiceServer). */
    RagChatClient(RagProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    /** True si la génération de réponse LLM est exploitable (flag ON + clé présente). */
    public boolean isReady() {
        return props.chatEnabled();
    }

    /**
     * Génère une réponse en texte libre à partir d'un prompt système + utilisateur.
     * Renvoie {@link Optional#empty()} si la voie est désactivée, si le prompt utilisateur
     * est vide, ou en cas de toute erreur (jamais d'exception propagée).
     */
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return Optional.empty();
        if (!isReady()) return Optional.empty();

        String url = trimTrailingSlash(props.chat().baseUrl()) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.chat().model());
        body.put("temperature", props.chat().temperature());
        List<Map<String, String>> messages = new ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.chat().apiKey());

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isBlank()) {
                log.warn("Chat RAG : réponse vide (status={}) — repli KB+FTS", resp.getStatusCode());
                return Optional.empty();
            }
            return parseContent(raw);
        } catch (HttpStatusCodeException e) {
            log.warn("Chat RAG HTTP {} sur {} : {} — repli KB+FTS", e.getStatusCode().value(), url,
                    safeShort(e.getResponseBodyAsString(), 300));
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Chat RAG erreur réseau / inattendue sur {} : {} — repli KB+FTS", url, e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------------
    //  Parsing réponse OpenAI-compatible : choices[0].message.content
    // ------------------------------------------------------------------------

    private Optional<String> parseContent(String body) {
        try {
            Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
            Object choicesObj = root.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                log.warn("Chat RAG : réponse sans 'choices' — repli KB+FTS");
                return Optional.empty();
            }
            if (!(choices.get(0) instanceof Map<?, ?> firstMap)) return Optional.empty();
            if (!(firstMap.get("message") instanceof Map<?, ?> messageMap)) return Optional.empty();
            Object content = messageMap.get("content");
            if (!(content instanceof String contentStr) || contentStr.isBlank()) {
                log.warn("Chat RAG : contenu vide — repli KB+FTS");
                return Optional.empty();
            }
            return Optional.of(contentStr.trim());
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("Chat RAG : parsing réponse échec ({}) — repli KB+FTS", e.getMessage());
            return Optional.empty();
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

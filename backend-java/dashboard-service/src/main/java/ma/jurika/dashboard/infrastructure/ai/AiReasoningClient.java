package ma.jurika.dashboard.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Lot IA-1 — Client HTTP du raisonnement LLM (ai-service {@code /internal/llm/reason}).
 *
 * <p>Modele {@code HttpAiPdfConversionClient} (RestClient + timeouts). Contrat de
 * SURETE : ne jette jamais. Toute indisponibilite (LLM off, {@code text:null},
 * timeout, 5xx, injoignable) -> {@link Optional#empty()} pour que
 * {@code AgentCopiloteService} retombe sur son briefing a base de regles.
 */
public class AiReasoningClient {

    private static final Logger log = LoggerFactory.getLogger(AiReasoningClient.class);

    private final RestClient client;

    public AiReasoningClient(RestClient client) {
        this.client = client;
    }

    /**
     * Demande au LLM de raisonner sur l'etat fourni.
     *
     * @return le texte genere, ou {@link Optional#empty()} si indisponible.
     */
    public Optional<String> reason(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return Optional.empty();
        try {
            Map<?, ?> body = client.post()
                    .uri("/internal/llm/reason")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "systemPrompt", systemPrompt == null ? "" : systemPrompt,
                            "userPrompt", userPrompt))
                    .retrieve()
                    .body(Map.class);
            if (body == null) return Optional.empty();
            Object text = body.get("text");
            if (text == null || text.toString().isBlank()) return Optional.empty();
            return Optional.of(text.toString());
        } catch (RuntimeException ex) {
            log.warn("ai-service /internal/llm/reason indisponible — fallback regles : {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Wiring RestClient (base-url + timeouts) — meme patron que les autres clients HTTP. */
    @Configuration
    @EnableConfigurationProperties(AiReasoningProperties.class)
    public static class AiReasoningClientConfig {

        @Bean
        public AiReasoningClient aiReasoningClient(AiReasoningProperties props) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) props.getTimeout().toMillis());
            factory.setReadTimeout((int) props.getTimeout().toMillis());
            RestClient rc = RestClient.builder()
                    .baseUrl(props.getBaseUrl())
                    .requestFactory(factory)
                    .build();
            return new AiReasoningClient(rc);
        }
    }
}

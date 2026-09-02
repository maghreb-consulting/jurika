package ma.jurika.dashboard.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Lot IA-1 — Configuration de l'appel au raisonnement LLM d'ai-service
 * ({@code POST /internal/llm/reason}). Meme convention que {@code jurika.ai}
 * cote dataroom-service.
 */
@ConfigurationProperties(prefix = "jurika.ai")
public class AiReasoningProperties {

    private String baseUrl = "http://ai-service:8085";
    private Duration timeout = Duration.ofSeconds(30);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}

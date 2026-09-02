package ma.jurika.dataroom.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration {@code jurika.ai.*} — client de conversion PDF vers ai-service.
 */
@ConfigurationProperties(prefix = "jurika.ai")
public class AiConversionProperties {

    /** URL de base d'ai-service (DNS interne docker). */
    private String baseUrl = "http://ai-service:8085";

    /**
     * Delai de conversion. LibreOffice a un cold-start non negligeable + la
     * conversion elle-meme (1-3 s) : 60 s par defaut.
     */
    private Duration timeout = Duration.ofSeconds(60);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration v) { this.timeout = v; }
}

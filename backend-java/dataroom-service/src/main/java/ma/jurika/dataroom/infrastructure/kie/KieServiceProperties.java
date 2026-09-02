package ma.jurika.dataroom.infrastructure.kie;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration {@code jurika.kie.*}.
 */
@ConfigurationProperties(prefix = "jurika.kie")
public class KieServiceProperties {

    /** URL de base du microservice kie-service (DNS interne docker). */
    private String baseUrl = "http://kie-service:8088";

    /** Délai d'inférence Donut (CPU) : 30s par défaut, l'inférence peut être longue. */
    private Duration timeout = Duration.ofSeconds(30);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration v) { this.timeout = v; }
}

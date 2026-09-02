package ma.jurika.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound from {@code jurika.jwt.*}. See {@code application.yml} for documentation.
 */
@ConfigurationProperties(prefix = "jurika.jwt")
public record JwtProperties(
        @DefaultValue("HS256") String algorithm,
        @DefaultValue("") String publicKeyPath,
        @DefaultValue("") String privateKeyPath,
        @DefaultValue("") String hmacSecret,
        @DefaultValue("jurika.ma") String issuer,
        @DefaultValue("900000") long accessTtlMs,
        @DefaultValue("604800000") long refreshTtlMs
) {

    public boolean isRsa() {
        return "RS256".equalsIgnoreCase(algorithm);
    }
}

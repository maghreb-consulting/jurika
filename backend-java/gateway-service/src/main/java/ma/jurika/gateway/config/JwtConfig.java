package ma.jurika.gateway.config;

import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.JwtPublicKeyProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link JwtPublicKeyProvider} consumed by
 * {@code JwtClaimsPropagationFilter}. Reads {@code jurika.jwt.*} and picks
 * RS256 (default) or HS256 legacy fallback automatically.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtPublicKeyProvider jwtPublicKeyProvider(JwtProperties props) {
        return JwtPublicKeyProviderFactory.build(props, "gateway-service");
    }
}

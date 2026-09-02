package ma.jurika.auth.infrastructure.security;

import ma.jurika.common.security.JwtKeyConfig;
import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtPublicKeyProvider;
import ma.jurika.common.security.JwtSigningKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Builds the JWT signing/verification key beans for the auth-service.
 *
 * <ul>
 *   <li>{@code RS256} (default): signs with the RSA private key from {@code privateKeyPath},
 *       verifies locally with the public key from {@code publicKeyPath}.</li>
 *   <li>{@code HS256} (legacy dev): both signing and verification fall back to the HMAC secret.</li>
 * </ul>
 */
@Configuration
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    @Bean
    public JwtSigningKeyProvider jwtSigningKeyProvider(JwtProperties props) {
        if (props.isRsa()) {
            PrivateKey privateKey = JwtKeyConfig.loadPrivateKey(props.privateKeyPath());
            log.info("JWT signing: RS256 (private key loaded from {})", props.privateKeyPath());
            return new RsaSigningKeyProvider(privateKey);
        }
        log.warn("JWT signing: HS256 legacy fallback (set JWT_ALGORITHM=RS256 in production)");
        Key hmac = JwtKeyConfig.legacyHmacKey(props.hmacSecret());
        return new HmacSigningKeyProvider(hmac);
    }

    @Bean
    public JwtPublicKeyProvider jwtPublicKeyProvider(JwtProperties props) {
        if (props.isRsa()) {
            PublicKey publicKey = JwtKeyConfig.loadPublicKey(props.publicKeyPath());
            log.info("JWT verification: RS256 (public key loaded from {})", props.publicKeyPath());
            return () -> publicKey;
        }
        Key hmac = JwtKeyConfig.legacyHmacKey(props.hmacSecret());
        return () -> hmac;
    }

    private record RsaSigningKeyProvider(PrivateKey key) implements JwtSigningKeyProvider {
        @Override
        public Key signingKey() {
            return key;
        }

        @Override
        public String algorithm() {
            return "RS256";
        }
    }

    private record HmacSigningKeyProvider(Key key) implements JwtSigningKeyProvider {
        @Override
        public Key signingKey() {
            return key;
        }

        @Override
        public String algorithm() {
            return "HS256";
        }
    }
}

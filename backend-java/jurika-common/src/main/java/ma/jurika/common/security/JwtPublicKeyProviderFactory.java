package ma.jurika.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;

/**
 * Builds a {@link JwtPublicKeyProvider} from {@link JwtProperties}, choosing
 * RSA public key or HMAC fallback based on {@code jurika.jwt.algorithm}.
 *
 * <p>Used by every verification-only service (gateway, ticket, workflow, dataroom,
 * ai, supervision) so the routing logic lives in one place.
 */
public final class JwtPublicKeyProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(JwtPublicKeyProviderFactory.class);

    private JwtPublicKeyProviderFactory() {
    }

    public static JwtPublicKeyProvider build(JwtProperties props, String serviceName) {
        if (props.isRsa()) {
            Key key = JwtKeyConfig.loadPublicKey(props.publicKeyPath());
            log.info("[{}] JWT verification: RS256 (public key from {})", serviceName, props.publicKeyPath());
            return () -> key;
        }
        log.warn("[{}] JWT verification: HS256 legacy fallback (set JWT_ALGORITHM=RS256 for production)", serviceName);
        Key key = JwtKeyConfig.legacyHmacKey(props.hmacSecret());
        return () -> key;
    }
}

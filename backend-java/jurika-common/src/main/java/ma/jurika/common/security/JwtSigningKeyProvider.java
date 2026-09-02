package ma.jurika.common.security;

import java.security.Key;

/**
 * Provides the JWT signing key for the issuer (auth-service).
 *
 * <p>Implementations return either an RSA {@link java.security.PrivateKey} (when
 * {@code jurika.jwt.algorithm=RS256}) or an HMAC {@link javax.crypto.SecretKey}
 * (legacy fallback when {@code jurika.jwt.algorithm=HS256}).
 */
public interface JwtSigningKeyProvider {

    Key signingKey();

    String algorithm();
}

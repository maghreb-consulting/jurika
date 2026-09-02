package ma.jurika.auth.infrastructure.security;

import io.jsonwebtoken.Jwts;
import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.common.security.JwtClaims;
import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtSigningKeyProvider;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private static final SecureRandom RNG = new SecureRandom();

    private final JwtSigningKeyProvider signingKeyProvider;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final String issuer;

    public JwtTokenIssuer(JwtSigningKeyProvider signingKeyProvider, JwtProperties props) {
        this.signingKeyProvider = signingKeyProvider;
        this.accessTtl = Duration.ofMillis(props.accessTtlMs());
        this.refreshTtl = Duration.ofMillis(props.refreshTtlMs());
        this.issuer = props.issuer();
    }

    @Override
    public AuthTokens issue(User user) {
        Instant now = Instant.now();
        Instant accessExp = now.plus(accessTtl);
        Instant refreshExp = now.plus(refreshTtl);

        Key key = signingKeyProvider.signingKey();
        boolean isRsa = "RS256".equalsIgnoreCase(signingKeyProvider.algorithm());

        var builder = Jwts.builder()
                .subject(user.id().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExp))
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .claim(JwtClaims.USER_ID, user.id().toString())
                .claim(JwtClaims.WORKSPACE_ID, user.workspaceId().toString())
                .claim(JwtClaims.EMAIL, user.loginEmail())
                .claim(JwtClaims.LOGIN_EMAIL, user.loginEmail())
                .claim(JwtClaims.CONTACT_EMAIL, user.contactEmail())
                .claim(JwtClaims.ROLE, user.role().name())
                .claim(JwtClaims.MUST_CHANGE_PASSWORD, user.mustChangePassword())
                .claim(JwtClaims.REQUIRES_2FA_SETUP, user.requires2faSetup());

        String accessToken;
        if (isRsa && key instanceof PrivateKey pk) {
            accessToken = builder.signWith(pk, Jwts.SIG.RS256).compact();
        } else if (!isRsa && key instanceof SecretKey sk) {
            accessToken = builder.signWith(sk, Jwts.SIG.HS256).compact();
        } else {
            throw new IllegalStateException(
                    "Type de cle JWT incompatible avec l'algo " + signingKeyProvider.algorithm()
                            + " (cle: " + key.getClass().getSimpleName() + ")");
        }

        String refreshToken = generateOpaqueRefreshToken();

        return new AuthTokens(accessToken, refreshToken, accessExp, refreshExp,
                user.id(), user.workspaceId());
    }

    @Override
    public String hashRefreshToken(String rawRefreshToken) {
        return sha256Hex(rawRefreshToken);
    }

    @Override
    public ParsedRefreshToken parseRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token vide");
        }
        return new ParsedRefreshToken(rawRefreshToken, sha256Hex(rawRefreshToken));
    }

    private String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        RNG.nextBytes(bytes);
        UUID id = UUID.randomUUID();
        return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}

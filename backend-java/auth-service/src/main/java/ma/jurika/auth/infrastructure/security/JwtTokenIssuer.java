package ma.jurika.auth.infrastructure.security;

import io.jsonwebtoken.Jwts;
import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** Lot L7 (E5) : etat du workspace (archive) lu a chaque emission ; null dans les tests unitaires. */
    private final WorkspaceRepository workspaceRepository;
    private final org.springframework.transaction.support.TransactionTemplate lecture;

    public JwtTokenIssuer(JwtSigningKeyProvider signingKeyProvider, JwtProperties props) {
        this(signingKeyProvider, props, null, null);
    }

    @Autowired
    public JwtTokenIssuer(JwtSigningKeyProvider signingKeyProvider, JwtProperties props,
                          WorkspaceRepository workspaceRepository,
                          org.springframework.transaction.PlatformTransactionManager transactions) {
        this.workspaceRepository = workspaceRepository;
        this.lecture = transactions == null ? null : new org.springframework.transaction.support.TransactionTemplate(transactions);
        if (this.lecture != null) this.lecture.setReadOnly(true);
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
        // Lot L7 (E5), RG-TAR-07 : un workspace archive n'ouvre que les chemins de AccesArchive.
        if (workspaceArchive(user)) {
            builder.claim(JwtClaims.ARCHIVE, true);
        }

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

    /**
     * Lot L7 (E5) : le workspace de l'utilisateur est-il archive ? Lu dans une transaction (celle de
     * l'appelant, ou une transaction de lecture ouverte ici) et dans le workspace de l'utilisateur,
     * pour la RLS.
     */
    private boolean workspaceArchive(User user) {
        if (workspaceRepository == null) return false;
        java.util.UUID avant = ma.jurika.common.security.TenantContext.get();
        try {
            if (avant == null) ma.jurika.common.security.TenantContext.set(user.workspaceId());
            java.util.function.Supplier<Boolean> lire = () -> workspaceRepository.findById(user.workspaceId())
                    .map(ma.jurika.auth.domain.model.Workspace::isArchived).orElse(false);
            Boolean archive = lecture == null ? lire.get() : lecture.execute(st -> lire.get());
            return Boolean.TRUE.equals(archive);
        } finally {
            if (avant == null) ma.jurika.common.security.TenantContext.clear();
        }
    }
}

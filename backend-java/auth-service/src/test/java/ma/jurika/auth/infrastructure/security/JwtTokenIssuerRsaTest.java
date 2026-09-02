package ma.jurika.auth.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.common.security.JwtClaims;
import ma.jurika.common.security.JwtProperties;
import ma.jurika.common.security.JwtSigningKeyProvider;
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenIssuerRsaTest {

    @Test
    void rsaTokenIsSignedWithPrivateKeyAndVerifiableWithPublicKey() throws Exception {
        KeyPair kp = generateRsaKeypair();

        JwtSigningKeyProvider provider = new JwtSigningKeyProvider() {
            @Override public Key signingKey() { return kp.getPrivate(); }
            @Override public String algorithm() { return "RS256"; }
        };
        JwtProperties props = new JwtProperties("RS256", "", "", "", "jurika.ma", 900_000L, 604_800_000L);
        JwtTokenIssuer issuer = new JwtTokenIssuer(provider, props);

        User user = sampleUser();

        AuthTokens tokens = issuer.issue(user);

        assertThat(tokens.accessToken()).isNotBlank();
        PublicKey publicKey = kp.getPublic();
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(tokens.accessToken());

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
        Claims claims = jws.getPayload();
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(JwtClaims.TYPE_ACCESS);
        assertThat(claims.get(JwtClaims.USER_ID, String.class)).isEqualTo(user.id().toString());
        assertThat(claims.get(JwtClaims.WORKSPACE_ID, String.class)).isEqualTo(user.workspaceId().toString());
        assertThat(claims.get(JwtClaims.EMAIL, String.class)).isEqualTo(user.email());
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo(user.role().name());
        assertThat(claims.getExpiration().toInstant())
                .isAfter(Instant.now())
                .isBefore(Instant.now().plusSeconds(901L));
    }

    @Test
    void hs256FallbackStillWorks() {
        String secret = "this-is-a-32+ character HMAC secret for fallback";
        Key hmac = javax.crypto.spec.SecretKeySpec.class
                .cast(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));

        JwtSigningKeyProvider provider = new JwtSigningKeyProvider() {
            @Override public Key signingKey() { return hmac; }
            @Override public String algorithm() { return "HS256"; }
        };
        JwtProperties props = new JwtProperties("HS256", "", "", secret, "jurika.ma", 900_000L, 604_800_000L);
        JwtTokenIssuer issuer = new JwtTokenIssuer(provider, props);

        AuthTokens tokens = issuer.issue(sampleUser());

        Jws<Claims> jws = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) hmac)
                .build()
                .parseSignedClaims(tokens.accessToken());

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    private static KeyPair generateRsaKeypair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static User sampleUser() {
        return new User(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@jurika.ma",
                "hash",
                "Karim",
                "El Idrissi",
                "+212600000000",
                Role.EMPLOYE,
                null,
                false,
                false,
                null,
                Instant.now(),
                null,
                null,
                (short) 0,
                null,
                null,
                ma.jurika.auth.domain.model.UserStatus.ACTIVE,
                Instant.now()
        );
    }
}

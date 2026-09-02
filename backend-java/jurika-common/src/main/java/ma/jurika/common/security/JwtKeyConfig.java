package ma.jurika.common.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utilities for loading JWT signing/verification keys from disk.
 *
 * <ul>
 *   <li>{@link #loadPublicKey(String)} — reads an X.509 PEM public key (RSA).</li>
 *   <li>{@link #loadPrivateKey(String)} — reads a PKCS#8 PEM private key (RSA).</li>
 *   <li>{@link #legacyHmacKey(String)} — wraps an HMAC secret (legacy HS256 fallback).</li>
 * </ul>
 */
public final class JwtKeyConfig {

    private static final String RSA = "RSA";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MIN_HMAC_LENGTH = 32;

    private JwtKeyConfig() {
    }

    public static PublicKey loadPublicKey(String pemPath) {
        byte[] der = decodePem(pemPath, "PUBLIC KEY");
        try {
            return KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Impossible de charger la cle publique RSA depuis " + pemPath, e);
        }
    }

    public static PrivateKey loadPrivateKey(String pemPath) {
        byte[] der = decodePem(pemPath, "PRIVATE KEY");
        try {
            return KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Impossible de charger la cle privee RSA (PKCS8) depuis " + pemPath, e);
        }
    }

    public static SecretKey legacyHmacKey(String secret) {
        if (secret == null || secret.length() < MIN_HMAC_LENGTH) {
            throw new IllegalStateException(
                    "jurika.jwt.hmac-secret doit faire au moins " + MIN_HMAC_LENGTH + " caracteres");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }

    private static byte[] decodePem(String pemPath, String expectedHeaderKeyword) {
        if (pemPath == null || pemPath.isBlank()) {
            throw new IllegalStateException("Chemin PEM vide");
        }
        String content;
        try {
            content = Files.readString(Path.of(pemPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Fichier PEM introuvable: " + pemPath, e);
        }
        String stripped = content
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        if (stripped.isEmpty()) {
            throw new IllegalStateException(
                    "PEM vide ou mal forme (attendu " + expectedHeaderKeyword + "): " + pemPath);
        }
        return Base64.getDecoder().decode(stripped);
    }
}

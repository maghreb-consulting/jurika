package ma.jurika.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyConfigTest {

    @Test
    void loadsRsaPublicKeyFromPem(@TempDir Path tmp) throws Exception {
        KeyPair kp = generateRsaKeypair();
        Path pem = writePem(tmp.resolve("pub.pem"), "PUBLIC KEY", kp.getPublic().getEncoded());

        PublicKey loaded = JwtKeyConfig.loadPublicKey(pem.toString());

        assertThat(loaded.getAlgorithm()).isEqualTo("RSA");
        assertThat(loaded.getEncoded()).isEqualTo(kp.getPublic().getEncoded());
    }

    @Test
    void loadsRsaPrivateKeyFromPkcs8Pem(@TempDir Path tmp) throws Exception {
        KeyPair kp = generateRsaKeypair();
        Path pem = writePem(tmp.resolve("priv.pem"), "PRIVATE KEY", kp.getPrivate().getEncoded());

        PrivateKey loaded = JwtKeyConfig.loadPrivateKey(pem.toString());

        assertThat(loaded.getAlgorithm()).isEqualTo("RSA");
        assertThat(loaded.getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
    }

    @Test
    void buildsHmacKeyFromSecret() {
        String secret = "this-is-a-dev-only-secret-of-at-least-32-chars";

        SecretKey key = JwtKeyConfig.legacyHmacKey(secret);

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(secret.length());
    }

    @Test
    void rejectsShortHmacSecret() {
        assertThatThrownBy(() -> JwtKeyConfig.legacyHmacKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    @Test
    void rejectsBlankPemPath() {
        assertThatThrownBy(() -> JwtKeyConfig.loadPublicKey(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.pem");
        assertThatThrownBy(() -> JwtKeyConfig.loadPrivateKey(missing.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("introuvable");
    }

    private static KeyPair generateRsaKeypair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static Path writePem(Path target, String headerLabel, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        String pem = "-----BEGIN " + headerLabel + "-----\n" + body + "\n-----END " + headerLabel + "-----\n";
        Files.writeString(target, pem);
        return target;
    }
}

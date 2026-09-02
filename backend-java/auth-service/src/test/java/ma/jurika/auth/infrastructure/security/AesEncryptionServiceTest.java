package ma.jurika.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesEncryptionServiceTest {

    @Test
    void encryptThenDecryptRoundTrip() {
        AesEncryptionService svc = new AesEncryptionService("TestSecretKeyMin32CharactersLooong____");
        String plain = "JBSWY3DPEHPK3PXP";

        String encrypted = svc.encrypt(plain);
        String decrypted = svc.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void differentInvocationsProduceDifferentCiphertext() {
        AesEncryptionService svc = new AesEncryptionService("TestSecretKeyMin32CharactersLooong____");
        String plain = "Same input";

        assertThat(svc.encrypt(plain)).isNotEqualTo(svc.encrypt(plain));
    }
}

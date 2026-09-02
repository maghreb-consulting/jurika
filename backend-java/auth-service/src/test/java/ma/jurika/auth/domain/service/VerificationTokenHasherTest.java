package ma.jurika.auth.domain.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationTokenHasherTest {

    private final VerificationTokenHasher hasher = new VerificationTokenHasher();

    @Test
    void hashIsDeterministic() {
        assertThat(hasher.hash("hello")).isEqualTo(hasher.hash("hello"));
    }

    @Test
    void hashIsHex64Chars() {
        // SHA-256 = 32 octets = 64 chars hex
        assertThat(hasher.hash("any")).hasSize(64);
    }

    @Test
    void differentInputsGiveDifferentHashes() {
        assertThat(hasher.hash("a")).isNotEqualTo(hasher.hash("b"));
    }

    @Test
    void generatesValidUuidToken() {
        String token = hasher.generateUuidToken();
        // Format UUID v4
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @RepeatedTest(20)
    void generatesNumericCodeWithRequestedLength() {
        String code = hasher.generateNumericCode(6);
        assertThat(code).hasSize(6).matches("\\d{6}");
    }

    @Test
    void rejectsNumericCodeOutOfBounds() {
        assertThatThrownBy(() -> hasher.generateNumericCode(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.generateNumericCode(11))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

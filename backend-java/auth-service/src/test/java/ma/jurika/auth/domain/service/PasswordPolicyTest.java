package ma.jurika.auth.domain.service;

import ma.jurika.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.check("Strong@2026!")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShort() {
        // V2 : min 12 chars (etait 10 en V1)
        assertThatThrownBy(() -> policy.check("Sh@rt12345"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("12 caracteres");
    }

    @Test
    void rejectsNoUpperCase() {
        assertThatThrownBy(() -> policy.check("nouppercase@2026"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("majuscule");
    }

    @Test
    void rejectsNoSpecial() {
        assertThatThrownBy(() -> policy.check("NoSpecial2026"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("special");
    }
}

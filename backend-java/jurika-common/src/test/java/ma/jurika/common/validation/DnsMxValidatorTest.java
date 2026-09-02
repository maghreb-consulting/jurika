package ma.jurika.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DnsMxValidatorTest {

    // DNS-MX desactive pour les tests unitaires (pas de reseau requis)
    private final DnsMxValidator validator = new DnsMxValidator(false, 3000);

    @Test
    void rejectsNullEmail() {
        assertThat(validator.validate(null).valid()).isFalse();
        assertThat(validator.validate(null).reason()).isEqualTo("EMAIL_FORMAT_INVALID");
    }

    @Test
    void rejectsBlankEmail() {
        assertThat(validator.validate("").valid()).isFalse();
        assertThat(validator.validate("   ").valid()).isFalse();
    }

    @Test
    void rejectsMalformedEmail() {
        assertThat(validator.validate("notanemail").valid()).isFalse();
        assertThat(validator.validate("missing@domain").valid()).isFalse();
        assertThat(validator.validate("@nolocal.com").valid()).isFalse();
        assertThat(validator.validate("spaces in@example.com").valid()).isFalse();
    }

    @Test
    void acceptsValidFormatWhenDnsDisabled() {
        assertThat(validator.validate("user@example.com").valid()).isTrue();
        assertThat(validator.validate("first.last+tag@cabinet-test.ma").valid()).isTrue();
        assertThat(validator.validate("admin@jurika.ma").valid()).isTrue();
    }

    @Test
    void formatInvalidReasonIsExplicit() {
        var result = validator.validate("bad");
        assertThat(result.reason()).isEqualTo("EMAIL_FORMAT_INVALID");
    }
}

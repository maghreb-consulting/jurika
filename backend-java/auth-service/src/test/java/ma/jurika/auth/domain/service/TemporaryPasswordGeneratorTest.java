package ma.jurika.auth.domain.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(12);

    @Test
    void returnsRequestedLength() {
        assertThat(generator.generate()).hasSize(12);
    }

    @RepeatedTest(20)
    void alwaysContainsAllCategories() {
        String pwd = generator.generate();
        assertThat(pwd).matches(".*[A-Z].*");
        assertThat(pwd).matches(".*[a-z].*");
        assertThat(pwd).matches(".*[0-9].*");
        assertThat(pwd).matches(".*[!@#$%^&*\\-_=+?].*");
    }

    @RepeatedTest(10)
    void avoidsAmbiguousCharacters() {
        String pwd = generator.generate();
        // L'alphabet exclut O, I, l, 0, 1 pour eviter les ambiguites visuelles
        assertThat(pwd).doesNotContain("O").doesNotContain("I")
                       .doesNotContain("l").doesNotContain("0")
                       .doesNotContain("1");
    }

    @Test
    void generatesDifferentPasswords() {
        String p1 = generator.generate();
        String p2 = generator.generate();
        assertThat(p1).isNotEqualTo(p2);
    }
}

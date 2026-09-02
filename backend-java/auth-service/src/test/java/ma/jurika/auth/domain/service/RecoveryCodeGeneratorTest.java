package ma.jurika.auth.domain.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodeGeneratorTest {

    private final RecoveryCodeGenerator generator = new RecoveryCodeGenerator();

    @RepeatedTest(20)
    void generatesExpectedFormat() {
        String code = generator.generateOne();
        // 4 groupes de 4 chars alphanumeriques separes par '-'
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
    }

    @Test
    void avoidsAmbiguousChars() {
        String code = generator.generateOne();
        assertThat(code).doesNotContain("0").doesNotContain("1")
                        .doesNotContain("O").doesNotContain("I");
    }

    @Test
    void generatesUniqueCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(generator.generateOne());
        }
        // 100 codes generes : collision tres improbable (31^16 combinations)
        assertThat(codes).hasSize(100);
    }
}

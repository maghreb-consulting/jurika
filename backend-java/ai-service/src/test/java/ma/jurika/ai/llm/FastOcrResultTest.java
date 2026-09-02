package ma.jurika.ai.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FastOcrResultTest {

    @Test
    void degraded_factory_setsFlagsAndText() {
        FastOcrResult r = FastOcrResult.degraded("doctr", "down");
        assertThat(r.degraded()).isTrue();
        assertThat(r.text()).isEmpty();
        assertThat(r.engine()).isEqualTo("doctr");
        assertThat(r.warning()).isEqualTo("down");
        assertThat(r.hasText()).isFalse();
    }

    @Test
    void hasText_requiresAtLeastThreeChars() {
        assertThat(new FastOcrResult("ab", "x", 0.9, 100, false, null).hasText()).isFalse();
        assertThat(new FastOcrResult("   ", "x", 0.9, 100, false, null).hasText()).isFalse();
        assertThat(new FastOcrResult("CIN", "x", 0.9, 100, false, null).hasText()).isTrue();
    }

    @Test
    void hasText_isFalseWhenDegraded() {
        assertThat(new FastOcrResult("PLENTY OF TEXT", "x", 0.9, 100, true, "down").hasText())
                .isFalse();
    }

    @Test
    void confidence_isClampedToZeroOne() {
        assertThat(new FastOcrResult("x", "e", -0.5, 0, false, null).confidence()).isEqualTo(0.0);
        assertThat(new FastOcrResult("x", "e", 2.0, 0, false, null).confidence()).isEqualTo(1.0);
        assertThat(new FastOcrResult("x", "e", 0.42, 0, false, null).confidence()).isEqualTo(0.42);
    }

    @Test
    void negativeMs_isClampedToZero() {
        assertThat(new FastOcrResult("x", "e", 0.0, -1, false, null).ms()).isEqualTo(0);
    }

    @Test
    void nullEngine_defaultsToUnknown() {
        assertThat(new FastOcrResult("x", null, 0.0, 0, false, null).engine()).isEqualTo("unknown");
    }

    @Test
    void nullText_defaultsToEmpty() {
        assertThat(new FastOcrResult(null, "e", 0.0, 0, false, null).text()).isEmpty();
    }
}

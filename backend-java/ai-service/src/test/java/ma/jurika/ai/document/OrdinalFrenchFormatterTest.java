package ma.jurika.ai.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrdinalFrenchFormatterTest {

    private final OrdinalFrenchFormatter formatter = new OrdinalFrenchFormatter();

    @Test
    void ordinal_1_returns_PREMIERE() {
        assertEquals("PREMIÈRE", formatter.ordinal(1));
    }

    @Test
    void ordinal_2_returns_DEUXIEME() {
        assertEquals("DEUXIÈME", formatter.ordinal(2));
    }

    @Test
    void ordinal_3_returns_TROISIEME() {
        assertEquals("TROISIÈME", formatter.ordinal(3));
    }

    @Test
    void ordinal_4_returns_QUATRIEME() {
        assertEquals("QUATRIÈME", formatter.ordinal(4));
    }

    @Test
    void ordinal_10_returns_DIXIEME() {
        assertEquals("DIXIÈME", formatter.ordinal(10));
    }

    @Test
    void ordinal_17_returns_DIX_SEPTIEME() {
        assertEquals("DIX-SEPTIÈME", formatter.ordinal(17));
    }

    @Test
    void ordinal_20_returns_VINGTIEME() {
        assertEquals("VINGTIÈME", formatter.ordinal(20));
    }

    @Test
    void ordinal_21_returns_21EME_fallback() {
        assertEquals("21ÈME", formatter.ordinal(21));
    }

    @Test
    void ordinal_100_returns_100EME_fallback() {
        assertEquals("100ÈME", formatter.ordinal(100));
    }

    @Test
    void ordinal_0_throws_IllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> formatter.ordinal(0));
    }

    @Test
    void ordinal_negative_throws_IllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> formatter.ordinal(-1));
    }

    @Test
    void static_feminineOrdinal_works() {
        assertEquals("PREMIÈRE", OrdinalFrenchFormatter.feminineOrdinal(1));
        assertEquals("VINGTIÈME", OrdinalFrenchFormatter.feminineOrdinal(20));
    }

    @Test
    void all_1_to_20_have_distinct_values() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 1; i <= 20; i++) {
            String v = formatter.ordinal(i);
            assertNotNull(v, "ordinal(" + i + ") null");
            assertTrue(seen.add(v), "duplicate value for " + i + " : " + v);
        }
    }
}

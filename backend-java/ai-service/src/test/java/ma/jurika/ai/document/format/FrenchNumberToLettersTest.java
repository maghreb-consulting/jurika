package ma.jurika.ai.document.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrenchNumberToLettersTest {

    @Test
    void zero() {
        assertEquals("ZÉRO", FrenchNumberToLetters.numberToLetters(0));
    }

    @Test
    void one() {
        assertEquals("UN", FrenchNumberToLetters.numberToLetters(1));
    }

    @Test
    void seventeen() {
        assertEquals("DIX-SEPT", FrenchNumberToLetters.numberToLetters(17));
    }

    @Test
    void twentyOne() {
        assertEquals("VINGT ET UN", FrenchNumberToLetters.numberToLetters(21));
    }

    @Test
    void eighty() {
        assertEquals("QUATRE-VINGTS", FrenchNumberToLetters.numberToLetters(80));
    }

    @Test
    void eightyOne() {
        assertEquals("QUATRE-VINGT-UN", FrenchNumberToLetters.numberToLetters(81));
    }

    @Test
    void hundred() {
        assertEquals("CENT", FrenchNumberToLetters.numberToLetters(100));
    }

    @Test
    void twoHundred() {
        assertEquals("DEUX CENTS", FrenchNumberToLetters.numberToLetters(200));
    }

    @Test
    void thousand() {
        assertEquals("MILLE", FrenchNumberToLetters.numberToLetters(1000));
    }

    @Test
    void eightyThousand() {
        // QUATRE-VINGT sans 's' devant un numéral (MILLE)
        assertEquals("QUATRE-VINGT MILLE", FrenchNumberToLetters.numberToLetters(80_000));
    }

    @Test
    void eightyThousandOne() {
        assertEquals("QUATRE-VINGT MILLE UN", FrenchNumberToLetters.numberToLetters(80_001));
    }

    @Test
    void hundredThousand() {
        assertEquals("CENT MILLE", FrenchNumberToLetters.numberToLetters(100_000));
    }

    @Test
    void oneMillion() {
        assertEquals("UN MILLION", FrenchNumberToLetters.numberToLetters(1_000_000));
    }

    @Test
    void madHundredThousand() {
        assertEquals("CENT MILLE DIRHAMS", FrenchNumberToLetters.madToLetters(100_000));
    }
}

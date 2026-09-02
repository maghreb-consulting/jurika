package ma.jurika.ai.document.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FrenchDateToLettersTest {

    @Test
    void dateThirdJune2026() {
        assertEquals(
                "TROIS JUIN DEUX MILLE VINGT-SIX",
                FrenchDateToLetters.dateToLetters(LocalDate.of(2026, 6, 3)));
    }

    @Test
    void datePremierJanvier2026() {
        assertEquals(
                "PREMIER JANVIER DEUX MILLE VINGT-SIX",
                FrenchDateToLetters.dateToLetters(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void year2026() {
        assertEquals("DEUX MILLE VINGT-SIX", FrenchDateToLetters.yearToLetters(2026));
    }
}

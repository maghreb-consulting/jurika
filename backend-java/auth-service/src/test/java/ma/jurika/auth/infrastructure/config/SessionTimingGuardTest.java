package ma.jurika.auth.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garde-fou config : l'invariant inactivity-window >= access-ttl + marge est
 * verifie au demarrage. Empeche de reproduire le bug "deconnecte alors qu'actif".
 */
class SessionTimingGuardTest {

    @Test
    void acceptsWindowComfortablyAboveAccessTtl() {
        // access 15m, window 30m => 30m >= 15m + 5m : OK.
        assertThatCode(() -> SessionTimingGuard.validate(
                Duration.ofMinutes(15), Duration.ofMinutes(30)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyAtMarginBoundary() {
        // window == access + marge exacte : accepte (borne inclusive).
        assertThatCode(() -> SessionTimingGuard.validate(
                Duration.ofMinutes(15), Duration.ofMinutes(20)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWindowShorterThanAccessTtl() {
        // Le cas du bug : access 8h, window 30m => refus de demarrer.
        assertThatThrownBy(() -> SessionTimingGuard.validate(
                Duration.ofHours(8), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactivity-window");
    }

    @Test
    void rejectsWindowWithinMarginOfAccessTtl() {
        // window > access mais marge insuffisante (access 15m, window 18m < 20m).
        assertThatThrownBy(() -> SessionTimingGuard.validate(
                Duration.ofMinutes(15), Duration.ofMinutes(18)))
                .isInstanceOf(IllegalStateException.class);
    }
}

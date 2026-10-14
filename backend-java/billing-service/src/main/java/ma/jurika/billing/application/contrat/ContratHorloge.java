package ma.jurika.billing.application.contrat;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Lot L7 (E6) : horloge du contrat, a l'heure de Casablanca (RG-CTR-02). Remplacable en test.
 */
@Component
public class ContratHorloge {

    public static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private volatile Clock clock = Clock.system(CASABLANCA);

    public Instant maintenant() {
        return Instant.now(clock);
    }

    public LocalDate aujourdhui() {
        return LocalDate.now(clock.withZone(CASABLANCA));
    }

    /** Tests seulement. */
    public void regler(Clock nouvelle) {
        this.clock = nouvelle;
    }
}

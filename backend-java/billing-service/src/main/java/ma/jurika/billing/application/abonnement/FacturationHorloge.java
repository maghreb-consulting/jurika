package ma.jurika.billing.application.abonnement;

import ma.jurika.common.billing.PeriodeAbonnement;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lot L7 (E8) : horloge de l'abonnement (echeances, rappel, suspension), a l'heure de Casablanca.
 * Remplacable en test pour derouler la sequence J0, J+14, J+15 sans attendre.
 */
@Component
public class FacturationHorloge {

    private volatile Clock clock = Clock.systemUTC();

    public Instant maintenant() {
        return clock.instant();
    }

    public LocalDate aujourdhui() {
        return LocalDate.ofInstant(clock.instant(), PeriodeAbonnement.CASABLANCA);
    }

    /** Date (Casablanca) d'un instant. */
    public static LocalDate jour(Instant i) {
        return i == null ? null : LocalDate.ofInstant(i, PeriodeAbonnement.CASABLANCA);
    }

    /** Tests seulement. */
    public void regler(Clock nouvelle) {
        this.clock = nouvelle;
    }
}

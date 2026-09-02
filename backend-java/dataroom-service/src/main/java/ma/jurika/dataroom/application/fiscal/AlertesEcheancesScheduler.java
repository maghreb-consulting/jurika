package ma.jurika.dataroom.application.fiscal;

import ma.jurika.dataroom.application.DossierArchiveGuard;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * RG-DF20 -- Scheduler quotidien 7h Casablanca : depile les alertes
 * dont date_alerte = today et statut = PLANIFIEE, les dispatche
 * (email + WebSocket) et passe le statut a ENVOYEE.
 *
 * Clock injectable pour tests (Spring choisit le bean par defaut Clock.systemUTC).
 */
@Component
public class AlertesEcheancesScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertesEcheancesScheduler.class);
    private static final ZoneId ZONE_MAROC = ZoneId.of("Africa/Casablanca");

    private final AlerteEcheanceJpaRepository repo;
    private final EcheanceDispatcher dispatcher;
    private final Clock clock;

    public AlertesEcheancesScheduler(AlerteEcheanceJpaRepository repo,
                                       EcheanceDispatcher dispatcher,
                                       Clock clock) {
        this.repo = repo;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void runDailyAlertes() {
        runForDate(LocalDate.now(clock.withZone(ZONE_MAROC)));
    }

    /** Methode utilitaire pour tests : force la date du run. */
    @Transactional
    public int runForDate(LocalDate today) {
        // Lot DIVERS §A (2026-08-13) — les societes dissoutes / en liquidation /
        // liquidees / radiees ne declarent plus : leurs echeances fiscales sont
        // suspendues (ni depilees, ni notifiees). Filtre porte par la requete pour
        // ne pas charger inutilement des lignes qu'on ignorerait ensuite.
        List<AlerteEcheanceEntity> due = repo.findDueForActiveDossiers(
                today, "PLANIFIEE", DossierArchiveGuard.ARCHIVED_STATUS);
        for (AlerteEcheanceEntity a : due) {
            try {
                dispatcher.dispatch(a);
            } catch (Exception ex) {
                log.warn("Echec dispatch alerte {} : {}", a.getId(), ex.getMessage());
            }
            a.setStatut("ENVOYEE");
            a.setSentAt(Instant.now());
        }
        if (!due.isEmpty()) {
            repo.saveAll(due);
            log.info("Alertes echeances J-15 dispatched : {} pour {}", due.size(), today);
        }
        return due.size();
    }
}

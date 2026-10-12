package ma.jurika.ticket.infrastructure;

import ma.jurika.ticket.application.AlertesDelaisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lot L5 (E9, RG-COM-08) : detection planifiee des delais a echeance. Une fois
 * par jour (07:00, heure de Casablanca, avant l'ouverture du cabinet) ; un
 * delai legal se compte en jours, une detection plus frequente n'apporterait
 * rien. Desactivable par {@code jurika.notifications.delais.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "jurika.notifications.delais.enabled", havingValue = "true", matchIfMissing = true)
public class AlertesDelaisJob {

    private static final Logger log = LoggerFactory.getLogger(AlertesDelaisJob.class);

    private final AlertesDelaisService service;

    public AlertesDelaisJob(AlertesDelaisService service) {
        this.service = service;
    }

    @Scheduled(cron = "${jurika.notifications.delais.cron:0 0 7 * * *}", zone = "Africa/Casablanca")
    public void executer() {
        try {
            service.detecter();
        } catch (Exception ex) {
            log.error("Detection des delais a echeance en echec : {}", ex.getMessage(), ex);
        }
    }
}

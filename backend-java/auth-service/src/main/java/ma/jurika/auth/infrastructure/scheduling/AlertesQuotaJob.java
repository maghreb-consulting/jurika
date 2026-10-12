package ma.jurika.auth.infrastructure.scheduling;

import ma.jurika.auth.application.AlertesQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lot L5 (E9, RG-COM-08 et RG-PLN-02) : detection planifiee du quota approchant,
 * une fois par jour a 07:00 (heure de Casablanca). Desactivable par
 * {@code jurika.notifications.quota.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "jurika.notifications.quota.enabled", havingValue = "true", matchIfMissing = true)
public class AlertesQuotaJob {

    private static final Logger log = LoggerFactory.getLogger(AlertesQuotaJob.class);

    private final AlertesQuotaService service;

    public AlertesQuotaJob(AlertesQuotaService service) {
        this.service = service;
    }

    @Scheduled(cron = "${jurika.notifications.quota.cron:0 0 7 * * *}", zone = "Africa/Casablanca")
    public void executer() {
        try {
            service.detecter();
        } catch (Exception ex) {
            log.error("Detection du quota approchant en echec : {}", ex.getMessage(), ex);
        }
    }
}

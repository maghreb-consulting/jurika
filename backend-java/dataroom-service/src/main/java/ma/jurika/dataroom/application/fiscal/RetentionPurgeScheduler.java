package ma.jurika.dataroom.application.fiscal;

import ma.jurika.dataroom.application.DataroomFiscalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RG-DF18 + CGI Art. 211 -- job de purge des documents fiscaux soft-deleted
 * dont la date de creation depasse 10 ans + 3 jours de marge.
 * <p>
 * Execution : dimanche 3h Casablanca.
 */
@Component
public class RetentionPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeScheduler.class);

    private final DataroomFiscalService fiscal;

    public RetentionPurgeScheduler(DataroomFiscalService fiscal) {
        this.fiscal = fiscal;
    }

    @Scheduled(cron = "0 0 3 * * SUN", zone = "Africa/Casablanca")
    public void purge() {
        try {
            int n = fiscal.purgeExpiredDocuments();
            if (n > 0) log.info("Retention 10 ans -- {} documents fiscaux purges", n);
        } catch (Exception ex) {
            log.error("Retention purge KO : {}", ex.getMessage(), ex);
        }
    }
}

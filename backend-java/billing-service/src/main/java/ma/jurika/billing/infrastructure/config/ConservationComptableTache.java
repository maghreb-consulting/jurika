package ma.jurika.billing.infrastructure.config;

import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService;
import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService.BilanPurge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lot L7 (E14) : purge des pieces comptables d'un essai supprime, au terme des 10 ans de
 * conservation (registre {@code conservation_comptable}, billing V18). Chaque jour a 02:00
 * (Casablanca) ; la fonction de la base refuse toute purge avant l'echeance. Chaque purge est
 * tracee dans la ligne du registre ; un echec reste en attente (repris au passage suivant) et
 * se lit dans le journal. Coupee par {@code jurika.billing.taches.enabled=false} (tests).
 */
@Component
@ConditionalOnProperty(name = "jurika.billing.taches.enabled", havingValue = "true", matchIfMissing = true)
public class ConservationComptableTache {

    private static final Logger log = LoggerFactory.getLogger(ConservationComptableTache.class);

    private final SuppressionFacturationEssaiService service;

    public ConservationComptableTache(SuppressionFacturationEssaiService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Casablanca")
    public void purger() {
        BilanPurge b = service.purgerEcheancesPassees();
        if (b.traites() == 0) return;
        if (b.echecs() > 0) {
            log.warn("Purge de la conservation comptable : {} traite(s), {} reussi(s), {} en echec : {}",
                    b.traites(), b.reussis(), b.echecs(), b.details());
        } else {
            log.info("Purge de la conservation comptable : {} cabinet(s) purge(s)", b.reussis());
        }
    }
}

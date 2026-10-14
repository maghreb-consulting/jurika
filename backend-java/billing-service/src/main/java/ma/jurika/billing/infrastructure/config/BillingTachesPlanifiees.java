package ma.jurika.billing.infrastructure.config;

import ma.jurika.billing.api.dto.BillingDtos.BilanTacheVue;
import ma.jurika.billing.application.AdministrationFacturationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lot L7 (E5) : taches planifiees de la facturation. Chaque passage est journalise quand il a fait
 * ou rate quelque chose ; l'etat a rejouer reste en base (visible du super-admin, qui peut aussi
 * relancer chaque tache a la main).
 *
 * <ul>
 *   <li>toutes les 5 minutes : activations non propagees a auth-service, factures en attente d'une
 *       mention ;</li>
 *   <li>chaque jour a 01:00 (Casablanca) : echeances depassees (lot L7, E8, article 4.4 du
 *       contrat) : rappel, suspension a J+15 apres rappel, archivage a l'arret du forfait.</li>
 * </ul>
 * Coupees par {@code jurika.billing.taches.enabled=false} (tests).
 */
@Component
@ConditionalOnProperty(name = "jurika.billing.taches.enabled", havingValue = "true", matchIfMissing = true)
public class BillingTachesPlanifiees {

    private static final Logger log = LoggerFactory.getLogger(BillingTachesPlanifiees.class);

    private final AdministrationFacturationService admin;

    public BillingTachesPlanifiees(AdministrationFacturationService admin) {
        this.admin = admin;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void rattrapages() {
        journal("Propagation des activations", admin.propagerActivations());
        journal("Factures en attente", admin.emettreFacturesEnAttente());
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Casablanca")
    public void archivage() {
        journal("Echeances depassees (rappel, suspension, archivage)", admin.archiver());
    }

    private static void journal(String tache, BilanTacheVue b) {
        if (b.traites() == 0) return;
        if (b.echecs() > 0) {
            log.warn("{} : {} traite(s), {} reussi(s), {} en attente : {}", tache, b.traites(), b.reussis(), b.echecs(), b.details());
        } else {
            log.info("{} : {} traite(s), {} reussi(s)", tache, b.traites(), b.reussis());
        }
    }
}

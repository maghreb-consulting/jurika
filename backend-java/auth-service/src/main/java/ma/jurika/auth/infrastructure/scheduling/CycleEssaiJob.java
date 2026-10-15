package ma.jurika.auth.infrastructure.scheduling;

import ma.jurika.auth.application.essai.CycleEssaiService;
import ma.jurika.auth.infrastructure.essai.EssaiJdbc;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-10 : passage planifie du cycle de l'essai (toutes les
 * heures par defaut) : fin des 7 jours, avertissement, suppression definitive
 * au terme des 30 jours de lecture seule ; purge des demandes perimees.
 * Chaque essai est traite dans son propre workspace et sa propre transaction :
 * un echec n'arrete pas les autres.
 */
@Component
@ConditionalOnProperty(name = "jurika.essai.cycle.enabled", havingValue = "true", matchIfMissing = true)
public class CycleEssaiJob {

    private static final Logger log = LoggerFactory.getLogger(CycleEssaiJob.class);

    private final CycleEssaiService cycle;
    private final EssaiJdbc essais;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public CycleEssaiJob(CycleEssaiService cycle, EssaiJdbc essais, PlatformTransactionManager transactions,
                         Clock horloge) {
        this.cycle = cycle;
        this.essais = essais;
        this.transaction = new TransactionTemplate(transactions);
        this.horloge = horloge;
    }

    public record Bilan(int essais, int supprimes, int erreurs) {}

    @Scheduled(cron = "${jurika.essai.cycle.cron:0 15 * * * *}", zone = "Africa/Casablanca")
    public void executer() {
        try {
            executerUnPassage();
        } catch (Exception ex) {
            log.error("Cycle de l'essai gratuit en echec : {}", ex.getMessage(), ex);
        }
    }

    public Bilan executerUnPassage() {
        TenantContext.clear();
        Integer purgees = transaction.execute(s -> essais.purgerDemandes(horloge.instant()));
        if (purgees != null && purgees > 0) {
            log.info("Cycle de l'essai : {} demande(s) d'essai purgee(s)", purgees);
        }
        List<UUID> aTraiter = transaction.execute(s -> essais.workspacesATraiter());
        int n = 0;
        int supprimes = 0;
        int erreurs = 0;
        for (UUID ws : aTraiter == null ? List.<UUID>of() : aTraiter) {
            n++;
            try {
                TenantContext.set(ws);
                if (cycle.traiter(ws) == CycleEssaiService.Issue.A_SUPPRIMER) {
                    cycle.supprimer(ws);
                    supprimes++;
                }
            } catch (Exception ex) {
                erreurs++;
                log.error("Cycle de l'essai : cabinet {} non traite : {}", ws, ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        return new Bilan(n, supprimes, erreurs);
    }
}

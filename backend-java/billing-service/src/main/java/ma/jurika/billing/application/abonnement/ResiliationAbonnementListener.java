package ma.jurika.billing.application.abonnement;

import ma.jurika.billing.application.contrat.ContratEvenements;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lot L7 (integration E5 / E6) : une resiliation du contrat (RG-CTR-08) arrete le forfait a sa
 * date d'effet. L'abonnement actif n'est plus renouvele et sa periode payee est bornee a cette
 * date ; la tache quotidienne ({@link ImpayeService#traiterEcheances}) archive alors le cabinet
 * avec le motif RESILIATION (RG-TAR-07 : jamais de suppression), A LA DATE D'EFFET (lot L7, E8 :
 * une date d'effet posterieure a la periode payee, cas de la resiliation pour defaut de paiement
 * persistant de l'article 12.2, laisse courir l'impaye et sa suspension jusqu'a cette date). Sans
 * abonnement actif, rien a arreter : le fait est journalise.
 */
@Component
public class ResiliationAbonnementListener {

    private static final Logger log = LoggerFactory.getLogger(ResiliationAbonnementListener.class);

    private final SubscriptionJpaRepository abonnements;
    private final TransactionTemplate tx;

    public ResiliationAbonnementListener(SubscriptionJpaRepository abonnements, PlatformTransactionManager tm) {
        this.abonnements = abonnements;
        this.tx = new TransactionTemplate(tm);
    }

    @EventListener
    public void surResiliation(ContratEvenements.ResiliationEnregistree e) {
        boolean fait = Boolean.TRUE.equals(tx.execute(st -> abonnements.verrouillerActif(e.workspaceId()).map(s -> {
            s.setCancelAtPeriodEnd(true);
            s.setResiliationEffet(e.dateEffet());
            if (s.getPeriodeFin() == null || e.dateEffet().isBefore(s.getPeriodeFin())) {
                s.periode(s.getPeriodeDebut(), e.dateEffet());
            }
            abonnements.save(s);
            return true;
        }).orElse(false)));
        if (fait) {
            log.info("Resiliation : forfait arrete au {} workspace={}", e.dateEffet(), e.workspaceId());
        } else {
            log.warn("Resiliation sans abonnement actif a arreter workspace={} effet={}", e.workspaceId(), e.dateEffet());
        }
    }
}

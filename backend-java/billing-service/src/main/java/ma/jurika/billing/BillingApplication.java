package ma.jurika.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Sprint 12 — billing-service (port 8090).
 *
 * <p>Responsabilites (lot L7, Stripe retire) :
 *  - forfaits du CDC 4.3, quotas RG-PLN, changement de forfait RG-TAR ;
 *  - paiement : carte par la page hebergee du CMI (retour serveur signe verifie),
 *    virement, cheque et especes enregistres par le super-admin (RG-PAY) ;
 *  - factures numerotees sans trou (RG-PAY-03), TVA au taux des parametres de l'editeur ;
 *  - contrat d'abonnement (RG-CTR) et parametres de l'editeur ;
 *  - persistance sur la base dediee {@code jurika_billing} (decision archi #4) ;
 *  - courriels transactionnels billing.
 *
 * <p>Pas d'integration Spring AI ici (pas d'IA pour le billing).
 */
@SpringBootApplication(scanBasePackages = {"ma.jurika.billing", "ma.jurika.common"})
// Sprint 12 fix — BillingRepositories nest 4 interfaces JpaRepository (cf.
// BillingJpaConfig avec considerNestedRepositories=true). @EnableJpaRepositories
// a ete deplace hors de cette classe applicative (Lot J3) pour ne pas casser les
// slices @WebMvcTest qui ne demarrent pas JPA.
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}

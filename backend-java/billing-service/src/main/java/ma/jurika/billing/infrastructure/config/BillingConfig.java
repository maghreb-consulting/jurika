package ma.jurika.billing.infrastructure.config;

import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;

/**
 * Lot L7 (E5) : proprietes de la facturation et du CMI, taches planifiees.
 *
 * <p>Garde-fou : le simulateur du CMI ({@code CMI_SIMULATEUR_ACTIF=true}) refuse de demarrer dans
 * un environnement de production ({@code JURIKA_ENV} = prod ou production) : il signerait de faux
 * retours avec la cle du magasin.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({BillingProperties.class, CmiProperties.class})
public class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);
    private static final Set<String> PRODUCTION = Set.of("prod", "production");

    private final CmiProperties cmi;
    private final String environnement;

    public BillingConfig(CmiProperties cmi, @Value("${jurika.env:local}") String environnement) {
        this.cmi = cmi;
        this.environnement = environnement;
    }

    @PostConstruct
    void verifier() {
        if (cmi.isSimulateurActif() && PRODUCTION.contains(environnement.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Simulateur du CMI actif en production (JURIKA_ENV=" + environnement
                    + ") : refuse. Retirer CMI_SIMULATEUR_ACTIF.");
        }
        if (cmi.configure()) {
            log.info("Paiement par carte : CMI configure ({}){}", cmi.getGatewayUrl(),
                    cmi.isSimulateurActif() ? " -- SIMULATEUR ACTIF (aucun paiement reel)" : "");
        } else {
            log.warn("Paiement par carte indisponible : parametres du CMI absents {}", cmi.manquants());
        }
    }
}

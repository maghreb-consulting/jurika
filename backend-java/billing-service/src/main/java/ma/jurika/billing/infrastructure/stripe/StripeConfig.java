package ma.jurika.billing.infrastructure.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 12 — initialise le SDK Stripe Java au demarrage.
 *
 * <p>Pose {@code Stripe.apiKey} (variable globale du SDK) a partir de la
 * propriete {@code jurika.stripe.secret-key}. C'est la convention Stripe :
 * pas d'injection per-client.
 *
 * <p>Garde-fou : si la cle commence par {@code sk_live_}, on log un WARN
 * tonitruant — en TEST mode uniquement pour Sprint 12 (PLAN contrainte
 * absolue).
 */
@Configuration
@EnableConfigurationProperties({StripeProperties.class, BillingProperties.class})
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    private final StripeProperties stripeProperties;

    public StripeConfig(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostConstruct
    public void initStripeSdk() {
        String secretKey = stripeProperties.getSecretKey();
        Stripe.apiKey = secretKey;
        Stripe.setAppInfo("JURIKA", "1.0.0", "https://jurika.ai");

        if (secretKey == null) {
            log.error("STRIPE_SECRET_KEY non definie — les appels Stripe vont echouer.");
            return;
        }
        if (secretKey.startsWith("sk_live_")) {
            log.warn("==========================================================");
            log.warn(" /!\\ STRIPE EN MODE PRODUCTION (sk_live_*) — Sprint 12 ");
            log.warn("     ne supporte que le TEST mode. Verifie ta config.    ");
            log.warn("==========================================================");
        } else if (secretKey.contains("PLACEHOLDER")) {
            log.warn("Stripe SDK initialise avec un PLACEHOLDER — aucun appel " +
                     "Stripe reel ne reussira. Remplace STRIPE_SECRET_KEY dans .env.local.");
        } else {
            log.info("Stripe SDK initialise (TEST mode, prefix={}).",
                    secretKey.length() >= 7 ? secretKey.substring(0, 7) : "?");
        }
    }
}

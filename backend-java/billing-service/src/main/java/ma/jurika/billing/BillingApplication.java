package ma.jurika.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Sprint 12 — billing-service (port 8090).
 *
 * <p>Responsabilites :
 *  - Integration Stripe Checkout (hosted) + Customer Portal (RG-BL03/RG-BL09)
 *  - Webhooks Stripe avec signature HMAC (RG-BL04..07)
 *  - TVA Maroc 20% calculee backend (RG-BL12, risque archi #4)
 *  - Persistance des subscriptions/invoices/payment_methods sur base
 *    PostgreSQL dediee {@code jurika_billing} (decision archi #4)
 *  - Email transactionnels billing (RG-BL05, RG-BL06, RG-BL16)
 *
 * <p>SubscribeUseCase compose CreateStripeCustomerUseCase +
 * CreateCheckoutSessionUseCase + RecordSubscriptionUseCase
 * (RG-BL17 pattern SignupCabinetUseCase Sprint 11).
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

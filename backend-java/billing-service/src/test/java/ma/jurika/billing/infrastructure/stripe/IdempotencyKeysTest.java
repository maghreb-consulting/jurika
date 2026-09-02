package ma.jurika.billing.infrastructure.stripe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 12 — verifie la stabilite des cles d'idempotence (RG-BL18).
 */
class IdempotencyKeysTest {

    private static final UUID WS = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void forCreateCustomer_isStable_perWorkspace() {
        String key1 = IdempotencyKeys.forCreateCustomer(WS);
        String key2 = IdempotencyKeys.forCreateCustomer(WS);
        assertThat(key1).isEqualTo(key2);
        assertThat(key1).startsWith("jurika_customer_");
        assertThat(key1).hasSize("jurika_customer_".length() + 16);
    }

    @Test
    void forCreateCustomer_differs_perWorkspace() {
        UUID other = UUID.fromString("99999999-8888-7777-6666-555555555555");
        assertThat(IdempotencyKeys.forCreateCustomer(WS))
                .isNotEqualTo(IdempotencyKeys.forCreateCustomer(other));
    }

    @Test
    void forCheckoutSession_includesPlanCode() {
        String essentielKey = IdempotencyKeys.forCheckoutSession(WS, "essentiel");
        String businessKey = IdempotencyKeys.forCheckoutSession(WS, "business");
        assertThat(essentielKey).startsWith("jurika_checkout_");
        assertThat(essentielKey).isNotEqualTo(businessKey);
    }

    @Test
    void forCustomerPortal_includesHourBucket() {
        String key = IdempotencyKeys.forCustomerPortal(WS);
        assertThat(key).startsWith("jurika_portal_");
    }
}

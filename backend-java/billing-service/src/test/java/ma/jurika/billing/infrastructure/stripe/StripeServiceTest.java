package ma.jurika.billing.infrastructure.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Sprint 12 — tests unitaires du StripeService.
 *
 * <p>Le SDK Stripe utilise des methodes statiques (Customer.create, etc.).
 * Mockito 5 (embarque avec Spring Boot 3.4) supporte le static mocking
 * via {@code mockStatic} sans dep additionnelle.
 */
class StripeServiceTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private StripeProperties props;
    private StripeService service;

    @BeforeEach
    void setUp() {
        props = new StripeProperties();
        props.setPublishableKey("pk_test_xxx");
        props.setSecretKey("sk_test_xxx");
        props.setWebhookSecret("whsec_xxx");
        StripeProperties.Prices prices = new StripeProperties.Prices();
        // Sprint Beta (pricing-deploy) — Prices passe a une structure nested
        // (monthly/yearly par plan). Pour ces tests, mensuel suffit (defaut).
        StripeProperties.PlanPrices essentiel = new StripeProperties.PlanPrices();
        essentiel.setMonthly("price_essentiel_xxx");
        essentiel.setYearly("price_essentiel_yearly_xxx");
        prices.setEssentiel(essentiel);
        StripeProperties.PlanPrices business = new StripeProperties.PlanPrices();
        business.setMonthly("price_business_xxx");
        business.setYearly("price_business_yearly_xxx");
        prices.setBusiness(business);
        props.setPrices(prices);
        service = new StripeService(props);
    }

    @Test
    void createCustomer_passesIdempotencyKey_andMetadata() throws StripeException {
        try (MockedStatic<Customer> mocked = mockStatic(Customer.class)) {
            Customer expected = new Customer();
            expected.setId("cus_test_123");

            ArgumentCaptor<CustomerCreateParams> paramsCap = ArgumentCaptor.forClass(CustomerCreateParams.class);
            ArgumentCaptor<RequestOptions> optsCap = ArgumentCaptor.forClass(RequestOptions.class);

            mocked.when(() -> Customer.create(paramsCap.capture(), optsCap.capture()))
                    .thenReturn(expected);

            Customer result = service.createCustomer(WORKSPACE_ID, "ops@cabinet-X.ma", "Cabinet X");

            assertThat(result.getId()).isEqualTo("cus_test_123");
            // Idempotency key stable pour ce workspace
            assertThat(optsCap.getValue().getIdempotencyKey())
                    .isEqualTo(IdempotencyKeys.forCreateCustomer(WORKSPACE_ID));
            // Metadata workspace_id transmis (necessaire pour matching webhook handler).
            // CustomerCreateParams.getMetadata() retourne Object dans stripe-java 28.x
            // (cf. type erasure des params builder) — cast explicite.
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> metadata =
                    (java.util.Map<String, String>) paramsCap.getValue().getMetadata();
            assertThat(metadata).containsEntry("workspace_id", WORKSPACE_ID.toString());
        }
    }

    @Test
    void resolvePriceId_returnsConfiguredId_forKnownPlans() {
        assertThat(props.resolvePriceId("essentiel")).isEqualTo("price_essentiel_xxx");
        assertThat(props.resolvePriceId("business")).isEqualTo("price_business_xxx");
    }

    @Test
    void resolvePriceId_rejectsEnterprise_perRGBL10() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> props.resolvePriceId("enterprise"));
    }

    @Test
    void resolvePriceId_rejectsUnknownPlan() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> props.resolvePriceId("premium-gold-platinum"));
    }

    @Test
    void getWebhookSecret_returnsConfiguredValue() {
        assertThat(service.getWebhookSecret()).isEqualTo("whsec_xxx");
    }
}

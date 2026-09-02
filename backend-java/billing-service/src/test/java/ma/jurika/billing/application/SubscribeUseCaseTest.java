package ma.jurika.billing.application;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import ma.jurika.billing.infrastructure.stripe.StripeProperties;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import ma.jurika.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 12 — tests unit pour SubscribeUseCase (composition RG-BL17).
 */
class SubscribeUseCaseTest {

    private static final UUID WS = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private StripeService stripeService;
    private StripeProperties stripeProps;
    private BillingProperties billingProps;
    private SubscriptionJpaRepository subscriptionRepo;
    private SubscribeUseCase useCase;

    @BeforeEach
    void setUp() {
        stripeService = mock(StripeService.class);
        subscriptionRepo = mock(SubscriptionJpaRepository.class);

        stripeProps = new StripeProperties();
        StripeProperties.Prices prices = new StripeProperties.Prices();
        // Sprint Beta (pricing-deploy) — Prices nested monthly/yearly par plan.
        StripeProperties.PlanPrices essentiel = new StripeProperties.PlanPrices();
        essentiel.setMonthly("price_essentiel_xxx");
        essentiel.setYearly("price_essentiel_yearly_xxx");
        prices.setEssentiel(essentiel);
        StripeProperties.PlanPrices business = new StripeProperties.PlanPrices();
        business.setMonthly("price_business_xxx");
        business.setYearly("price_business_yearly_xxx");
        prices.setBusiness(business);
        stripeProps.setPrices(prices);
        stripeProps.setSecretKey("sk_test_xxx");
        stripeProps.setPublishableKey("pk_test_xxx");
        stripeProps.setWebhookSecret("whsec_xxx");

        billingProps = new BillingProperties();
        BillingProperties.Checkout co = new BillingProperties.Checkout();
        co.setSuccessUrl("https://app.test/billing/success?s={CHECKOUT_SESSION_ID}");
        co.setCancelUrl("https://app.test/billing");
        billingProps.setCheckout(co);

        useCase = new SubscribeUseCase(stripeService, stripeProps, billingProps, subscriptionRepo);
    }

    @Test
    void enterprise_isRejected_RGBL10() {
        assertThatThrownBy(() -> useCase.subscribe(new SubscribeUseCase.Command(
                WS, "admin@cab.ma", "Cabinet X", "enterprise")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contact-sales");
    }

    @Test
    void alreadyActive_isRejected() {
        when(subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(WS, "active"))
                .thenReturn(Optional.of(new SubscriptionEntity()));
        assertThatThrownBy(() -> useCase.subscribe(new SubscribeUseCase.Command(
                WS, "admin@cab.ma", "Cabinet X", "essentiel")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Customer Portal");
    }

    @Test
    void firstTime_createsCustomer_andCheckoutSession() throws StripeException {
        when(subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(WS, "active"))
                .thenReturn(Optional.empty());
        when(subscriptionRepo.findFirstByWorkspaceIdOrderByCreatedAtDesc(WS))
                .thenReturn(Optional.empty());

        Customer customer = new Customer();
        customer.setId("cus_test_001");
        when(stripeService.createCustomer(eq(WS), eq("admin@cab.ma"), eq("Cabinet X")))
                .thenReturn(customer);

        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/c/pay/cs_test_123");
        when(stripeService.createCheckoutSession(
                eq("cus_test_001"), eq("price_essentiel_xxx"), eq(WS), eq("essentiel"),
                anyString(), anyString())).thenReturn(session);

        SubscribeUseCase.Result result = useCase.subscribe(new SubscribeUseCase.Command(
                WS, "admin@cab.ma", "Cabinet X", "essentiel"));

        assertThat(result.checkoutSessionId()).isEqualTo("cs_test_123");
        assertThat(result.checkoutUrl()).startsWith("https://checkout.stripe.com/");
        assertThat(result.planCode()).isEqualTo("essentiel");
        assertThat(result.stripeCustomerId()).isEqualTo("cus_test_001");

        verify(stripeService, times(1)).createCustomer(eq(WS), anyString(), anyString());
    }

    @Test
    void existingCustomer_reusedFromHistoricSubscription() throws StripeException {
        SubscriptionEntity oldSub = new SubscriptionEntity();
        oldSub.setStripeCustomerId("cus_test_existing");
        when(subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(WS, "active"))
                .thenReturn(Optional.empty());
        when(subscriptionRepo.findFirstByWorkspaceIdOrderByCreatedAtDesc(WS))
                .thenReturn(Optional.of(oldSub));

        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_456");
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/c/pay/cs_test_456");
        when(stripeService.createCheckoutSession(
                eq("cus_test_existing"), eq("price_business_xxx"), eq(WS), eq("business"),
                anyString(), anyString())).thenReturn(session);

        SubscribeUseCase.Result result = useCase.subscribe(new SubscribeUseCase.Command(
                WS, "admin@cab.ma", "Cabinet X", "business"));

        assertThat(result.stripeCustomerId()).isEqualTo("cus_test_existing");
        // Pas de nouveau createCustomer (reuse cycle de vie 1 WS = 1 customer).
        verify(stripeService, times(0)).createCustomer(any(), anyString(), anyString());
    }

    @Test
    void invalidCommand_throws() {
        assertThatThrownBy(() -> useCase.subscribe(new SubscribeUseCase.Command(
                null, "x@x.ma", "Cab", "essentiel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.subscribe(new SubscribeUseCase.Command(
                WS, null, "Cab", "essentiel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.subscribe(new SubscribeUseCase.Command(
                WS, "x@x.ma", "Cab", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

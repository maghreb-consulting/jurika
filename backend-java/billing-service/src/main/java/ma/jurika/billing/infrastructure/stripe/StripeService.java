package ma.jurika.billing.infrastructure.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sprint 12 — facade autour du Stripe Java SDK pour billing-service.
 *
 * <p>Toutes les mutations passent un {@link RequestOptions} avec
 * {@code Idempotency-Key} (RG-BL18). Les cles sont construites par
 * {@link IdempotencyKeys} (stables pour le meme {@code (workspaceId, op)}
 * donc une double-soumission UI ne cree pas de doublon cote Stripe pendant
 * 24h).
 *
 * <p>Exceptions Stripe sont relancees brutes — le caller (UseCase) decide
 * de la traduction metier ({@code Subscription not found} → 404, etc.).
 *
 * <p>Tests : unit avec mocks Stripe SDK (cf. {@code StripeServiceTest}).
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeProperties stripeProperties;

    public StripeService(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    /**
     * Cree un customer Stripe pour un workspace. Idempotent par workspaceId.
     *
     * @param workspaceId UUID workspace JURIKA (transmit comme metadata)
     * @param email       email facturation
     * @param name        raison sociale cabinet
     * @return le Customer Stripe nouvellement cree
     */
    public Customer createCustomer(UUID workspaceId, String email, String name) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putMetadata("workspace_id", workspaceId.toString())
                .putMetadata("source", "jurika-billing-sprint12")
                .build();
        RequestOptions opts = RequestOptions.builder()
                .setIdempotencyKey(IdempotencyKeys.forCreateCustomer(workspaceId))
                .build();
        Customer customer = Customer.create(params, opts);
        log.info("Stripe customer cree workspace={} customer_id={}", workspaceId, customer.getId());
        return customer;
    }

    /**
     * Cree une Stripe Checkout Session (hosted mode) pour souscrire a un plan.
     * RG-BL03 / decision archi #2.
     *
     * @param customerId       Stripe customer ID
     * @param priceId          Stripe price ID (resolu via StripeProperties#resolvePriceId)
     * @param workspaceId      UUID workspace JURIKA (metadata pour le webhook handler)
     * @param planCode         plan code (essentiel/business)
     * @param successUrl       URL de redirection succes (contient {CHECKOUT_SESSION_ID})
     * @param cancelUrl        URL de redirection annulation
     * @return la Checkout Session (utiliser {@code getUrl()} pour rediriger l'user)
     */
    public com.stripe.model.checkout.Session createCheckoutSession(
            String customerId, String priceId, UUID workspaceId, String planCode,
            String successUrl, String cancelUrl) throws StripeException {

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setLocale(SessionCreateParams.Locale.FR)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .putMetadata("workspace_id", workspaceId.toString())
                .putMetadata("plan_code", planCode)
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("workspace_id", workspaceId.toString())
                        .putMetadata("plan_code", planCode)
                        .build())
                .build();

        RequestOptions opts = RequestOptions.builder()
                .setIdempotencyKey(IdempotencyKeys.forCheckoutSession(workspaceId, planCode))
                .build();

        com.stripe.model.checkout.Session session =
                com.stripe.model.checkout.Session.create(params, opts);
        log.info("Stripe checkout session creee workspace={} plan={} session_id={}",
                workspaceId, planCode, session.getId());
        return session;
    }

    /**
     * Cree une session de Customer Portal Stripe pour qu'un user gere son
     * abonnement (update card, cancel, resume — RG-BL09 / decision archi #3).
     */
    public Session createCustomerPortalSession(String customerId, UUID workspaceId,
                                                String returnUrl) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .setLocale(com.stripe.param.billingportal.SessionCreateParams.Locale.FR)
                        .build();
        RequestOptions opts = RequestOptions.builder()
                .setIdempotencyKey(IdempotencyKeys.forCustomerPortal(workspaceId))
                .build();
        Session session = Session.create(params, opts);
        log.info("Stripe customer portal session creee workspace={} session_id={}",
                workspaceId, session.getId());
        return session;
    }

    /** Lecture simple (idempotente, pas de key). */
    public Subscription retrieveSubscription(String stripeSubscriptionId) throws StripeException {
        return Subscription.retrieve(stripeSubscriptionId);
    }

    /**
     * BUG 8 (2026-06-07) — change le price item d'une subscription Stripe
     * existante avec proration calculee par Stripe.
     *
     * <p>Pattern Stripe : on retrouve l'item courant via subscription.items,
     * on l'update avec le nouveau price, on demande
     * {@code proration_behavior=create_prorations} pour generer la facture
     * de difference (credit ou debit). Le webhook
     * {@code customer.subscription.updated} arrive ensuite avec les nouvelles
     * dates de periode.
     *
     * @param stripeSubscriptionId la subscription a modifier
     * @param newPriceId           le nouveau price (variant period inclus)
     * @param prorationBehavior    "create_prorations" (defaut) ou "none"
     * @param workspaceId          metadata propage (used in webhook lookup)
     * @param targetPlanCode       metadata propage
     */
    public Subscription updateSubscriptionPlan(String stripeSubscriptionId,
                                                 String newPriceId,
                                                 String prorationBehavior,
                                                 UUID workspaceId,
                                                 String targetPlanCode) throws StripeException {
        Subscription sub = Subscription.retrieve(stripeSubscriptionId);
        if (sub.getItems() == null || sub.getItems().getData().isEmpty()) {
            throw new IllegalStateException("Subscription " + stripeSubscriptionId + " sans item — non modifiable");
        }
        String itemId = sub.getItems().getData().get(0).getId();

        com.stripe.param.SubscriptionUpdateParams params =
                com.stripe.param.SubscriptionUpdateParams.builder()
                        .addItem(com.stripe.param.SubscriptionUpdateParams.Item.builder()
                                .setId(itemId)
                                .setPrice(newPriceId)
                                .build())
                        .setProrationBehavior(
                                "none".equalsIgnoreCase(prorationBehavior)
                                        ? com.stripe.param.SubscriptionUpdateParams.ProrationBehavior.NONE
                                        : com.stripe.param.SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                        .putMetadata("workspace_id", workspaceId.toString())
                        .putMetadata("plan_code", targetPlanCode)
                        .build();

        RequestOptions opts = RequestOptions.builder()
                .setIdempotencyKey(IdempotencyKeys.forChangePlan(workspaceId, targetPlanCode))
                .build();

        Subscription updated = sub.update(params, opts);
        log.info("Stripe subscription update workspace={} sub_id={} new_plan={} new_price={}",
                workspaceId, stripeSubscriptionId, targetPlanCode, newPriceId);
        return updated;
    }

    /** Lecture simple (idempotente, pas de key). */
    public Invoice retrieveInvoice(String stripeInvoiceId) throws StripeException {
        return Invoice.retrieve(stripeInvoiceId);
    }

    /**
     * Expose la cle webhook secret pour le handler signature verification.
     * Voir {@code StripeWebhookController}.
     */
    public String getWebhookSecret() {
        return stripeProperties.getWebhookSecret();
    }
}

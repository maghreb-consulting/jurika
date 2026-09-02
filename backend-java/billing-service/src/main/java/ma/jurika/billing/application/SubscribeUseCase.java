package ma.jurika.billing.application;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import ma.jurika.billing.infrastructure.stripe.StripeProperties;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — Use case principal de souscription (RG-BL17 composition).
 *
 * <p>Pattern : compose {@code CreateStripeCustomerUseCase} +
 * {@code CreateCheckoutSessionUseCase} + {@code RecordSubscriptionUseCase}
 * en methodes prive de cette classe (les "use cases" sont des etapes
 * conceptuelles, pas chacune un service Spring separe — meme pattern
 * que {@code SignupCabinetUseCase} Sprint 11 qui compose
 * {@code RegisterWorkspaceUseCase}).
 *
 * <p>Pipeline :
 * <ol>
 *   <li>findOrCreateStripeCustomer(workspaceId, email, name)</li>
 *   <li>createCheckoutSession(customer, priceId, urls)</li>
 *   <li>recordPendingSubscription(workspace, customer, status=incomplete)</li>
 * </ol>
 * Le workspace ne passe en {@code active} qu'a reception du webhook
 * {@code checkout.session.completed} (cf. T4 CheckoutCompletedHandler).
 *
 * <p>Idempotency : si une checkout session a deja ete creee pour ce
 * (workspace, plan, jour), Stripe retournera la meme cle d'idempotency
 * = meme session. Pas de doublon.
 */
@Service
public class SubscribeUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubscribeUseCase.class);

    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    private final BillingProperties billingProperties;
    private final SubscriptionJpaRepository subscriptionRepo;

    public SubscribeUseCase(StripeService stripeService,
                             StripeProperties stripeProperties,
                             BillingProperties billingProperties,
                             SubscriptionJpaRepository subscriptionRepo) {
        this.stripeService = stripeService;
        this.stripeProperties = stripeProperties;
        this.billingProperties = billingProperties;
        this.subscriptionRepo = subscriptionRepo;
    }

    public record Command(
            UUID workspaceId,
            String contactEmail,
            String workspaceName,
            String planCode,
            BillingPeriod billingPeriod
    ) {
        /** Constructeur retro-compat Sprint 12 — defaut MONTHLY. */
        public Command(UUID workspaceId, String contactEmail, String workspaceName, String planCode) {
            this(workspaceId, contactEmail, workspaceName, planCode, BillingPeriod.MONTHLY);
        }
    }

    public record Result(
            String checkoutSessionId,
            String checkoutUrl,
            String stripeCustomerId,
            String planCode,
            String billingPeriod
    ) {}

    @Transactional
    public Result subscribe(Command cmd) {
        validateCommand(cmd);
        if (subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(cmd.workspaceId(), "active").isPresent()) {
            throw new BusinessException("BILLING_ALREADY_ACTIVE",
                    "Workspace deja sous abonnement actif. Use Customer Portal pour changer de plan.");
        }

        BillingPeriod period = cmd.billingPeriod() != null ? cmd.billingPeriod() : BillingPeriod.MONTHLY;
        String priceId = stripeProperties.resolvePriceId(cmd.planCode(), period);

        try {
            String stripeCustomerId = findOrCreateStripeCustomer(cmd);
            Session session = createCheckoutSession(cmd, stripeCustomerId, priceId);
            recordPendingSubscription(cmd, stripeCustomerId);
            log.info("Checkout session creee workspace={} plan={} period={} session={}",
                    cmd.workspaceId(), cmd.planCode(), period, session.getId());
            return new Result(session.getId(), session.getUrl(), stripeCustomerId,
                    cmd.planCode(), period.suffix());
        } catch (StripeException e) {
            log.error("Erreur Stripe lors du subscribe workspace={} plan={}",
                    cmd.workspaceId(), cmd.planCode(), e);
            throw new BusinessException("STRIPE_ERROR", "Stripe error: " + e.getStripeError().getMessage());
        }
    }

    // ─── Etape 1 : findOrCreateStripeCustomer ───────────────────────────
    private String findOrCreateStripeCustomer(Command cmd) throws StripeException {
        // Cherche d'abord dans nos subscriptions historiques (memoizer le customer_id Stripe).
        return subscriptionRepo.findFirstByWorkspaceIdOrderByCreatedAtDesc(cmd.workspaceId())
                .map(SubscriptionEntity::getStripeCustomerId)
                .orElseGet(() -> {
                    try {
                        Customer customer = stripeService.createCustomer(
                                cmd.workspaceId(), cmd.contactEmail(), cmd.workspaceName());
                        return customer.getId();
                    } catch (StripeException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    // ─── Etape 2 : createCheckoutSession ─────────────────────────────────
    private Session createCheckoutSession(Command cmd, String stripeCustomerId, String priceId) throws StripeException {
        return stripeService.createCheckoutSession(
                stripeCustomerId,
                priceId,
                cmd.workspaceId(),
                cmd.planCode(),
                billingProperties.getCheckout().getSuccessUrl(),
                billingProperties.getCheckout().getCancelUrl());
    }

    // ─── Etape 3 : recordPendingSubscription ─────────────────────────────
    private void recordPendingSubscription(Command cmd, String stripeCustomerId) {
        // Ne pas creer de ligne "active" ici — c'est le webhook
        // checkout.session.completed qui finalise. Le row "incomplete"
        // sert juste a tracer la tentative pour observabilite.
        // Skip si une tentative recente existe deja.
        log.debug("Tentative checkout workspace={} customer={} plan={} (subscription row creee par webhook)",
                cmd.workspaceId(), stripeCustomerId, cmd.planCode());
    }

    private void validateCommand(Command cmd) {
        if (cmd.workspaceId() == null) throw new IllegalArgumentException("workspaceId requis");
        if (cmd.contactEmail() == null || cmd.contactEmail().isBlank()) throw new IllegalArgumentException("contactEmail requis");
        if (cmd.planCode() == null || cmd.planCode().isBlank()) throw new IllegalArgumentException("planCode requis");
        // RG-BL10 : entreprise (canonique V16) ET enterprise (alias) sont rejetes.
        String normalized = PlanCatalog.normalize(cmd.planCode());
        if ("entreprise".equals(normalized)) {
            throw new BusinessException("BILLING_ENTERPRISE_NOT_STRIPE",
                    "Plan entreprise sur devis — utilise POST /api/v1/billing/contact-sales (RG-BL10)");
        }
    }
}

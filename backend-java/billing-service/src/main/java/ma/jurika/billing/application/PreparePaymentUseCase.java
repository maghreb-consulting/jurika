package ma.jurika.billing.application;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.api.dto.BillingDtos.PaymentDto;
import ma.jurika.billing.api.dto.BillingDtos.PaymentInstructionsDto;
import ma.jurika.billing.api.dto.BillingDtos.PaymentsListDto;
import ma.jurika.billing.api.dto.BillingDtos.PreparePaymentRequest;
import ma.jurika.billing.api.dto.BillingDtos.PreparePaymentResponse;
import ma.jurika.billing.application.workspace.WorkspaceStatusUpdater;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import ma.jurika.billing.infrastructure.stripe.StripeProperties;
import ma.jurika.billing.infrastructure.stripe.StripeService;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BUG 14 (2026-06-07) — initialise un paiement multi-moyens entre le choix
 * du forfait et l'activation des identifiants.
 *
 * <p>4 methodes :
 * <ul>
 *   <li>{@code CARD} : reutilise {@link StripeService#createCheckoutSession}
 *       (mode SUBSCRIPTION), enregistre un Payment PENDING avec
 *       {@code stripe_payment_id = session.id}. Le webhook
 *       {@code checkout.session.completed} bascule le row a COMPLETED.</li>
 *   <li>{@code BANK_TRANSFER} : Payment PENDING + instructions RIB + reference</li>
 *   <li>{@code CHEQUE} : Payment PENDING + instructions ordre + adresse</li>
 *   <li>{@code CASH} : Payment PENDING + instructions "regler au siege"</li>
 * </ul>
 *
 * <p>Pour les 3 hors-ligne, la validation est manuelle via
 * {@link ValidatePaymentUseCase} (PATCH /payments/{id}/validate).
 */
@Service
public class PreparePaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PreparePaymentUseCase.class);

    private static final java.util.Set<String> ALLOWED_METHODS =
            java.util.Set.of("CARD", "BANK_TRANSFER", "CHEQUE", "CASH", "TEST_BYPASS");

    private final PaymentJpaRepository paymentRepo;
    private final SubscriptionJpaRepository subscriptionRepo;
    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    private final BillingProperties billingProperties;
    private final WorkspaceStatusUpdater workspaceUpdater;

    public PreparePaymentUseCase(PaymentJpaRepository paymentRepo,
                                  SubscriptionJpaRepository subscriptionRepo,
                                  StripeService stripeService,
                                  StripeProperties stripeProperties,
                                  BillingProperties billingProperties,
                                  WorkspaceStatusUpdater workspaceUpdater) {
        this.paymentRepo = paymentRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.stripeService = stripeService;
        this.stripeProperties = stripeProperties;
        this.billingProperties = billingProperties;
        this.workspaceUpdater = workspaceUpdater;
    }

    @Transactional
    public PreparePaymentResponse execute(UUID workspaceId, PreparePaymentRequest req) {
        validate(req);
        String method = req.method().toUpperCase();

        // BUG 14 (2026-06-07) — gate strict TEST_BYPASS : interdit en prod
        // sauf si property explicitement activee.
        if ("TEST_BYPASS".equals(method) && !billingProperties.isTestBypassEnabled()) {
            throw new BusinessException("TEST_BYPASS_DISABLED",
                    "La methode TEST_BYPASS n'est pas autorisee. "
                            + "Activer jurika.billing.test-bypass-enabled=true en dev/staging si necessaire.");
        }
        String planCode = PlanCatalog.normalize(req.planCode());
        Plan plan = PlanCatalog.findByCode(planCode)
                .orElseThrow(() -> new BusinessException("PLAN_UNKNOWN", "Plan inconnu: " + req.planCode()));
        if (plan.quoteOnly()) {
            throw new BusinessException("PLAN_QUOTE_ONLY",
                    "Plan Entreprise sur devis — utilisez la demande contact-sales.");
        }
        BillingPeriod period = BillingPeriod.fromString(req.billingPeriod());
        int priceMad = period == BillingPeriod.YEARLY
                ? (plan.priceYearlyMad() == null ? 0 : plan.priceYearlyMad())
                : (plan.priceMonthlyMad() == null ? 0 : plan.priceMonthlyMad());
        long amountCents = (long) priceMad * 100L;

        PaymentEntity payment = new PaymentEntity();
        payment.setWorkspaceId(workspaceId);
        payment.setPlanCode(planCode);
        payment.setBillingPeriod(period.suffix());
        payment.setAmountMadCents(amountCents);
        payment.setCurrency("mad");
        payment.setMethod(method);
        payment.setStatus("PENDING");
        payment.setBankReference(req.reference());

        if ("CARD".equals(method)) {
            try {
                String stripeCustomerId = findOrCreateStripeCustomer(workspaceId, req);
                String priceId = stripeProperties.resolvePriceId(planCode, period);
                Session session = stripeService.createCheckoutSession(
                        stripeCustomerId, priceId, workspaceId, planCode,
                        billingProperties.getCheckout().getSuccessUrl(),
                        billingProperties.getCheckout().getCancelUrl());
                payment.setStripePaymentId(session.getId());
                payment.setStripeCheckoutUrl(session.getUrl());
            } catch (StripeException e) {
                log.error("Echec Stripe checkout pour workspace={} : {}", workspaceId, e.getMessage());
                payment.setStatus("FAILED");
                payment.setNotes("Stripe error: " + (e.getStripeError() != null
                        ? e.getStripeError().getMessage() : e.getMessage()));
                paymentRepo.save(payment);
                throw new BusinessException("STRIPE_ERROR",
                        "Service de paiement indisponible. Reessayez ou choisissez un autre moyen.");
            }
        } else if ("TEST_BYPASS".equals(method)) {
            // BUG 14 — bypass instantane : on cree direct le Payment en
            // COMPLETED + shadow subscription + activation + emission
            // identifiants. Reservation EXCLUSIVE au dev/staging via
            // BillingProperties.testBypassEnabled (gardee plus haut).
            payment.setStatus("COMPLETED");
            payment.setCompletedAt(java.time.Instant.now());
            payment.setValidatedBy("TEST_BYPASS");
            payment.setNotes("Paiement valide automatiquement (mode test bypass — dev/staging only).");
            paymentRepo.save(payment);
            ensureShadowSubscription(workspaceId, planCode, period);
            workspaceUpdater.activate(workspaceId, planCode,
                    java.time.Instant.now().plusSeconds(
                            ("yearly".equalsIgnoreCase(period.suffix()) ? 365L : 30L) * 24L * 3600L));
            workspaceUpdater.issueCredentials(workspaceId, "PAYMENT_VALIDATED_TEST_BYPASS");
            log.info("Payment TEST_BYPASS cree COMPLETED workspace={} plan={} period={}",
                    workspaceId, planCode, period);
            return new PreparePaymentResponse(
                    payment.getId(), payment.getMethod(), payment.getStatus(),
                    payment.getPlanCode(), payment.getBillingPeriod(),
                    payment.getAmountMadCents(), payment.getCurrency(),
                    null,
                    java.util.List.of(
                            new PaymentInstructionsDto("paymentRef", "PAY-" + payment.getId()),
                            new PaymentInstructionsDto("mode", "TEST BYPASS (dev only) — workspace active, identifiants envoyes par email.")),
                    "Paiement valide instantanement (mode test). Verifiez votre boite mail pour les identifiants.");
        }

        paymentRepo.save(payment);
        log.info("Payment PENDING cree workspace={} method={} plan={} amount={} centimes",
                workspaceId, method, planCode, amountCents);

        List<PaymentInstructionsDto> instructions = buildInstructions(method, payment, plan, period);
        String userMessage = userMessageFor(method);

        return new PreparePaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getPlanCode(),
                payment.getBillingPeriod(),
                payment.getAmountMadCents(),
                payment.getCurrency(),
                payment.getStripeCheckoutUrl(),
                instructions,
                userMessage);
    }

    @Transactional(readOnly = true)
    public PaymentsListDto listForWorkspace(UUID workspaceId) {
        List<PaymentDto> items = paymentRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(PreparePaymentUseCase::toDto)
                .toList();
        return new PaymentsListDto(items);
    }

    /** Builder Dto reutilise par ValidatePaymentUseCase. */
    public static PaymentDto toDto(PaymentEntity e) {
        return new PaymentDto(
                e.getId(), e.getPlanCode(), e.getBillingPeriod(),
                e.getAmountMadCents(), e.getCurrency(),
                e.getMethod(), e.getStatus(),
                e.getStripePaymentId(),
                e.getBankReference(), e.getChequeNumber(),
                e.getChequeDate() != null ? e.getChequeDate().toString() : null,
                e.getProofUrl(),
                e.getCreatedAt(), e.getCompletedAt(),
                e.getValidatedBy(), e.getNotes());
    }

    private void validate(PreparePaymentRequest req) {
        if (req == null) throw new IllegalArgumentException("body requis");
        if (req.planCode() == null || req.planCode().isBlank()) {
            throw new IllegalArgumentException("planCode requis");
        }
        if (req.method() == null || !ALLOWED_METHODS.contains(req.method().toUpperCase())) {
            throw new BusinessException("PAYMENT_METHOD_UNKNOWN",
                    "Methode de paiement non supportee : " + req.method()
                            + " (attendu : CARD | BANK_TRANSFER | CHEQUE | CASH)");
        }
    }

    /** BUG 14 — shadow subscription pour les flux hors Stripe (TEST_BYPASS, validation manuelle hors-ligne). */
    private void ensureShadowSubscription(UUID workspaceId, String planCode, BillingPeriod period) {
        if (subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, "active").isPresent()) {
            return; // idempotent
        }
        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setWorkspaceId(workspaceId);
        sub.setStripeCustomerId("manual-" + workspaceId.toString().substring(0, 8));
        sub.setStripeSubscriptionId("manual-PAY-bypass-" + System.nanoTime());
        sub.setPlanCode(planCode);
        sub.setStatus("active");
        sub.setCurrentPeriodStart(java.time.Instant.now());
        long days = period == BillingPeriod.YEARLY ? 365L : 30L;
        sub.setCurrentPeriodEnd(java.time.Instant.now().plusSeconds(days * 24L * 3600L));
        subscriptionRepo.save(sub);
    }

    private String findOrCreateStripeCustomer(UUID workspaceId, PreparePaymentRequest req)
            throws StripeException {
        return subscriptionRepo.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .map(SubscriptionEntity::getStripeCustomerId)
                .orElseGet(() -> {
                    try {
                        Customer c = stripeService.createCustomer(
                                workspaceId,
                                req.contactEmail() != null ? req.contactEmail() : "billing@jurika.ma",
                                req.workspaceName() != null ? req.workspaceName() : "Cabinet JURIKA");
                        return c.getId();
                    } catch (StripeException ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    private List<PaymentInstructionsDto> buildInstructions(String method,
                                                            PaymentEntity payment,
                                                            Plan plan,
                                                            BillingPeriod period) {
        List<PaymentInstructionsDto> out = new ArrayList<>();
        switch (method) {
            case "CARD" -> {
                out.add(new PaymentInstructionsDto("checkoutUrl", payment.getStripeCheckoutUrl()));
                out.add(new PaymentInstructionsDto("paymentRef", "PAY-" + payment.getId()));
            }
            case "BANK_TRANSFER" -> {
                // Note : ces coordonnees doivent etre injectees via properties en prod.
                // V1 demo : valeurs statiques Maghreb Consulting (a remplacer Sprint 13).
                out.add(new PaymentInstructionsDto("beneficiary", "Maghreb Consulting SARL"));
                out.add(new PaymentInstructionsDto("bank", "Attijariwafa Bank — Agence Casablanca Centre"));
                out.add(new PaymentInstructionsDto("rib", "007 780 0001234567890123 45"));
                out.add(new PaymentInstructionsDto("iban", "MA64 0078 0000 1234 5678 9012 3456"));
                out.add(new PaymentInstructionsDto("swift", "BCMAMAMC"));
                out.add(new PaymentInstructionsDto("reference",
                        "JURIKA-" + payment.getWorkspaceId().toString().substring(0, 8).toUpperCase() + "-" + payment.getId()));
                out.add(new PaymentInstructionsDto("amount",
                        formatMad(payment.getAmountMadCents()) + " (" + plan.displayLabel() + " " + period.suffix() + ")"));
            }
            case "CHEQUE" -> {
                out.add(new PaymentInstructionsDto("beneficiary", "Maghreb Consulting SARL"));
                out.add(new PaymentInstructionsDto("mailingAddress",
                        "Maghreb Consulting — Service Comptabilite\n45 Rue Mohammed Diouri, 20250 Casablanca"));
                out.add(new PaymentInstructionsDto("reference",
                        "JURIKA-" + payment.getWorkspaceId().toString().substring(0, 8).toUpperCase() + "-" + payment.getId()));
                out.add(new PaymentInstructionsDto("amount", formatMad(payment.getAmountMadCents())));
                out.add(new PaymentInstructionsDto("instruction",
                        "Mentionnez la reference au dos du cheque et envoyez-nous une photo via le bouton ci-dessous."));
            }
            case "CASH" -> {
                out.add(new PaymentInstructionsDto("siegeAddress",
                        "Maghreb Consulting — 45 Rue Mohammed Diouri, 20250 Casablanca"));
                out.add(new PaymentInstructionsDto("hours", "Lundi-Vendredi 9h-17h, Samedi 9h-12h"));
                out.add(new PaymentInstructionsDto("amount", formatMad(payment.getAmountMadCents())));
                out.add(new PaymentInstructionsDto("reference",
                        "JURIKA-" + payment.getWorkspaceId().toString().substring(0, 8).toUpperCase() + "-" + payment.getId()));
                out.add(new PaymentInstructionsDto("instruction",
                        "Presentez la reference au comptoir. Un recu vous sera remis."));
            }
            default -> { /* validated upstream */ }
        }
        return out;
    }

    private static String userMessageFor(String method) {
        return switch (method) {
            case "CARD" -> "Redirection vers la page securisee Stripe...";
            case "BANK_TRANSFER" -> "Votre paiement est enregistre. Il sera active sous 24-48h apres reception du virement.";
            case "CHEQUE" -> "Votre paiement est enregistre. Il sera active a reception et encaissement du cheque (5-7 jours).";
            case "CASH" -> "Votre paiement est enregistre. Il sera active immediatement apres reglement au siege.";
            default -> "Paiement en attente de validation.";
        };
    }

    private static String formatMad(long cents) {
        return String.format("%,d MAD", cents / 100L).replace(',', ' ');
    }
}

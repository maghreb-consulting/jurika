package ma.jurika.billing.application.webhook;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import ma.jurika.billing.application.ValidatePaymentUseCase;
import ma.jurika.billing.application.email.BillingEmailService;
import ma.jurika.billing.application.workspace.WorkspaceStatusUpdater;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.InvoiceJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.InvoiceEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.billing.infrastructure.stripe.BillingProperties;
import ma.jurika.billing.infrastructure.stripe.StripeProperties;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.events.BusinessEventPublisher;
import ma.jurika.common.events.BusinessEventPublisher.EventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 12 — handlers Stripe par event type. 5 handlers @Component statics
 * picked up by Spring component scan.
 *
 * <p>Effets de bord par handler (T11 gap-fill) :
 *  1. Mutation DB billing (subscription / invoice)
 *  2. Notification WorkspaceStatusUpdater (auth-service via Feign T7)
 *  3. {@link BillingEmailService} email transactionnel (RG-BL05/06/16)
 *  4. {@link BusinessEventPublisher} business_events (RG-SAAS-02 funnel)
 *  5. {@link Auditable} via AuditAspect (RG-BL15 transitions status)
 *
 * <p>Tous les effets de bord sont best-effort : si l'email/event/audit
 * echoue, le handler reussit quand meme (la DB billing est la source de
 * verite, on peut reconcilier).
 */
public final class StripeEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(StripeEventHandlers.class);

    private StripeEventHandlers() {}

    // ─────────────────────────────────────────────────────────────────────
    // 1. checkout.session.completed — conversion trial → active
    // ─────────────────────────────────────────────────────────────────────
    @Component
    public static class CheckoutCompletedHandler implements StripeEventHandler {

        private final SubscriptionJpaRepository subscriptionRepo;
        private final WorkspaceStatusUpdater workspaceUpdater;
        private final BillingEmailService emailService;
        private final ObjectProvider<BusinessEventPublisher> eventPublisher;
        private final ObjectProvider<ValidatePaymentUseCase> validatePaymentProvider;

        public CheckoutCompletedHandler(SubscriptionJpaRepository subscriptionRepo,
                                         WorkspaceStatusUpdater workspaceUpdater,
                                         BillingEmailService emailService,
                                         ObjectProvider<BusinessEventPublisher> eventPublisher,
                                         ObjectProvider<ValidatePaymentUseCase> validatePaymentProvider) {
            this.subscriptionRepo = subscriptionRepo;
            this.workspaceUpdater = workspaceUpdater;
            this.emailService = emailService;
            this.eventPublisher = eventPublisher;
            this.validatePaymentProvider = validatePaymentProvider;
        }

        @Override public String eventType() { return "checkout.session.completed"; }

        @Override
        @Transactional
        @Auditable(action = "BILLING_WORKSPACE_ACTIVATED", resourceType = "workspace")
        public void handle(Event event) {
            Session session = (Session) deserialize(event);
            if (session == null || session.getSubscription() == null) {
                log.warn("checkout.session.completed sans subscription — skip. event_id={}", event.getId());
                return;
            }
            UUID workspaceId = extractWorkspaceId(session.getMetadata().get("workspace_id"));
            String planCodeRaw = session.getMetadata().get("plan_code");
            String planCode = StripeProperties.normalize(planCodeRaw != null ? planCodeRaw : "essentiel");
            String stripeSubscriptionId = session.getSubscription();
            String stripeCustomerId = session.getCustomer();
            String contactEmail = extractEmail(session);

            // Idempotency : si on a deja persiste cette subscription, no-op.
            if (subscriptionRepo.findByStripeSubscriptionId(stripeSubscriptionId).isPresent()) {
                log.info("Subscription deja persistee (skip). stripe_sub_id={}", stripeSubscriptionId);
                return;
            }

            SubscriptionEntity sub = new SubscriptionEntity();
            sub.setWorkspaceId(workspaceId);
            sub.setStripeCustomerId(stripeCustomerId);
            sub.setStripeSubscriptionId(stripeSubscriptionId);
            sub.setPlanCode(planCode);
            sub.setStatus("active");
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plusSeconds(30L * 24 * 3600));
            subscriptionRepo.save(sub);

            workspaceUpdater.activate(workspaceId, planCode, sub.getCurrentPeriodEnd());
            log.info("Workspace converti trial -> active. workspace={} plan={}", workspaceId, planCode);

            // BUG 14 (2026-06-07) — si un Payment CARD PENDING existe pour cette
            // session, on le bascule a COMPLETED + on declenche l'envoi des
            // identifiants. Idempotent : si pas de Payment, no-op silencieux.
            ValidatePaymentUseCase validatePayment = validatePaymentProvider.getIfAvailable();
            if (validatePayment != null) {
                try {
                    validatePayment.markCompletedByStripeSession(session.getId(), "stripe-webhook");
                } catch (RuntimeException ex) {
                    log.warn("markCompletedByStripeSession KO session={} : {}", session.getId(), ex.getMessage());
                }
            }

            // Email conversion + business event (best-effort)
            if (contactEmail != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("contactName", "client JURIKA");
                vars.put("planLabel", labelFor(planCode));
                vars.put("nextRenewal", formatDate(sub.getCurrentPeriodEnd()));
                vars.put("supportLevel", "prioritaire".equals(planCode) ? "prioritaire" : "email J+1");
                vars.put("dashboardUrl", "http://localhost:5173/app/dashboard");
                emailService.sendConversionActive(contactEmail, vars);
            }
            publishEvent("BILLING_CONVERSION_COMPLETED", workspaceId,
                    Map.of("planCode", planCode), eventPublisher);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. invoice.payment_succeeded — recording facture + email
    // ─────────────────────────────────────────────────────────────────────
    @Component
    public static class InvoicePaymentSucceededHandler implements StripeEventHandler {

        private final InvoiceJpaRepository invoiceRepo;
        private final SubscriptionJpaRepository subscriptionRepo;
        private final BillingProperties billingProps;
        private final BillingEmailService emailService;
        private final ObjectProvider<BusinessEventPublisher> eventPublisher;

        public InvoicePaymentSucceededHandler(InvoiceJpaRepository invoiceRepo,
                                                SubscriptionJpaRepository subscriptionRepo,
                                                BillingProperties billingProps,
                                                BillingEmailService emailService,
                                                ObjectProvider<BusinessEventPublisher> eventPublisher) {
            this.invoiceRepo = invoiceRepo;
            this.subscriptionRepo = subscriptionRepo;
            this.billingProps = billingProps;
            this.emailService = emailService;
            this.eventPublisher = eventPublisher;
        }

        @Override public String eventType() { return "invoice.payment_succeeded"; }

        @Override
        @Transactional
        @Auditable(action = "BILLING_INVOICE_PAID", resourceType = "invoice")
        public void handle(Event event) {
            Invoice invoice = (Invoice) deserialize(event);
            if (invoice == null) return;

            if (invoiceRepo.findByStripeInvoiceId(invoice.getId()).isPresent()) {
                log.info("Invoice deja persistee (skip). stripe_invoice_id={}", invoice.getId());
                return;
            }

            UUID workspaceId = lookupWorkspaceFromSubscription(invoice.getSubscription(), subscriptionRepo);
            if (workspaceId == null) {
                log.warn("Impossible de resoudre workspace pour invoice {}", invoice.getId());
                return;
            }

            // RG-BL12 : calcul TVA Maroc 20% backend (pas Stripe Tax).
            long amountTtcCents = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : 0L;
            BigDecimal tvaRate = billingProps.getTvaRatePercent();
            BigDecimal divisor = BigDecimal.ONE.add(tvaRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            long amountHtCents = new BigDecimal(amountTtcCents)
                    .divide(divisor, 0, RoundingMode.HALF_UP)
                    .longValueExact();
            long amountTvaCents = amountTtcCents - amountHtCents;

            InvoiceEntity entity = new InvoiceEntity();
            entity.setWorkspaceId(workspaceId);
            entity.setStripeInvoiceId(invoice.getId());
            entity.setNumber(invoice.getNumber() != null ? invoice.getNumber() : invoice.getId());
            entity.setAmountDueCents(invoice.getAmountDue() != null ? invoice.getAmountDue() : 0L);
            entity.setAmountPaidCents(amountTtcCents);
            entity.setAmountHtCents(amountHtCents);
            entity.setAmountTvaCents(amountTvaCents);
            entity.setCurrency(invoice.getCurrency() != null ? invoice.getCurrency() : "mad");
            entity.setStatus("paid");
            entity.setTvaRate(tvaRate);
            entity.setInvoicePdfUrl(invoice.getInvoicePdf());
            entity.setHostedInvoiceUrl(invoice.getHostedInvoiceUrl());
            entity.setIssuedAt(Instant.ofEpochSecond(invoice.getCreated() != null ? invoice.getCreated() : Instant.now().getEpochSecond()));
            entity.setPaidAt(Instant.now());

            subscriptionRepo.findByStripeSubscriptionId(invoice.getSubscription())
                    .map(SubscriptionEntity::getId)
                    .ifPresent(entity::setSubscriptionId);
            invoiceRepo.save(entity);
            log.info("Invoice persistee workspace={} number={} ttc={} ht={} tva={}",
                    workspaceId, entity.getNumber(), amountTtcCents, amountHtCents, amountTvaCents);

            String contactEmail = invoice.getCustomerEmail();
            if (contactEmail != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("contactName", "client JURIKA");
                vars.put("invoiceNumber", entity.getNumber());
                vars.put("periodStart", formatDate(entity.getIssuedAt()));
                vars.put("periodEnd", formatDate(entity.getIssuedAt().plusSeconds(30L * 24 * 3600)));
                vars.put("amountHt", formatMad(amountHtCents));
                vars.put("amountTva", formatMad(amountTvaCents));
                vars.put("amountTtc", formatMad(amountTtcCents));
                vars.put("invoicePdfUrl", entity.getInvoicePdfUrl());
                vars.put("billingUrl", "http://localhost:5173/app/billing");
                emailService.sendInvoiceAvailable(contactEmail, vars);
            }
            publishEvent("BILLING_INVOICE_PAID", workspaceId,
                    Map.of("number", entity.getNumber(), "amountTtcCents", amountTtcCents), eventPublisher);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. invoice.payment_failed — workspace passe past_due
    // ─────────────────────────────────────────────────────────────────────
    @Component
    public static class InvoicePaymentFailedHandler implements StripeEventHandler {

        private final SubscriptionJpaRepository subscriptionRepo;
        private final BillingEmailService emailService;
        private final BillingProperties billingProps;
        private final WorkspaceStatusUpdater workspaceUpdater;
        private final ObjectProvider<BusinessEventPublisher> eventPublisher;

        public InvoicePaymentFailedHandler(SubscriptionJpaRepository subscriptionRepo,
                                            BillingEmailService emailService,
                                            BillingProperties billingProps,
                                            WorkspaceStatusUpdater workspaceUpdater,
                                            ObjectProvider<BusinessEventPublisher> eventPublisher) {
            this.subscriptionRepo = subscriptionRepo;
            this.emailService = emailService;
            this.billingProps = billingProps;
            this.workspaceUpdater = workspaceUpdater;
            this.eventPublisher = eventPublisher;
        }

        @Override public String eventType() { return "invoice.payment_failed"; }

        @Override
        @Transactional
        @Auditable(action = "BILLING_WORKSPACE_PAST_DUE", resourceType = "workspace")
        public void handle(Event event) {
            Invoice invoice = (Invoice) deserialize(event);
            if (invoice == null || invoice.getSubscription() == null) return;
            subscriptionRepo.findByStripeSubscriptionId(invoice.getSubscription())
                    .ifPresent(sub -> {
                        sub.setStatus("past_due");
                        sub.setUpdatedAt(Instant.now());
                        log.warn("Payment failed workspace={} sub_id={}",
                                sub.getWorkspaceId(), sub.getStripeSubscriptionId());
                        workspaceUpdater.markPastDue(sub.getWorkspaceId());

                        String contactEmail = invoice.getCustomerEmail();
                        if (contactEmail != null) {
                            Map<String, Object> vars = new HashMap<>();
                            vars.put("contactName", "client JURIKA");
                            vars.put("planCode", sub.getPlanCode());
                            vars.put("amountTtc", formatMad(invoice.getAmountDue() != null ? invoice.getAmountDue() : 0L));
                            vars.put("customerPortalUrl", billingProps.getPortal().getReturnUrl());
                            emailService.sendPaymentFailed(contactEmail, vars);
                        }
                        publishEvent("BILLING_PAYMENT_FAILED", sub.getWorkspaceId(),
                                Map.of("subscriptionId", sub.getStripeSubscriptionId()), eventPublisher);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. customer.subscription.updated — sync status + period + reactivation
    // ─────────────────────────────────────────────────────────────────────
    @Component
    public static class SubscriptionUpdatedHandler implements StripeEventHandler {

        private final SubscriptionJpaRepository subscriptionRepo;
        private final BillingEmailService emailService;
        private final BillingProperties billingProps;
        private final ObjectProvider<BusinessEventPublisher> eventPublisher;

        public SubscriptionUpdatedHandler(SubscriptionJpaRepository subscriptionRepo,
                                            BillingEmailService emailService,
                                            BillingProperties billingProps,
                                            ObjectProvider<BusinessEventPublisher> eventPublisher) {
            this.subscriptionRepo = subscriptionRepo;
            this.emailService = emailService;
            this.billingProps = billingProps;
            this.eventPublisher = eventPublisher;
        }

        @Override public String eventType() { return "customer.subscription.updated"; }

        @Override
        @Transactional
        public void handle(Event event) {
            Subscription stripeSub = (Subscription) deserialize(event);
            if (stripeSub == null) return;
            Optional<SubscriptionEntity> opt = subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId());
            if (opt.isEmpty()) {
                log.info("Subscription updated avant que checkout.completed soit traite (replay attendu). sub_id={}",
                        stripeSub.getId());
                return;
            }
            SubscriptionEntity sub = opt.get();
            String oldStatus = sub.getStatus();
            boolean wasCancelled = "cancelled".equals(oldStatus) || sub.getCancelledAt() != null;

            sub.setStatus(stripeSub.getStatus() != null ? stripeSub.getStatus() : sub.getStatus());
            sub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
            if (stripeSub.getCurrentPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
            }
            if (stripeSub.getCurrentPeriodStart() != null) {
                sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()));
            }
            sub.setUpdatedAt(Instant.now());
            log.info("Subscription mise a jour workspace={} status={} cancel_at_end={}",
                    sub.getWorkspaceId(), sub.getStatus(), sub.isCancelAtPeriodEnd());

            // Reactivation : ancien etat 'cancelled' (ou cancelledAt non null) + nouvelle status 'active'.
            if (wasCancelled && "active".equals(sub.getStatus()) && !sub.isCancelAtPeriodEnd()) {
                sub.setCancelledAt(null);
                publishEvent("BILLING_WORKSPACE_REACTIVATED", sub.getWorkspaceId(),
                        Map.of("planCode", sub.getPlanCode()), eventPublisher);
                Map<String, Object> vars = new HashMap<>();
                vars.put("contactName", "client JURIKA");
                vars.put("planLabel", labelFor(sub.getPlanCode()));
                vars.put("workspaceCode", String.valueOf(sub.getWorkspaceId()).substring(0, 8));
                vars.put("nextRenewal", formatDate(sub.getCurrentPeriodEnd()));
                vars.put("dashboardUrl", "http://localhost:5173/app/dashboard");
                String to = lookupContactEmail(sub);
                if (to != null) emailService.sendReactivation(to, vars);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. customer.subscription.deleted — cancellation (RG-BL07)
    // ─────────────────────────────────────────────────────────────────────
    @Component
    public static class SubscriptionDeletedHandler implements StripeEventHandler {

        private final SubscriptionJpaRepository subscriptionRepo;
        private final WorkspaceStatusUpdater workspaceUpdater;
        private final BillingEmailService emailService;
        private final ObjectProvider<BusinessEventPublisher> eventPublisher;

        public SubscriptionDeletedHandler(SubscriptionJpaRepository subscriptionRepo,
                                            WorkspaceStatusUpdater workspaceUpdater,
                                            BillingEmailService emailService,
                                            ObjectProvider<BusinessEventPublisher> eventPublisher) {
            this.subscriptionRepo = subscriptionRepo;
            this.workspaceUpdater = workspaceUpdater;
            this.emailService = emailService;
            this.eventPublisher = eventPublisher;
        }

        @Override public String eventType() { return "customer.subscription.deleted"; }

        @Override
        @Transactional
        @Auditable(action = "BILLING_WORKSPACE_CANCELLED", resourceType = "workspace")
        public void handle(Event event) {
            Subscription stripeSub = (Subscription) deserialize(event);
            if (stripeSub == null) return;
            Optional<SubscriptionEntity> opt = subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId());
            if (opt.isEmpty()) {
                log.warn("Subscription.deleted sur subscription inconnue. sub_id={}", stripeSub.getId());
                return;
            }
            SubscriptionEntity sub = opt.get();
            sub.setStatus("cancelled");
            sub.setCancelledAt(Instant.now());
            sub.setUpdatedAt(Instant.now());
            // RG-BL07 : conserve l'acces jusqu'a current_period_end.
            workspaceUpdater.markCancelled(sub.getWorkspaceId(), sub.getCurrentPeriodEnd());
            log.info("Subscription annulee workspace={} access_until={}",
                    sub.getWorkspaceId(), sub.getCurrentPeriodEnd());

            String to = lookupContactEmail(sub);
            if (to != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("contactName", "client JURIKA");
                vars.put("planLabel", labelFor(sub.getPlanCode()));
                vars.put("accessUntil", formatDate(sub.getCurrentPeriodEnd()));
                vars.put("reactivateUrl", "http://localhost:5173/app/billing");
                emailService.sendCancellation(to, vars);
            }
            publishEvent("BILLING_WORKSPACE_CANCELLED", sub.getWorkspaceId(),
                    Map.of("planCode", sub.getPlanCode(),
                            "accessUntil", String.valueOf(sub.getCurrentPeriodEnd())),
                    eventPublisher);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static Object deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().orElse(null);
    }

    private static UUID extractWorkspaceId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("metadata workspace_id manquant — event ignore");
        }
        return UUID.fromString(raw);
    }

    private static String extractEmail(Session session) {
        if (session.getCustomerDetails() != null && session.getCustomerDetails().getEmail() != null) {
            return session.getCustomerDetails().getEmail();
        }
        return session.getCustomerEmail();
    }

    private static UUID lookupWorkspaceFromSubscription(String stripeSubscriptionId,
                                                         SubscriptionJpaRepository subscriptionRepo) {
        if (stripeSubscriptionId == null) return null;
        return subscriptionRepo.findByStripeSubscriptionId(stripeSubscriptionId)
                .map(SubscriptionEntity::getWorkspaceId).orElse(null);
    }

    private static String lookupContactEmail(SubscriptionEntity sub) {
        // V1 — pas de table billing_contact (a ajouter Sprint 13). En attendant,
        // on retourne null si on n'a pas l'email — le caller skip silencieux.
        return null;
    }

    private static String labelFor(String planCode) {
        if (planCode == null) return "JURIKA";
        // Spec directeur 2026-06-02 : essentiel / business / entreprise.
        // Les anciens codes (DB legacy) sont rabattus via PlanCatalog.normalize
        // (filet pour lignes pre-migration V17/V3).
        return ma.jurika.common.billing.PlanCatalog.findByCode(planCode)
                .map(p -> p.displayLabel())
                .orElse(planCode);
    }

    private static String formatDate(Instant t) {
        if (t == null) return "—";
        return java.time.LocalDate.ofInstant(t, java.time.ZoneId.of("Africa/Casablanca")).toString();
    }

    private static String formatMad(long cents) {
        BigDecimal mad = new BigDecimal(cents).movePointLeft(2);
        return mad.setScale(2, RoundingMode.HALF_UP) + " MAD";
    }

    /** Publishe en best-effort un business event via Feign (Sprint 11 TASK 6). */
    private static void publishEvent(String type, UUID workspaceId, Map<String, Object> props,
                                       ObjectProvider<BusinessEventPublisher> provider) {
        BusinessEventPublisher pub = provider.getIfAvailable();
        if (pub == null) return;
        try {
            EventPayload payload = EventPayload.of(type, workspaceId, props);
            pub.publish(payload, null);
        } catch (RuntimeException e) {
            log.warn("Echec publication business_event type={} workspace={} : {}",
                    type, workspaceId, e.getMessage());
        }
    }
}

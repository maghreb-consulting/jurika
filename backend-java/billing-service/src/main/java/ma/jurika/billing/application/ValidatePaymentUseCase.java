package ma.jurika.billing.application;

import ma.jurika.billing.api.dto.BillingDtos.PaymentDto;
import ma.jurika.billing.api.dto.BillingDtos.ValidatePaymentRequest;
import ma.jurika.billing.application.workspace.WorkspaceStatusUpdater;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * BUG 14 (2026-06-07) — valide manuellement un paiement hors-ligne
 * (BANK_TRANSFER / CHEQUE / CASH) ou re-confirme un paiement CARD coince
 * (cas edge ou le webhook checkout.session.completed n'est pas arrive).
 *
 * <p>Pipeline :
 * <ol>
 *   <li>Charge le payment par son identifiant (appel SUPER_ADMIN, lot L0 E17) ;
 *       le workspace active est celui du paiement</li>
 *   <li>Verifie status == PENDING (idempotent : skip si deja COMPLETED)</li>
 *   <li>Marque COMPLETED + completed_at + validated_by (email du caller)</li>
 *   <li>Si pas de subscription active, cree un "shadow row" SubscriptionEntity
 *       pour reflecter localement l'activation hors-Stripe (pas de stripe_sub_id
 *       reel — on persiste "manual-PAY-{id}" pour la tracabilite)</li>
 *   <li>Notify auth-service : activate(workspace, plan, periodEnd)</li>
 *   <li>Notify auth-service : issueCredentials(workspace, reason)</li>
 * </ol>
 */
@Service
public class ValidatePaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ValidatePaymentUseCase.class);

    private final PaymentJpaRepository paymentRepo;
    private final SubscriptionJpaRepository subscriptionRepo;
    private final WorkspaceStatusUpdater workspaceUpdater;

    public ValidatePaymentUseCase(PaymentJpaRepository paymentRepo,
                                   SubscriptionJpaRepository subscriptionRepo,
                                   WorkspaceStatusUpdater workspaceUpdater) {
        this.paymentRepo = paymentRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.workspaceUpdater = workspaceUpdater;
    }

    @Transactional
    @Auditable(action = "BILLING_PAYMENT_VALIDATED", resourceType = "payment")
    public PaymentDto execute(Long paymentId, ValidatePaymentRequest req) {
        // Lot L0 (E17, RG-PAY-02) : appele par le SUPER_ADMIN, dont le workspace
        // n'est pas celui du cabinet qui a paye. Le paiement est retrouve par son
        // identifiant, et c'est SON workspace qui est active.
        PaymentEntity p = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Paiement introuvable : " + paymentId));
        UUID workspaceId = p.getWorkspaceId();

        if ("COMPLETED".equals(p.getStatus())) {
            log.info("Payment {} deja COMPLETED — idempotent return", paymentId);
            return PreparePaymentUseCase.toDto(p);
        }
        if (!"PENDING".equals(p.getStatus())) {
            throw new BusinessException("PAYMENT_INVALID_TRANSITION",
                    "Paiement non validable depuis le statut " + p.getStatus());
        }

        p.setStatus("COMPLETED");
        p.setCompletedAt(Instant.now());
        p.setValidatedBy(currentCallerEmail());
        if (req != null && req.notes() != null && !req.notes().isBlank()) {
            p.setNotes(req.notes());
        }
        paymentRepo.save(p);

        // Pour les paiements hors-ligne (pas de webhook Stripe), on cree un
        // shadow SubscriptionEntity. Pour CARD, la sub a deja ete creee par
        // le webhook checkout.session.completed (idempotent : on skip).
        Instant periodEnd = computePeriodEnd(p);
        ensureLocalSubscription(p, periodEnd);

        workspaceUpdater.activate(workspaceId, p.getPlanCode(), periodEnd);
        log.info("Workspace active suite a validation paiement workspace={} payment={} method={} plan={}",
                workspaceId, paymentId, p.getMethod(), p.getPlanCode());

        String reason = "PAYMENT_VALIDATED_" + p.getMethod();
        workspaceUpdater.issueCredentials(workspaceId, reason);

        return PreparePaymentUseCase.toDto(p);
    }

    /**
     * Hook appele par le webhook {@code checkout.session.completed} pour un
     * Payment CARD : marque COMPLETED sans appel HTTP supplementaire (la
     * SubscriptionEntity est creee par CheckoutCompletedHandler, donc on
     * skip le ensureLocalSubscription ici).
     */
    @Transactional
    public void markCompletedByStripeSession(String stripeSessionId, String validatedBy) {
        paymentRepo.findByStripePaymentId(stripeSessionId).ifPresent(p -> {
            if ("COMPLETED".equals(p.getStatus())) return;
            p.setStatus("COMPLETED");
            p.setCompletedAt(Instant.now());
            p.setValidatedBy(validatedBy == null ? "stripe-webhook" : validatedBy);
            paymentRepo.save(p);
            // Identifiants emis en post-payment CARD (webhook already activated workspace
            // via CheckoutCompletedHandler -> WorkspaceStatusUpdater.activate).
            workspaceUpdater.issueCredentials(p.getWorkspaceId(), "PAYMENT_VALIDATED_CARD");
            log.info("Payment CARD marque COMPLETED via webhook session={} payment={}",
                    stripeSessionId, p.getId());
        });
    }

    private void ensureLocalSubscription(PaymentEntity p, Instant periodEnd) {
        if ("CARD".equals(p.getMethod())) {
            // CARD : la sub a deja ete persistee par CheckoutCompletedHandler.
            return;
        }
        if (subscriptionRepo.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(
                p.getWorkspaceId(), "active").isPresent()) {
            return; // idempotent
        }
        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setWorkspaceId(p.getWorkspaceId());
        sub.setStripeCustomerId("manual-" + p.getWorkspaceId().toString().substring(0, 8));
        sub.setStripeSubscriptionId("manual-PAY-" + p.getId());
        sub.setPlanCode(p.getPlanCode());
        sub.setStatus("active");
        sub.setCurrentPeriodStart(Instant.now());
        sub.setCurrentPeriodEnd(periodEnd);
        subscriptionRepo.save(sub);
        log.info("Shadow subscription creee pour paiement hors-ligne workspace={} method={} plan={}",
                p.getWorkspaceId(), p.getMethod(), p.getPlanCode());
    }

    private static Instant computePeriodEnd(PaymentEntity p) {
        long secondsInDay = 24L * 3600L;
        long days = "yearly".equalsIgnoreCase(p.getBillingPeriod()) ? 365L : 30L;
        return Instant.now().plusSeconds(days * secondsInDay);
    }

    private static String currentCallerEmail() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            return a == null ? "anonymous" : a.getName();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }
}

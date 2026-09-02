package ma.jurika.billing.api.dto;

import ma.jurika.billing.infrastructure.persistence.InvoiceEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentMethodEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;

import java.time.Instant;
import java.util.List;

/**
 * Sprint 12 — DTOs Records immutables pour les reponses billing.
 */
public final class BillingDtos {

    private BillingDtos() {}

    public record SubscriptionDto(
            String planCode,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            Instant cancelledAt,
            PaymentMethodDto paymentMethod
    ) {
        public static SubscriptionDto from(SubscriptionEntity sub, PaymentMethodEntity pm) {
            return new SubscriptionDto(
                    sub.getPlanCode(),
                    sub.getStatus(),
                    sub.getCurrentPeriodStart(),
                    sub.getCurrentPeriodEnd(),
                    sub.isCancelAtPeriodEnd(),
                    sub.getCancelledAt(),
                    pm == null ? null : PaymentMethodDto.from(pm)
            );
        }
    }

    public record PaymentMethodDto(
            String brand,
            String last4,
            Integer expMonth,
            Integer expYear
    ) {
        public static PaymentMethodDto from(PaymentMethodEntity pm) {
            return new PaymentMethodDto(pm.getBrand(), pm.getLast4(), pm.getExpMonth(), pm.getExpYear());
        }
    }

    public record InvoiceDto(
            Long id,
            String number,
            long amountTtcCents,
            long amountHtCents,
            long amountTvaCents,
            String currency,
            String status,
            Instant issuedAt,
            Instant paidAt,
            String invoicePdfUrl,
            String hostedInvoiceUrl
    ) {
        public static InvoiceDto from(InvoiceEntity i) {
            return new InvoiceDto(
                    i.getId(),
                    i.getNumber(),
                    i.getAmountPaidCents(),
                    i.getAmountHtCents(),
                    i.getAmountTvaCents(),
                    i.getCurrency(),
                    i.getStatus(),
                    i.getIssuedAt(),
                    i.getPaidAt(),
                    i.getInvoicePdfUrl(),
                    i.getHostedInvoiceUrl()
            );
        }
    }

    public record InvoicesPageDto(
            List<InvoiceDto> items,
            long total,
            int page,
            int size
    ) {}

    public record CustomerPortalDto(String url) {}

    public record ContactSalesRequest(
            String workspaceName,
            String contactEmail,
            String contactName,
            String phone,
            String message
    ) {}

    public record ContactSalesResponse(boolean accepted, String message) {}

    // ─── BUG 8 (2026-06-07) — Change Plan ─────────────────────────────────
    public record ChangePlanRequest(String targetPlanCode, String billingPeriod) {}

    public record ChangePlanResponse(
            String previousPlanCode,
            String newPlanCode,
            String billingPeriod,
            String stripeSubscriptionId,
            java.time.Instant currentPeriodEnd
    ) {}

    public record ChangePlanPreviewResponse(
            String fromPlan, String toPlan, String billingPeriod,
            boolean isUpgrade, boolean isDowngrade,
            boolean downgradeAllowed, String blockedReason,
            Integer fromPriceMad, Integer toPriceMad
    ) {}

    // ─── BUG 14 (2026-06-07) — Multi-method payments ──────────────────────
    public record PreparePaymentRequest(
            String planCode,
            String billingPeriod,
            String method,
            String contactEmail,
            String workspaceName,
            // Optionnel : referent paiement (utile pour CHEQUE/BANK_TRANSFER en complement)
            String reference
    ) {}

    public record PaymentInstructionsDto(
            String key,
            String value
    ) {}

    public record PreparePaymentResponse(
            Long paymentId,
            String method,
            String status,
            String planCode,
            String billingPeriod,
            Long amountMadCents,
            String currency,
            // Pour CARD : checkoutUrl Stripe a suivre immediatement.
            String checkoutUrl,
            // Pour BANK_TRANSFER/CHEQUE/CASH : instructions textuelles a afficher.
            java.util.List<PaymentInstructionsDto> instructions,
            // Message UI a afficher (ex. "Paiement en attente de validation").
            String userMessage
    ) {}

    public record PaymentDto(
            Long id,
            String planCode,
            String billingPeriod,
            Long amountMadCents,
            String currency,
            String method,
            String status,
            String stripePaymentId,
            String bankReference,
            String chequeNumber,
            String chequeDate,
            String proofUrl,
            java.time.Instant createdAt,
            java.time.Instant completedAt,
            String validatedBy,
            String notes
    ) {}

    public record PaymentsListDto(java.util.List<PaymentDto> items) {}

    public record ValidatePaymentRequest(
            String notes
    ) {}
}

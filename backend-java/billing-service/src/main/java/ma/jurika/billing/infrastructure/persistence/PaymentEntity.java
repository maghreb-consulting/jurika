package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * BUG 14 (2026-06-07) — paiement multi-moyens (CARD / BANK_TRANSFER /
 * CHEQUE / CASH) initie depuis la PaymentPage. Le row PENDING est cree
 * par {@code PreparePaymentUseCase}, valide ensuite soit par le webhook
 * Stripe (CARD), soit par {@code ValidatePaymentUseCase} (hors-ligne).
 *
 * <p>Status :
 *  - {@code PENDING}   : attend validation
 *  - {@code COMPLETED} : declenche activation workspace + envoi identifiants
 *  - {@code FAILED}    : Stripe rejet ou back-office refus
 *  - {@code CANCELLED} : user a abandonne ou retire par admin
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod = "monthly";

    @Column(name = "amount_mad_cents", nullable = false)
    private long amountMadCents;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "mad";

    /** CARD | BANK_TRANSFER | CHEQUE | CASH */
    @Column(name = "method", nullable = false, length = 30)
    private String method;

    /** PENDING | COMPLETED | FAILED | CANCELLED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "stripe_checkout_url", length = 2048)
    private String stripeCheckoutUrl;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "cheque_number", length = 50)
    private String chequeNumber;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "proof_url", length = 2048)
    private String proofUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String v) { this.planCode = v; }
    public String getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(String v) { this.billingPeriod = v; }
    public long getAmountMadCents() { return amountMadCents; }
    public void setAmountMadCents(long v) { this.amountMadCents = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { this.method = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getStripePaymentId() { return stripePaymentId; }
    public void setStripePaymentId(String v) { this.stripePaymentId = v; }
    public String getStripeCheckoutUrl() { return stripeCheckoutUrl; }
    public void setStripeCheckoutUrl(String v) { this.stripeCheckoutUrl = v; }
    public String getBankReference() { return bankReference; }
    public void setBankReference(String v) { this.bankReference = v; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String v) { this.chequeNumber = v; }
    public LocalDate getChequeDate() { return chequeDate; }
    public void setChequeDate(LocalDate v) { this.chequeDate = v; }
    public String getProofUrl() { return proofUrl; }
    public void setProofUrl(String v) { this.proofUrl = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getValidatedBy() { return validatedBy; }
    public void setValidatedBy(String v) { this.validatedBy = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

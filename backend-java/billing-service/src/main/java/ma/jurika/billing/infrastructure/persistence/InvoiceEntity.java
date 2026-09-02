package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — miroir local des invoices Stripe (RG-BL05, RG-BL12).
 * Le PDF reste hoste cote Stripe (invoice_pdf_url).
 */
@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "stripe_invoice_id", nullable = false, unique = true)
    private String stripeInvoiceId;

    @Column(name = "number", nullable = false)
    private String number;

    @Column(name = "amount_due_cents", nullable = false)
    private long amountDueCents;

    @Column(name = "amount_paid_cents", nullable = false)
    private long amountPaidCents = 0L;

    @Column(name = "amount_ht_cents", nullable = false)
    private long amountHtCents = 0L;

    @Column(name = "amount_tva_cents", nullable = false)
    private long amountTvaCents = 0L;

    @Column(name = "currency", nullable = false)
    private String currency = "mad";

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "tva_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal tvaRate = new BigDecimal("20.00");

    @Column(name = "invoice_pdf_url", length = 1024)
    private String invoicePdfUrl;

    @Column(name = "hosted_invoice_url", length = 1024)
    private String hostedInvoiceUrl;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long v) { this.subscriptionId = v; }
    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public void setStripeInvoiceId(String v) { this.stripeInvoiceId = v; }
    public String getNumber() { return number; }
    public void setNumber(String v) { this.number = v; }
    public long getAmountDueCents() { return amountDueCents; }
    public void setAmountDueCents(long v) { this.amountDueCents = v; }
    public long getAmountPaidCents() { return amountPaidCents; }
    public void setAmountPaidCents(long v) { this.amountPaidCents = v; }
    public long getAmountHtCents() { return amountHtCents; }
    public void setAmountHtCents(long v) { this.amountHtCents = v; }
    public long getAmountTvaCents() { return amountTvaCents; }
    public void setAmountTvaCents(long v) { this.amountTvaCents = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getTvaRate() { return tvaRate; }
    public void setTvaRate(BigDecimal v) { this.tvaRate = v; }
    public String getInvoicePdfUrl() { return invoicePdfUrl; }
    public void setInvoicePdfUrl(String v) { this.invoicePdfUrl = v; }
    public String getHostedInvoiceUrl() { return hostedInvoiceUrl; }
    public void setHostedInvoiceUrl(String v) { this.hostedInvoiceUrl = v; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant v) { this.issuedAt = v; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}

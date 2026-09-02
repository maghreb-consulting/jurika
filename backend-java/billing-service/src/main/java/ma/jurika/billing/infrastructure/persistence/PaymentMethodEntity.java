package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 12 — reference vers le Stripe payment method (PCI compliant : aucun
 * PAN ne touche notre DB).
 */
@Entity
@Table(name = "payment_methods")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "stripe_payment_method_id", nullable = false, unique = true)
    private String stripePaymentMethodId;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "type", nullable = false)
    private String type = "card";

    @Column(name = "brand")
    private String brand;

    @Column(name = "last4", length = 4)
    private String last4;

    @Column(name = "exp_month")
    private Integer expMonth;

    @Column(name = "exp_year")
    private Integer expYear;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getStripePaymentMethodId() { return stripePaymentMethodId; }
    public void setStripePaymentMethodId(String v) { this.stripePaymentMethodId = v; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String v) { this.stripeCustomerId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getBrand() { return brand; }
    public void setBrand(String v) { this.brand = v; }
    public String getLast4() { return last4; }
    public void setLast4(String v) { this.last4 = v; }
    public Integer getExpMonth() { return expMonth; }
    public void setExpMonth(Integer v) { this.expMonth = v; }
    public Integer getExpYear() { return expYear; }
    public void setExpYear(Integer v) { this.expYear = v; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean v) { this.isDefault = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}

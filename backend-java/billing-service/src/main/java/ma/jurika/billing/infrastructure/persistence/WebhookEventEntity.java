package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Sprint 12 — store-and-forward des events Stripe (decision archi #6).
 *
 * <p>Le {@code stripe_event_id} unique sert d'idempotency : si le meme event
 * arrive 10 fois (Stripe retry), on insert 1 fois grace a {@code UNIQUE}.
 * Le caller catch {@code DataIntegrityViolationException} et skip
 * silencieusement (acceptance criteria : 10x meme event = 1 invoice).
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, unique = true)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getStripeEventId() { return stripeEventId; }
    public void setStripeEventId(String v) { this.stripeEventId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean v) { this.processed = v; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant v) { this.processedAt = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { this.attempts = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant v) { this.receivedAt = v; }
}

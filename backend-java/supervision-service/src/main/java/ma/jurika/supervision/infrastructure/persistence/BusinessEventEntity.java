package ma.jurika.supervision.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 11 TASK 6 — Event business persiste dans business_events.
 *
 * Source unique (Sprint 11 MVP) : POST /internal/events ingere depuis
 * marketing-site, frontend-react, et auth-service via {@code BusinessEventEmitter}.
 */
@Entity
@Table(name = "business_events")
public class BusinessEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id")
    private UUID workspaceId; // nullable pour events anonymes

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> properties = new HashMap<>();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 20)
    private String source = "backend";

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> v) { this.properties = v == null ? new HashMap<>() : v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { this.occurredAt = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant v) { this.receivedAt = v; }
}

package ma.jurika.dashboard.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dashboard_snapshots")
public class DashboardSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(name = "actor_id")
    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (generatedAt == null) generatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { this.scope = v; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID v) { this.actorId = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant v) { this.generatedAt = v; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant v) { this.validUntil = v; }
}

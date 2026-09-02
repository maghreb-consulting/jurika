package ma.jurika.dashboard.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Lot IA-1 — Briefing du jour de l'agent copilote (table {@code agent_briefings}).
 *
 * <p>Le {@code payload} est stocke en JSONB (signaux bruts + plan du jour + texte
 * LLM), serialise/deserialise par le service via Jackson. Meme mapping que
 * {@code DashboardSnapshotEntity} : {@code @JdbcTypeCode(SqlTypes.JSON)} sur un
 * champ {@code String} (Hibernate 6 natif, pas de dependance supplementaire).
 */
@Entity
@Table(name = "agent_briefings")
public class AgentBriefingEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "seen_at")
    private Instant seenAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant v) { this.seenAt = v; }
}

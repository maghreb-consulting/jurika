package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "deadlines")
public class DeadlineEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id")
    private UUID ticketId;
    @Column(name = "dossier_id")
    private UUID dossierId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(nullable = false, length = 10)
    private String severity;
    @Column(nullable = false, length = 10)
    private String source;
    @Column(name = "rule_key", length = 60)
    private String ruleKey;
    @Column(nullable = false, length = 20)
    private String statut;
    @Column(name = "assigne_id")
    private UUID assigneId;
    @Column(name = "cree_par_id")
    private UUID creeParId;
    @Column(name = "terminee_at")
    private Instant termineeAt;
    @Column(name = "ignoree_at")
    private Instant ignoreeAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (statut == null) statut = "OUVERTE";
        if (severity == null) severity = "INFO";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant v) { this.dueAt = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String v) { this.ruleKey = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public UUID getAssigneId() { return assigneId; }
    public void setAssigneId(UUID v) { this.assigneId = v; }
    public UUID getCreeParId() { return creeParId; }
    public void setCreeParId(UUID v) { this.creeParId = v; }
    public Instant getTermineeAt() { return termineeAt; }
    public void setTermineeAt(Instant v) { this.termineeAt = v; }
    public Instant getIgnoreeAt() { return ignoreeAt; }
    public void setIgnoreeAt(Instant v) { this.ignoreeAt = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

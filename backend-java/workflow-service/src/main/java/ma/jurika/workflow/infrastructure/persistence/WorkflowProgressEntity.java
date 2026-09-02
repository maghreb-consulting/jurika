package ma.jurika.workflow.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_progress")
public class WorkflowProgressEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "workflow_type", nullable = false, length = 40)
    private String workflowType;
    @Column(name = "current_step", nullable = false)
    private short currentStep;
    @Column(name = "total_steps", nullable = false)
    private short totalSteps;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;
    @Column(nullable = false, length = 20)
    private String statut;
    @Column(name = "started_by_id", nullable = false)
    private UUID startedById;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (statut == null) statut = "EN_COURS";
        if (currentStep == 0) currentStep = 1;
        if (data == null) data = new HashMap<>();
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String v) { this.workflowType = v; }
    public short getCurrentStep() { return currentStep; }
    public void setCurrentStep(short v) { this.currentStep = v; }
    public short getTotalSteps() { return totalSteps; }
    public void setTotalSteps(short v) { this.totalSteps = v; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> v) { this.data = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public UUID getStartedById() { return startedById; }
    public void setStartedById(UUID v) { this.startedById = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dossier_transfert_requests")
public class DossierTransfertRequestEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;
    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;
    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;
    @Column(nullable = false, length = 20)
    private String statut;
    @Column(nullable = false)
    private boolean direct;
    @Column(columnDefinition = "TEXT")
    private String motif;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (statut == null) statut = "EN_ATTENTE";
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID v) { this.fromUserId = v; }
    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID v) { this.toUserId = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public boolean isDirect() { return direct; }
    public void setDirect(boolean v) { this.direct = v; }
    public String getMotif() { return motif; }
    public void setMotif(String v) { this.motif = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { this.decidedAt = v; }
}

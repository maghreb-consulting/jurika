package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_document_snapshots")
public class TicketSnapshotEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "snapshot_kind", nullable = false, length = 20)
    private String snapshotKind;
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (capturedAt == null) capturedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getSnapshotKind() { return snapshotKind; }
    public void setSnapshotKind(String v) { this.snapshotKind = v; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant v) { this.capturedAt = v; }
}

package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
public class TicketCommentEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "auteur_id", nullable = false)
    private UUID auteurId;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (type == null) type = "COMMENTAIRE";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public UUID getAuteurId() { return auteurId; }
    public void setAuteurId(UUID v) { this.auteurId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getContenu() { return contenu; }
    public void setContenu(String v) { this.contenu = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }
    public Instant getCreatedAt() { return createdAt; }
}

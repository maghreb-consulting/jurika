package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 7 / TASK 5 -- Trace detaillee des acces client au Data Room.
 *
 * Voir V11__rg_dr_access_log.sql pour le schema. La colonne ip_address est
 * INET cote DB ; on la mappe en VARCHAR cote JPA (insert/select fonctionnels,
 * comportement de comparaison reste cote SQL).
 */
@Entity
@Table(name = "dataroom_client_access_log")
public class ClientAccessLogEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 20)
    private String action;

    /** Mappe INET en VARCHAR pour simplifier JPA -- PG accepte le cast. */
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { this.ipAddress = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { this.userAgent = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}

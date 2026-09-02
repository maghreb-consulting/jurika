package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Lot V -- un depot libre du CLIENT dans l'espace « Depots » (miroir de
 * {@link ComptableDocumentEntity} sans annee/categorie/exercice). Le fichier
 * appartient a un dossier ; il est consulte par l'employe responsable.
 */
@Entity
@Table(name = "dataroom_depots")
public class DepotEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;
    @Column(length = 200)
    private String title;
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;
    @Column(nullable = false, length = 255)
    private String filename;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;
    @Column(name = "deleted_at")
    private Instant deletedAt;
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
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String v) { this.objectKey = v; }
    public String getFilename() { return filename; }
    public void setFilename(String v) { this.filename = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID v) { this.uploadedBy = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}

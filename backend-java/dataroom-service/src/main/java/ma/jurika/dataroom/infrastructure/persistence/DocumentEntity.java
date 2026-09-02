package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataroom_documents")
public class DocumentEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;
    @Column(name = "ticket_id")
    private UUID ticketId;
    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false)
    private short version;
    @Column(name = "is_current", nullable = false)
    private boolean current;
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;
    @Column(nullable = false, length = 255)
    private String filename;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "replaced_at")
    private Instant replacedAt;

    /**
     * Sprint 2026-06-23 (V17) — raison du remplacement / de la création de la
     * version. Champ libre, NULL accepté (compat retro + uploads sans motif).
     * Exemples : "Modification : Transfert du siège", "Correction typo",
     * "Restoration de la version 2 — la version 3 contenait un mauvais montant".
     */
    @Column(name = "motif", columnDefinition = "text")
    private String motif;

    /**
     * Sprint 7 / RG-DR-FTS : colonne tsvector GENERATED ALWAYS AS ... STORED (V10).
     * Mappee en read-only pour cohabiter avec hibernate.ddl-auto=validate sans
     * que JPA tente jamais d'ecrire dedans. Aucun getter expose (usage strict
     * via @Query nativeQuery dans DocumentJpaRepository).
     */
    @Column(name = "search_vector", insertable = false, updatable = false,
            columnDefinition = "tsvector")
    @SuppressWarnings("unused")
    private String searchVector;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (version == 0) version = 1;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String v) { this.documentType = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public short getVersion() { return version; }
    public void setVersion(short v) { this.version = v; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean v) { this.current = v; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getReplacedAt() { return replacedAt; }
    public void setReplacedAt(Instant v) { this.replacedAt = v; }
    public String getMotif() { return motif; }
    public void setMotif(String v) { this.motif = v; }
}

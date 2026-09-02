package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 8 -- table dataroom_fiscal_documents (V13).
 * 7 categories CGI Maroc, sous-classification obligatoire, retention 10 ans
 * (CGI Art. 211), notification immediate CONTENTIEUX (RG-DF24).
 */
@Entity
@Table(name = "dataroom_fiscal_documents")
public class FiscalDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "exercice_fiscal_id", nullable = false)
    private UUID exerciceFiscalId;

    @Column(nullable = false, length = 20)
    private String categorie;

    @Column(name = "sous_classification", nullable = false, length = 40)
    private String sousClassification;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "tif_metadata", length = 20)
    private String tifMetadata;

    @Column(name = "numero_declaration", length = 60)
    private String numeroDeclaration;

    @Column(name = "periode_declaree", length = 20)
    private String periodeDeclaree;

    @Column(name = "comptable_doc_source")
    private UUID comptableDocSource;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getExerciceFiscalId() { return exerciceFiscalId; }
    public void setExerciceFiscalId(UUID v) { this.exerciceFiscalId = v; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String v) { this.categorie = v; }
    public String getSousClassification() { return sousClassification; }
    public void setSousClassification(String v) { this.sousClassification = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String v) { this.commentaire = v; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String v) { this.objectKey = v; }
    public String getFilename() { return filename; }
    public void setFilename(String v) { this.filename = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public String getTifMetadata() { return tifMetadata; }
    public void setTifMetadata(String v) { this.tifMetadata = v; }
    public String getNumeroDeclaration() { return numeroDeclaration; }
    public void setNumeroDeclaration(String v) { this.numeroDeclaration = v; }
    public String getPeriodeDeclaree() { return periodeDeclaree; }
    public void setPeriodeDeclaree(String v) { this.periodeDeclaree = v; }
    public UUID getComptableDocSource() { return comptableDocSource; }
    public void setComptableDocSource(UUID v) { this.comptableDocSource = v; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID v) { this.uploadedBy = v; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID v) { this.deletedBy = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

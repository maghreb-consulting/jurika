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
    /** Rangement dans le dossier du ticket (V24). NULL = nature non deductible. */
    @Column(name = "groupe", length = 30)
    private String groupe;
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
     * Lot 2 (V26) — document GENERE par un workflow et pas encore valide par
     * l'employe.
     *
     * <p>Un brouillon est stocke exactement comme un document normal (meme
     * table, meme objet MinIO) mais reste invisible du dossier juridique : il
     * est exclu des documents en vigueur, du regroupement par ticket, du
     * lignage des versions et de la recherche. La validation ne le recopie pas,
     * elle bascule ce drapeau — le document garde son identite et emprunte
     * ensuite le versionnement juridique existant.
     *
     * <p>La base garantit qu'un brouillon n'est jamais courant et porte toujours
     * un ticket (contraintes CHECK de V26).
     */
    @Column(name = "brouillon", nullable = false)
    private boolean brouillon;

    /**
     * Lot 3 (V27) — date de la derniere edition manuelle dans Collabora.
     * {@code null} = document tel que genere par le moteur.
     *
     * <p>Cet etat est VISIBLE et PERSISTANT parce qu'il commande un
     * avertissement : regenerer repart des variables et efface les retouches.
     * Sans cette date, l'interface ne pourrait pas nommer ce qui sera perdu, et
     * un avertissement generique se clique sans se lire.
     */
    @Column(name = "edite_manuellement_at")
    private Instant editeManuellementAt;

    /** Qui a edite en dernier — la trace « qui et quand » vit sur l'acte. */
    @Column(name = "edite_par")
    private UUID editePar;

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
    public String getGroupe() { return groupe; }
    public void setGroupe(String v) { this.groupe = v; }
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
    public boolean isBrouillon() { return brouillon; }
    public void setBrouillon(boolean v) { this.brouillon = v; }
    public Instant getEditeManuellementAt() { return editeManuellementAt; }
    public void setEditeManuellementAt(Instant v) { this.editeManuellementAt = v; }
    public UUID getEditePar() { return editePar; }
    public void setEditePar(UUID v) { this.editePar = v; }
}

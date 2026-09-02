package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ticket_debours")
public class DeboursEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(nullable = false, length = 200)
    private String libelle;
    @Column(nullable = false, length = 40)
    private String categorie;
    @Column(name = "montant_mad", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantMad;
    @Column(name = "date_engagement", nullable = false)
    private LocalDate dateEngagement;
    @Column(name = "piece_jointe_url", columnDefinition = "TEXT")
    private String pieceJointeUrl;
    @Column(name = "piece_jointe_filename", length = 255)
    private String pieceJointeFilename;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;
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
    public String getLibelle() { return libelle; }
    public void setLibelle(String v) { this.libelle = v; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String v) { this.categorie = v; }
    public BigDecimal getMontantMad() { return montantMad; }
    public void setMontantMad(BigDecimal v) { this.montantMad = v; }
    public LocalDate getDateEngagement() { return dateEngagement; }
    public void setDateEngagement(LocalDate v) { this.dateEngagement = v; }
    public String getPieceJointeUrl() { return pieceJointeUrl; }
    public void setPieceJointeUrl(String v) { this.pieceJointeUrl = v; }
    public String getPieceJointeFilename() { return pieceJointeFilename; }
    public void setPieceJointeFilename(String v) { this.pieceJointeFilename = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID v) { this.createdById = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

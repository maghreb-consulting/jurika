package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class TicketEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(nullable = false, length = 20)
    private String reference;
    @Column(nullable = false, length = 200)
    private String titre;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 20)
    private String statut;
    @Column(nullable = false, length = 10)
    private String priorite;
    @Column(name = "dossier_id")
    private UUID dossierId;
    @Column(name = "assigne_id")
    private UUID assigneId;
    @Column(name = "cree_par_id", nullable = false)
    private UUID creeParId;
    @Column(columnDefinition = "TEXT")
    private String description;
    private Instant deadline;
    @Column(name = "annulation_motif", columnDefinition = "TEXT")
    private String annulationMotif;
    @Column(name = "cloture_at")
    private Instant clotureAt;
    @Column(name = "annule_at")
    private Instant annuleAt;
    /** V10 — horodatage de reprise lors d'un transfert de dossier (NULL = jamais transfere). */
    @Column(name = "transferred_at")
    private Instant transferredAt;
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
        if (statut == null) statut = "CREATION_TICKET";
        if (priorite == null) priorite = "NORMALE";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public String getTitre() { return titre; }
    public void setTitre(String v) { this.titre = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String v) { this.priorite = v; }
    public UUID getDossierId() { return dossierId; }
    public void setDossierId(UUID v) { this.dossierId = v; }
    public UUID getAssigneId() { return assigneId; }
    public void setAssigneId(UUID v) { this.assigneId = v; }
    public UUID getCreeParId() { return creeParId; }
    public void setCreeParId(UUID v) { this.creeParId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant v) { this.deadline = v; }
    public String getAnnulationMotif() { return annulationMotif; }
    public void setAnnulationMotif(String v) { this.annulationMotif = v; }
    public Instant getClotureAt() { return clotureAt; }
    public void setClotureAt(Instant v) { this.clotureAt = v; }
    public Instant getAnnuleAt() { return annuleAt; }
    public void setAnnuleAt(Instant v) { this.annuleAt = v; }
    public Instant getTransferredAt() { return transferredAt; }
    public void setTransferredAt(Instant v) { this.transferredAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

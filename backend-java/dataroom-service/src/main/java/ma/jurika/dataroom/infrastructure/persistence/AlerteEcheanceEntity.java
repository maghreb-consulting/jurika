package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 8 -- table dataroom_alertes_echeances (V14).
 * RG-DF20 : 10 types d echeances DGI Maroc, alerte J-15 par defaut.
 */
@Entity
@Table(name = "dataroom_alertes_echeances")
public class AlerteEcheanceEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "exercice_fiscal_id", nullable = false)
    private UUID exerciceFiscalId;

    @Column(name = "type_echeance", nullable = false, length = 40)
    private String typeEcheance;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Column(name = "date_alerte", nullable = false)
    private LocalDate dateAlerte;

    @Column(nullable = false, length = 20)
    private String statut;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "traite_par")
    private UUID traitePar;

    @Column(name = "traite_at")
    private Instant traiteAt;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(columnDefinition = "TEXT")
    private String note;

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
        if (statut == null) statut = "PLANIFIEE";
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
    public String getTypeEcheance() { return typeEcheance; }
    public void setTypeEcheance(String v) { this.typeEcheance = v; }
    public LocalDate getDateEcheance() { return dateEcheance; }
    public void setDateEcheance(LocalDate v) { this.dateEcheance = v; }
    public LocalDate getDateAlerte() { return dateAlerte; }
    public void setDateAlerte(LocalDate v) { this.dateAlerte = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { this.sentAt = v; }
    public UUID getTraitePar() { return traitePar; }
    public void setTraitePar(UUID v) { this.traitePar = v; }
    public Instant getTraiteAt() { return traiteAt; }
    public void setTraiteAt(Instant v) { this.traiteAt = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

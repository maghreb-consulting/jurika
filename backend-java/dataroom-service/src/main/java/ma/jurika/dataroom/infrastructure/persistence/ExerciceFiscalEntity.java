package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 7 / TASK 6.1 -- Entite exercice fiscal (V12).
 *
 * En Sprint 7 elle n'est lue qu'en placeholder (ExerciceFiscalSummary
 * dans DossierFiscalView). Sprint 8 ajoutera les transitions de statut
 * (OUVERT -> CLOTURE -> VERROUILLE) + lien avec dataroom_comptable
 * et la future table dataroom_fiscal.
 */
@Entity
@Table(name = "dataroom_exercices_fiscaux")
public class ExerciceFiscalEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(nullable = false)
    private short annee;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false, length = 20)
    private String statut;

    @Column(name = "date_ouverture", nullable = false)
    private Instant dateOuverture;

    @Column(name = "date_cloture")
    private Instant dateCloture;

    @Column(name = "cloture_par")
    private UUID cloturePar;

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
        if (dateOuverture == null) dateOuverture = now;
        if (statut == null) statut = "OUVERT";
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
    public short getAnnee() { return annee; }
    public void setAnnee(short v) { this.annee = v; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate v) { this.dateDebut = v; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate v) { this.dateFin = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public Instant getDateOuverture() { return dateOuverture; }
    public void setDateOuverture(Instant v) { this.dateOuverture = v; }
    public Instant getDateCloture() { return dateCloture; }
    public void setDateCloture(Instant v) { this.dateCloture = v; }
    public UUID getCloturePar() { return cloturePar; }
    public void setCloturePar(UUID v) { this.cloturePar = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

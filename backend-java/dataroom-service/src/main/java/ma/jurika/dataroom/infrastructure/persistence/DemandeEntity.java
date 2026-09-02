package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataroom_demandes_client")
public class DemandeEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;
    @Column(name = "soumis_par")
    private UUID soumisPar;
    @Column(nullable = false, length = 200)
    private String sujet;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, length = 20)
    private String statut;
    @Column(name = "ticket_id")
    private UUID ticketId;
    @Column(name = "pris_en_charge_par")
    private UUID prisEnChargePar;
    @Column(name = "note_interne", columnDefinition = "TEXT")
    private String noteInterne;
    @Column(name = "traite_at")
    private Instant traiteAt;
    // Lot AG -- direction + machine a etats des requetes EMPLOYE_TO_CLIENT.
    @Column(nullable = false, length = 20)
    private String direction;                 // CLIENT_TO_EMPLOYE | EMPLOYE_TO_CLIENT
    @Column(name = "type_requete", length = 20)
    private String typeRequete;               // PIECE | INFO | SIGNATURE (EMPLOYE_TO_CLIENT)
    @Column(name = "repondu_at")
    private Instant reponduAt;
    @Column(name = "cloture_at")
    private Instant clotureAt;
    @Column(name = "note_client", columnDefinition = "TEXT")
    private String noteClient;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (statut == null) statut = "NON_TRAITEE";
        if (direction == null) direction = "CLIENT_TO_EMPLOYE";
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
    public UUID getSoumisPar() { return soumisPar; }
    public void setSoumisPar(UUID v) { this.soumisPar = v; }
    public String getSujet() { return sujet; }
    public void setSujet(String v) { this.sujet = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getStatut() { return statut; }
    public void setStatut(String v) { this.statut = v; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID v) { this.ticketId = v; }
    public UUID getPrisEnChargePar() { return prisEnChargePar; }
    public void setPrisEnChargePar(UUID v) { this.prisEnChargePar = v; }
    public String getNoteInterne() { return noteInterne; }
    public void setNoteInterne(String v) { this.noteInterne = v; }
    public Instant getTraiteAt() { return traiteAt; }
    public void setTraiteAt(Instant v) { this.traiteAt = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { this.direction = v; }
    public String getTypeRequete() { return typeRequete; }
    public void setTypeRequete(String v) { this.typeRequete = v; }
    public Instant getReponduAt() { return reponduAt; }
    public void setReponduAt(Instant v) { this.reponduAt = v; }
    public Instant getClotureAt() { return clotureAt; }
    public void setClotureAt(Instant v) { this.clotureAt = v; }
    public String getNoteClient() { return noteClient; }
    public void setNoteClient(String v) { this.noteClient = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}

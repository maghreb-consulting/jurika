package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Etat d'une demarche du referentiel sur un ticket donne (migration V21). */
@Entity
@Table(name = "ticket_demarches")
public class TicketDemarcheEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "demarche_id", nullable = false)
    private UUID demarcheId;
    @Column(nullable = false, length = 20)
    private String etat;
    @Column(columnDefinition = "TEXT")
    private String motif;
    @Column(name = "acteur_id")
    private UUID acteurId;
    @Column(name = "coche_at")
    private Instant cocheAt;
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
    public UUID getDemarcheId() { return demarcheId; }
    public void setDemarcheId(UUID v) { this.demarcheId = v; }
    public String getEtat() { return etat; }
    public void setEtat(String v) { this.etat = v; }
    public String getMotif() { return motif; }
    public void setMotif(String v) { this.motif = v; }
    public UUID getActeurId() { return acteurId; }
    public void setActeurId(UUID v) { this.acteurId = v; }
    public Instant getCocheAt() { return cocheAt; }
    public void setCocheAt(Instant v) { this.cocheAt = v; }
}

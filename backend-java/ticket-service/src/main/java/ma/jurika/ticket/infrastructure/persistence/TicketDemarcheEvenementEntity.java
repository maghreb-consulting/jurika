package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Un evenement du journal de cochage (migration V25). */
@Entity
@Table(name = "ticket_demarche_evenements")
public class TicketDemarcheEvenementEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_demarche_id", nullable = false)
    private UUID ticketDemarcheId;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(columnDefinition = "TEXT")
    private String motif;
    @Column(name = "acteur_id")
    private UUID acteurId;
    @Column(name = "survenu_le", nullable = false)
    private Instant survenuLe;
    @Column(nullable = false)
    private Short justificatifs;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (survenuLe == null) survenuLe = Instant.now();
        if (justificatifs == null) justificatifs = 0;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketDemarcheId() { return ticketDemarcheId; }
    public void setTicketDemarcheId(UUID v) { this.ticketDemarcheId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getMotif() { return motif; }
    public void setMotif(String v) { this.motif = v; }
    public UUID getActeurId() { return acteurId; }
    public void setActeurId(UUID v) { this.acteurId = v; }
    public Instant getSurvenuLe() { return survenuLe; }
    public void setSurvenuLe(Instant v) { this.survenuLe = v; }
    public Short getJustificatifs() { return justificatifs; }
    public void setJustificatifs(Short v) { this.justificatifs = v; }
}

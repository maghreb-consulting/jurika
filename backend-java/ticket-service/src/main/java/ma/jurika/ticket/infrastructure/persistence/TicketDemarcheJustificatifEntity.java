package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Rattachement explicite d'un document de la Data Room a une demarche cochee
 * (migration V21).
 *
 * <p>Le rattachement ne se deduit PAS du type : les etapes 13, 14 et 17 attendent
 * toutes un document de type STATUTS (signe, puis legalise, puis enregistre).
 * Sans cette table, cocher l'etape 17 passerait au seul motif qu'un STATUTS
 * existe deja depuis l'etape 13 -- le controle serait vide.
 */
@Entity
@Table(name = "ticket_demarche_justificatifs")
public class TicketDemarcheJustificatifEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "ticket_demarche_id", nullable = false)
    private UUID ticketDemarcheId;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "document_type", nullable = false, length = 60)
    private String documentType;
    @Column(name = "alternative_groupe", nullable = false)
    private Short alternativeGroupe;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public UUID getTicketDemarcheId() { return ticketDemarcheId; }
    public void setTicketDemarcheId(UUID v) { this.ticketDemarcheId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String v) { this.documentType = v; }
    public Short getAlternativeGroupe() { return alternativeGroupe; }
    public void setAlternativeGroupe(Short v) { this.alternativeGroupe = v; }
}

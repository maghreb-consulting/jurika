package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Vue read-only sur `dataroom_documents` (table du dataroom-service). Permet de
 * verifier COTE SERVEUR qu'un justificatif a bien ete televerse avant de cocher
 * une demarche, sans appel HTTP inter-services.
 *
 * <p>Meme pattern que {@link UserViewEntity} et {@link WorkspaceViewEntity} :
 * toutes les colonnes sont {@code insertable = false, updatable = false} --
 * ecrire dans cette table reste du seul ressort du dataroom-service.
 */
@Entity
@Table(name = "dataroom_documents")
public class DataroomDocumentViewEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", insertable = false, updatable = false)
    private UUID workspaceId;
    @Column(name = "dossier_id", insertable = false, updatable = false)
    private UUID dossierId;
    @Column(name = "ticket_id", insertable = false, updatable = false)
    private UUID ticketId;
    @Column(name = "document_type", insertable = false, updatable = false)
    private String documentType;
    @Column(insertable = false, updatable = false)
    private String title;
    @Column(name = "is_current", insertable = false, updatable = false)
    private boolean current;
    /** Lot B — l'indicateur « visible pour le client », porte par le document. */
    @Column(name = "visible_client", insertable = false, updatable = false)
    private boolean visibleClient;

    public boolean isCurrent() { return current; }
    public boolean isVisibleClient() { return visibleClient; }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getDossierId() { return dossierId; }
    public UUID getTicketId() { return ticketId; }
    public String getDocumentType() { return documentType; }
    public String getTitle() { return title; }
}

package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class Ticket {

    private final UUID id;
    private final UUID workspaceId;
    private final String reference;
    private final String titre;
    private final TicketType type;
    private final TicketStatut statut;
    private final TicketPriorite priorite;
    private final UUID dossierId;
    private final UUID assigneId;
    private final UUID creeParId;
    private final String description;
    private final Instant deadline;
    private final String annulationMotif;
    private final Instant clotureAt;
    private final Instant annuleAt;
    /** V10 — horodatage de reprise lors d'un transfert de dossier (null = jamais transfere). */
    private final Instant transferredAt;
    private final Instant createdAt;

    /**
     * Constructeur historique (sans transfert) : delegue avec transferredAt=null.
     * Conserve pour ne pas casser les appelants existants (tests, fabriques).
     */
    public Ticket(UUID id, UUID workspaceId, String reference, String titre,
                  TicketType type, TicketStatut statut, TicketPriorite priorite,
                  UUID dossierId, UUID assigneId, UUID creeParId, String description,
                  Instant deadline, String annulationMotif, Instant clotureAt,
                  Instant annuleAt, Instant createdAt) {
        this(id, workspaceId, reference, titre, type, statut, priorite, dossierId,
                assigneId, creeParId, description, deadline, annulationMotif, clotureAt,
                annuleAt, null, createdAt);
    }

    public Ticket(UUID id, UUID workspaceId, String reference, String titre,
                  TicketType type, TicketStatut statut, TicketPriorite priorite,
                  UUID dossierId, UUID assigneId, UUID creeParId, String description,
                  Instant deadline, String annulationMotif, Instant clotureAt,
                  Instant annuleAt, Instant transferredAt, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.reference = reference;
        this.titre = titre;
        this.type = type;
        this.statut = statut;
        this.priorite = priorite;
        this.dossierId = dossierId;
        this.assigneId = assigneId;
        this.creeParId = creeParId;
        this.description = description;
        this.deadline = deadline;
        this.annulationMotif = annulationMotif;
        this.clotureAt = clotureAt;
        this.annuleAt = annuleAt;
        this.transferredAt = transferredAt;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public String reference() { return reference; }
    public String titre() { return titre; }
    public TicketType type() { return type; }
    public TicketStatut statut() { return statut; }
    public TicketPriorite priorite() { return priorite; }
    public UUID dossierId() { return dossierId; }
    public UUID assigneId() { return assigneId; }
    public UUID creeParId() { return creeParId; }
    public String description() { return description; }
    public Instant deadline() { return deadline; }
    public String annulationMotif() { return annulationMotif; }
    public Instant clotureAt() { return clotureAt; }
    public Instant annuleAt() { return annuleAt; }
    public Instant transferredAt() { return transferredAt; }
    public Instant createdAt() { return createdAt; }
}

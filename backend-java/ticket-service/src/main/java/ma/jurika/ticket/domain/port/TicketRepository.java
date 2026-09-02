package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {

    Optional<Ticket> findById(UUID workspaceId, UUID ticketId);

    Ticket create(UUID workspaceId, String reference, String titre, TicketType type,
                  TicketPriorite priorite, UUID dossierId, UUID assigneId, UUID creeParId,
                  String description, Instant deadline);

    Ticket updateStatut(UUID ticketId, TicketStatut statut, String annulationMotif,
                         Instant clotureAt, Instant annuleAt);

    Ticket updateAssignment(UUID ticketId, UUID assigneId, TicketPriorite priorite,
                             Instant deadline, String titre, String description);

    /**
     * Reassigne un ticket dans le cadre d'un transfert de dossier et l'horodate
     * comme "transfere" ({@code transferred_at}). Le statut n'est jamais touche.
     */
    Ticket markTransferred(UUID ticketId, UUID assigneId, Instant transferredAt);

    List<Ticket> search(TicketFilter filter, int limit, int offset);

    long count(TicketFilter filter);

    String generateReference();
}

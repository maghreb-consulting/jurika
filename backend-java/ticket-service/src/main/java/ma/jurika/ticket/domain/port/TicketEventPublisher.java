package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;

public interface TicketEventPublisher {

    void publishTicketCreated(Ticket ticket);

    void publishTicketStatusChanged(Ticket ticket, TicketStatut previous);

    void publishTicketAssigned(Ticket ticket, java.util.UUID previousAssigneId);
}

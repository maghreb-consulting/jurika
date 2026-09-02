package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;

public interface TicketStatutHandler {

    TicketStatut statut();

    void validateTransition(Ticket current, TransitionContext ctx);

    record TransitionContext(String comment, java.util.UUID actorUserId) {}
}

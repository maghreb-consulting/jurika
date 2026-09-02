package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

@Component
public class NouveauHandler implements TicketStatutHandler {

    @Override
    public TicketStatut statut() {
        return TicketStatut.NOUVEAU;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        if (!current.statut().canTransitionTo(TicketStatut.NOUVEAU)) {
            throw new IllegalStateException(
                    "Transition impossible : " + current.statut() + " -> NOUVEAU");
        }
    }
}

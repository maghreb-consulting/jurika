package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Component;

/**
 * Statut 1 — « Creation du ticket ».
 *
 * <p>Premier statut du parcours : on n'y revient que par REPRISE d'un ticket
 * annule ({@code predecesseur() == null}), jamais depuis un statut plus avance.
 */
@Component
public class CreationTicketHandler extends ParcoursHandler {

    public CreationTicketHandler(TransitionChecks checks) {
        super(checks);
    }

    @Override
    public TicketStatut statut() {
        return TicketStatut.CREATION_TICKET;
    }

    @Override
    protected TicketStatut predecesseur() {
        return null;
    }
}

package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

@Component
public class ClotureHandler implements TicketStatutHandler {

    @Override
    public TicketStatut statut() {
        return TicketStatut.CLOTURE;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        TicketStatut source = current.statut();
        // 2026-06-25 — CLOTURE atteignable depuis EN_COURS (cloture normale) OU
        // ANNULE (cloture d'un ticket precedemment annule).
        if (source != TicketStatut.EN_COURS && source != TicketStatut.ANNULE) {
            throw new ConflictException("Seul un ticket EN_COURS ou ANNULE peut etre cloture");
        }
        // Cloture depuis ANNULE : transition sensible, motif non vide obligatoire.
        if (source == TicketStatut.ANNULE
                && (ctx.comment() == null || ctx.comment().trim().isEmpty())) {
            throw new ValidationException("Motif obligatoire pour cloturer un ticket annule");
        }
    }
}

package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

@Component
public class EnCoursHandler implements TicketStatutHandler {

    @Override
    public TicketStatut statut() {
        return TicketStatut.EN_COURS;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        TicketStatut source = current.statut();
        // 2026-06-25 — EN_COURS atteignable depuis NOUVEAU (prise en charge) OU
        // ANNULE (reprise d'un ticket annule).
        if (source != TicketStatut.NOUVEAU && source != TicketStatut.ANNULE) {
            throw new ConflictException("Seul un ticket NOUVEAU ou ANNULE peut passer EN_COURS");
        }
        if (source == TicketStatut.NOUVEAU && current.assigneId() == null) {
            throw new ConflictException("Le ticket doit etre assigne avant de passer EN_COURS");
        }
        // Reprise (ANNULE -> EN_COURS) : transition sensible, motif non vide
        // obligatoire. L'exigence d'assigne est assouplie : le use case
        // reassigne le ticket a l'acteur de la reprise si besoin.
        if (source == TicketStatut.ANNULE
                && (ctx.comment() == null || ctx.comment().trim().isEmpty())) {
            throw new ValidationException("Motif obligatoire pour reprendre un ticket annule");
        }
    }
}

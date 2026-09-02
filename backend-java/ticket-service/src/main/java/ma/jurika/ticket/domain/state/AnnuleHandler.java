package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

@Component
public class AnnuleHandler implements TicketStatutHandler {

    private static final int MIN_COMMENT_LENGTH = 10;

    @Override
    public TicketStatut statut() {
        return TicketStatut.ANNULE;
    }

    @Override
    public void validateTransition(Ticket current, TransitionContext ctx) {
        // 2026-06-25 — TOUT ticket peut etre annule, y compris CLOTURE (le blocage
        // "un CLOTURE ne peut plus etre annule" a ete retire). La double-annulation
        // (ANNULE -> ANNULE) est deja gere en amont : no-op idempotent dans le use
        // case + "transition invalide" dans la machine a etats.
        // Transition sensible : motif obligatoire (>= 10 caracteres) quelle que
        // soit la source, ce qui couvre aussi CLOTURE -> ANNULE.
        if (ctx.comment() == null || ctx.comment().trim().length() < MIN_COMMENT_LENGTH) {
            throw new ValidationException(
                    "Commentaire d'annulation obligatoire (min " + MIN_COMMENT_LENGTH + " caracteres)");
        }
    }
}

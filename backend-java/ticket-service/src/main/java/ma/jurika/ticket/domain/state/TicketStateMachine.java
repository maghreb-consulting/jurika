package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TicketStateMachine {

    private final Map<TicketStatut, TicketStatutHandler> handlers;

    public TicketStateMachine(List<TicketStatutHandler> handlerList) {
        EnumMap<TicketStatut, TicketStatutHandler> map = new EnumMap<>(TicketStatut.class);
        for (TicketStatutHandler h : handlerList) {
            map.put(h.statut(), h);
        }
        this.handlers = map;
    }

    public void assertCanTransition(Ticket current, TicketStatut target, TicketStatutHandler.TransitionContext ctx) {
        if (current.statut() == target) {
            throw new ConflictException("Le ticket est d\u00e9j\u00e0 au statut \u00ab " + target.libelle() + " \u00bb.");
        }
        if (!current.statut().canTransitionTo(target)) {
            throw new ConflictException("Transition invalide : le ticket ne peut pas passer du statut \u00ab "
                    + current.statut().libelle() + " \u00bb au statut \u00ab " + target.libelle() + " \u00bb.");
        }
        TicketStatutHandler handler = handlers.get(target);
        if (handler == null) {
            throw new IllegalStateException("Aucun handler pour " + target);
        }
        handler.validateTransition(current, ctx);
    }
}

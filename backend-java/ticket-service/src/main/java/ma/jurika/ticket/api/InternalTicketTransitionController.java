package ma.jurika.ticket.api;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.TransitionTicketUseCase;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot L4c (E1, RG-TKT-02, 06) : transition demandee par un autre service (fin d'etape d'un
 * parcours). Elle passe par la machine d'etats et les controles de passage, comme une
 * transition de l'ecran : plus aucun service ne modifie le statut d'un ticket en base.
 *
 * <p>Reponse : APPLIQUEE, SANS_OBJET (le ticket n'est plus au statut attendu : rien a
 * faire) ou REFUSEE avec le motif (obstacles nommes). Route interne, bloquee a la
 * passerelle.
 */
@RestController
@RequestMapping("/internal/tickets")
public class InternalTicketTransitionController {

    public record Demande(UUID workspaceId, TicketStatut attendu, TicketStatut cible, String raison, UUID acteurId) {}

    public record Reponse(String resultat, TicketStatut statut, String motif) {}

    private final TransitionTicketUseCase transitions;
    private final TicketRepository tickets;

    public InternalTicketTransitionController(TransitionTicketUseCase transitions, TicketRepository tickets) {
        this.transitions = transitions;
        this.tickets = tickets;
    }

    @PostMapping("/{ticketId}/transition")
    public Reponse transition(@PathVariable UUID ticketId, @RequestBody Demande d) {
        TenantContext.set(d.workspaceId());
        Ticket t = tickets.findById(d.workspaceId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (d.attendu() != null && t.statut() != d.attendu()) {
            return new Reponse("SANS_OBJET", t.statut(), null);
        }
        try {
            Ticket apres = transitions.execute(new TransitionTicketUseCase.Command(d.workspaceId(), ticketId,
                    d.cible(), d.raison(), d.acteurId()));
            return new Reponse("APPLIQUEE", apres.statut(), null);
        } catch (ConflictException | ValidationException ex) {
            return new Reponse("REFUSEE", t.statut(), ex.getMessage());
        }
    }
}

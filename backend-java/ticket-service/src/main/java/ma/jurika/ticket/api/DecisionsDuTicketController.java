package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.referentiel.DemarchesDuTicket;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot L4b (E4, RG-DEM-01) : les decisions d'un ticket hors creation. Elles choisissent
 * les lignes du referentiel du corpus (demarches du ticket).
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/decisions")
public class DecisionsDuTicketController {

    private final DemarchesDuTicket corpus;
    private final TicketRepository tickets;

    public DecisionsDuTicketController(DemarchesDuTicket corpus, TicketRepository tickets) {
        this.corpus = corpus;
        this.tickets = tickets;
    }

    public record Choix(List<DemarchesDuTicket.Choix> decisions) {}

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public DemarchesDuTicket.Decisions lire(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID ticketId) {
        return corpus.decisions(ticket(user, ticketId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
    @Transactional
    public DemarchesDuTicket.Decisions choisir(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID ticketId, @RequestBody Choix body) {
        Ticket t = ticket(user, ticketId);
        corpus.choisir(t, body == null || body.decisions() == null ? List.of() : body.decisions(), user.userId());
        return corpus.decisions(t);
    }

    private Ticket ticket(AuthenticatedUser user, UUID ticketId) {
        TenantContext.set(user.workspaceId());
        return tickets.findById(user.workspaceId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
    }
}

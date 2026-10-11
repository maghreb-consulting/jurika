package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.EmployeEnChargeTicket;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot L4d (E9, RG-TKT-06) : ce qui empeche le ticket de passer au statut suivant du parcours,
 * AVANT que l'employe ne le demande. Les obstacles sont ceux-la memes que la transition
 * opposerait (meme {@link TransitionChecks}) : l'ecran dit ce que le serveur refusera.
 * Lecture : employe en charge et superviseur ; un autre employe recoit 404 (RG-DOS-01).
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/passage")
public class PassageStatutController {

    /**
     * @param suivant  statut suivant du parcours nominal, ou null (clos, annule)
     * @param obstacles obstacles nommes au passage vers {@code suivant} (vide : passage possible)
     */
    public record Passage(TicketStatut statut, TicketStatut suivant, List<String> obstacles) {}

    private final TicketRepository tickets;
    private final TransitionChecks checks;
    private final EmployeEnChargeTicket enCharge;

    public PassageStatutController(TicketRepository tickets, TransitionChecks checks, EmployeEnChargeTicket enCharge) {
        this.tickets = tickets;
        this.checks = checks;
        this.enCharge = enCharge;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public Passage passage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID ticketId) {
        TenantContext.set(user.workspaceId());
        Ticket t = tickets.findById(user.workspaceId(), ticketId).orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (user.role() == Role.EMPLOYE) enCharge.exiger(user.workspaceId(), t, user.userId());
        TicketStatut suivant = suivant(t.statut());
        if (suivant == null) return new Passage(t.statut(), null, List.of());
        return new Passage(t.statut(), suivant, checks.obstacles(t, suivant));
    }

    static TicketStatut suivant(TicketStatut s) {
        List<TicketStatut> parcours = TicketStatut.parcours();
        int i = parcours.indexOf(s);
        return i < 0 || i == parcours.size() - 1 ? null : parcours.get(i + 1);
    }
}

package ma.jurika.ticket.api;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.EmployeEnChargeTicket;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot L4d (E2, RG-DOS-01, RG-TKT-11) : ce que l'utilisateur courant peut faire sur un
 * ticket. L'ecran n'offre une action qu'a l'employe en charge, sur un ticket ni clos ni
 * annule ; le superviseur observe. Le serveur reste juge : chaque geste se recontrole.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/acces")
public class AccesTicketController {

    public record Acces(boolean enCharge, boolean peutAgir, String raison) {}

    private final TicketRepository tickets;
    private final EmployeEnChargeTicket enCharge;

    public AccesTicketController(TicketRepository tickets, EmployeEnChargeTicket enCharge) {
        this.tickets = tickets;
        this.enCharge = enCharge;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public Acces acces(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID ticketId) {
        TenantContext.set(user.workspaceId());
        Ticket t = tickets.findById(user.workspaceId(), ticketId).orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (user.role() != Role.EMPLOYE) {
            return new Acces(false, false, "Consultation : seul l'employé responsable du dossier agit sur le ticket.");
        }
        boolean responsable = enCharge.estEnCharge(user.workspaceId(), t, user.userId());
        if (!responsable) {
            return new Acces(false, false, "Ce ticket relève d'un dossier dont vous n'êtes pas le responsable.");
        }
        if (t.statut() == TicketStatut.CLOTURE_DOSSIER) {
            return new Acces(true, false, "Ticket clos : lecture seule.");
        }
        return new Acces(true, true, null);
    }
}

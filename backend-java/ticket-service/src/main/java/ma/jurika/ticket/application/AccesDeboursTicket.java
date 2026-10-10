package ma.jurika.ticket.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.PermissionsClientLookup;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lot L1, etape E11 : acces a l'etat des debours d'un ticket.
 * <ul>
 *   <li>lecture : superviseur (observation) ; employe responsable du dossier
 *       (RG-DOS-01, a defaut de dossier l'assigne ou le createur) ; client du dossier
 *       si sa permission de consultation le prevoit (RG-DEB-03, RG-CLI-01) ;</li>
 *   <li>ecriture : employe responsable seulement.</li>
 * </ul>
 * Ticket d'un autre cabinet, ou d'un dossier qui n'est pas celui de l'acteur : 404.
 */
@Component
public class AccesDeboursTicket {

    private final TicketRepository tickets;
    private final DossierRepository dossiers;
    private final PermissionsClientLookup permissions;

    public AccesDeboursTicket(TicketRepository tickets, DossierRepository dossiers,
                              PermissionsClientLookup permissions) {
        this.tickets = tickets;
        this.dossiers = dossiers;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public Ticket lecture(AuthenticatedUser actor, UUID ticketId) {
        Ticket t = ticket(actor, ticketId);
        switch (actor.role()) {
            case SUPERVISEUR -> { }
            case EMPLOYE -> exigerEnCharge(actor, t);
            case CLIENT -> {
                EntrepriseDossier d = dossier(actor, t);
                if (d == null || !actor.userId().equals(d.clientId())) {
                    throw new NotFoundException("Ticket inconnu");
                }
                if (!permissions.consultationPermise(actor.workspaceId(), d.id())) {
                    throw new AccessDeniedException(
                            "La consultation des documents n'est pas autorisee pour votre acces.");
                }
            }
            default -> throw new AccessDeniedException("Acces refuse");
        }
        return t;
    }

    @Transactional(readOnly = true)
    public Ticket ecriture(AuthenticatedUser actor, UUID ticketId) {
        if (actor.role() != Role.EMPLOYE) {
            throw new AccessDeniedException("Seul l'employe en charge modifie les debours");
        }
        Ticket t = ticket(actor, ticketId);
        exigerEnCharge(actor, t);
        return t;
    }

    private Ticket ticket(AuthenticatedUser actor, UUID ticketId) {
        TenantContext.set(actor.workspaceId());
        return tickets.findById(actor.workspaceId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
    }

    private EntrepriseDossier dossier(AuthenticatedUser actor, Ticket t) {
        return t.dossierId() == null ? null : dossiers.findById(actor.workspaceId(), t.dossierId()).orElse(null);
    }

    private void exigerEnCharge(AuthenticatedUser actor, Ticket t) {
        EntrepriseDossier d = dossier(actor, t);
        boolean enCharge = d != null
                ? actor.userId().equals(d.responsableId())
                : actor.userId().equals(t.assigneId()) || actor.userId().equals(t.creeParId());
        if (!enCharge) {
            throw new NotFoundException("Ticket inconnu");
        }
    }
}

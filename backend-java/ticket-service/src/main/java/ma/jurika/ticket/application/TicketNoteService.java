package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketNote;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketNoteRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L1 : note de ticket (RG-TKT-07).
 * <ul>
 *   <li>ouverte des la creation du ticket : sans enregistrement, la note est vide ;</li>
 *   <li>validable meme vide ; modifiable par l'employe en charge (responsable du
 *       dossier, a defaut de dossier l'assigne ou le createur du ticket) ;</li>
 *   <li>lisible par le superviseur, en observation ;</li>
 *   <li>STRICTEMENT INTERNE : aucun acces client (garde du controleur) ;</li>
 *   <li>ticket clos : lecture seule (RG-TKT-11).</li>
 * </ul>
 */
@Service
public class TicketNoteService {

    private final TicketRepository tickets;
    private final DossierRepository dossiers;
    private final TicketNoteRepository notes;
    private final AuditEventEmitter audit;

    public TicketNoteService(TicketRepository tickets, DossierRepository dossiers,
                             TicketNoteRepository notes, AuditEventEmitter audit) {
        this.tickets = tickets;
        this.dossiers = dossiers;
        this.notes = notes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TicketNote lire(UUID workspaceId, UUID actorId, boolean superviseur, UUID ticketId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        if (!superviseur) {
            exigerEnCharge(workspaceId, actorId, ticket);
        }
        return notes.find(workspaceId, ticketId).orElse(new TicketNote(ticketId, "", null, null));
    }

    @Transactional
    public TicketNote enregistrer(UUID workspaceId, UUID actorId, UUID ticketId, String contenu) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        exigerEnCharge(workspaceId, actorId, ticket);
        if (ticket.statut() == TicketStatut.CLOTURE_DOSSIER) {
            throw new ConflictException("Ticket clos : la note est en lecture seule");
        }
        TicketNote note = notes.save(workspaceId, ticketId, contenu == null ? "" : contenu, actorId);
        audit.emit(workspaceId, actorId, "TICKET_NOTE_MODIFIEE", "ticket", ticketId,
                Map.of("longueur", note.contenu().length()));
        return note;
    }

    private Ticket charger(UUID workspaceId, UUID ticketId) {
        return tickets.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
    }

    /** Employe en charge : responsable du dossier ; sans dossier, assigne ou createur. 404 sinon. */
    private void exigerEnCharge(UUID workspaceId, UUID actorId, Ticket ticket) {
        UUID responsable = ticket.dossierId() == null ? null
                : dossiers.findById(workspaceId, ticket.dossierId())
                        .map(EntrepriseDossier::responsableId).orElse(null);
        boolean enCharge = responsable != null
                ? responsable.equals(actorId)
                : actorId.equals(ticket.assigneId()) || actorId.equals(ticket.creeParId());
        if (!enCharge) {
            throw new NotFoundException("Ticket inconnu");
        }
    }
}

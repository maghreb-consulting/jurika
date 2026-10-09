package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.port.DeboursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DeboursUseCase {

    private final DeboursRepository repository;
    /** Lot L0 (E23) : statut du ticket (lecture seule une fois clos, RG-TKT-11). */
    private final TicketRepository tickets;

    public DeboursUseCase(DeboursRepository repository, TicketRepository tickets) {
        this.repository = repository;
        this.tickets = tickets;
    }

    public record CreateCommand(UUID workspaceId, UUID ticketId, String libelle, DeboursCategorie categorie,
                                 BigDecimal montant, LocalDate dateEngagement, String pieceJointeUrl,
                                 String filename, String notes, UUID createdById) {}

    public record UpdateCommand(UUID workspaceId, UUID deboursId, String libelle, DeboursCategorie categorie,
                                 BigDecimal montant, LocalDate dateEngagement, String notes) {}

    public record Summary(List<Debours> items, BigDecimal total) {}

    @Transactional
    public Debours create(CreateCommand cmd) {
        TenantContext.set(cmd.workspaceId());
        assertTicketOuvert(cmd.workspaceId(), cmd.ticketId());
        return repository.create(cmd.workspaceId(), cmd.ticketId(), cmd.libelle(),
                cmd.categorie(), cmd.montant(), cmd.dateEngagement(),
                cmd.pieceJointeUrl(), cmd.filename(), cmd.notes(), cmd.createdById());
    }

    @Transactional
    public Debours update(UpdateCommand cmd) {
        TenantContext.set(cmd.workspaceId());
        Debours existant = repository.findById(cmd.workspaceId(), cmd.deboursId())
                .orElseThrow(() -> new NotFoundException("Debours inconnu"));
        assertTicketOuvert(cmd.workspaceId(), existant.ticketId());
        return repository.update(cmd.deboursId(), cmd.libelle(), cmd.categorie(),
                cmd.montant(), cmd.dateEngagement(), cmd.notes());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        TenantContext.set(workspaceId);
        repository.findById(workspaceId, id)
                .ifPresent(d -> assertTicketOuvert(workspaceId, d.ticketId()));
        repository.delete(workspaceId, id);
    }

    /**
     * Lot L0 (E23, RG-TKT-11) : un ticket clos s'ouvre en lecture seule, sans
     * aucune action possible ; garde cote serveur. Ticket illisible dans le
     * workspace : 404.
     */
    private void assertTicketOuvert(UUID workspaceId, UUID ticketId) {
        Ticket t = tickets.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (t.statut() == TicketStatut.CLOTURE_DOSSIER) {
            throw new ConflictException("TICKET_CLOS_LECTURE_SEULE : le ticket est clos, "
                    + "ses debours ne peuvent plus etre modifies (RG-TKT-11).");
        }
    }

    @Transactional(readOnly = true)
    public Summary listForTicket(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        List<Debours> items = repository.findByTicket(workspaceId, ticketId);
        BigDecimal total = items.stream().map(Debours::montantMad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(items, total);
    }
}

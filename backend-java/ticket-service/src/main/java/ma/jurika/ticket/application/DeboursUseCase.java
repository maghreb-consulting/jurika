package ma.jurika.ticket.application;

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

    public DeboursUseCase(DeboursRepository repository) {
        this.repository = repository;
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
        return repository.create(cmd.workspaceId(), cmd.ticketId(), cmd.libelle(),
                cmd.categorie(), cmd.montant(), cmd.dateEngagement(),
                cmd.pieceJointeUrl(), cmd.filename(), cmd.notes(), cmd.createdById());
    }

    @Transactional
    public Debours update(UpdateCommand cmd) {
        TenantContext.set(cmd.workspaceId());
        repository.findById(cmd.workspaceId(), cmd.deboursId())
                .orElseThrow(() -> new NotFoundException("Debours inconnu"));
        return repository.update(cmd.deboursId(), cmd.libelle(), cmd.categorie(),
                cmd.montant(), cmd.dateEngagement(), cmd.notes());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        TenantContext.set(workspaceId);
        repository.delete(workspaceId, id);
    }

    @Transactional(readOnly = true)
    public Summary listForTicket(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        List<Debours> items = repository.findByTicket(workspaceId, ticketId);
        BigDecimal total = items.stream().map(Debours::montantMad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(items, total);
    }
}

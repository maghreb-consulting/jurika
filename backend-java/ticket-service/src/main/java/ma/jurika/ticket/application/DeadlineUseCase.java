package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Deadline;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.DeadlineSeverity;
import ma.jurika.ticket.domain.model.DeadlineSource;
import ma.jurika.ticket.domain.model.DeadlineStatut;
import ma.jurika.ticket.domain.port.DeadlineRepository;
import ma.jurika.ticket.domain.service.DeadlineComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrateur des deadlines : creation auto (Killer Feature §4.2),
 * creation manuelle, listing, transitions (TERMINEE / IGNOREE).
 */
@Service
public class DeadlineUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeadlineUseCase.class);

    private final DeadlineRepository repository;
    private final DeadlineComputer computer;
    private final Clock clock;

    public DeadlineUseCase(DeadlineRepository repository, DeadlineComputer computer, Clock clock) {
        this.repository = repository;
        this.computer = computer;
        this.clock = clock;
    }

    public record AutoComputeCommand(UUID workspaceId, UUID ticketId, UUID dossierId,
                                      DeadlineRule rule, Instant anchor, UUID creeParId,
                                      Map<String, Object> metadata) {}

    public record ManualCreateCommand(UUID workspaceId, UUID ticketId, UUID dossierId,
                                       String title, String description, Instant dueAt,
                                       DeadlineSeverity severity, UUID assigneId, UUID creeParId) {}

    /**
     * Cree (ou retourne l'existante) une deadline AUTO pour ce (ticket, regle).
     * Idempotent : si une deadline AUTO existe deja pour ce (ticket, regle), elle est mise a jour.
     */
    @Transactional
    public Deadline computeAuto(AutoComputeCommand cmd) {
        TenantContext.set(cmd.workspaceId());
        var existing = cmd.ticketId() == null ? null
                : repository.findAutoByTicketAndRule(cmd.workspaceId(), cmd.ticketId(), cmd.rule()).orElse(null);
        Deadline fresh = computer.build(cmd.workspaceId(), cmd.ticketId(), cmd.dossierId(),
                cmd.rule(), cmd.anchor(), cmd.creeParId(), cmd.metadata());
        if (existing != null) {
            // On rafraichit la date + severite mais on conserve l'historique (id, createdAt, statut s'il etait deja TERMINEE/IGNOREE)
            if (existing.statut() != DeadlineStatut.OUVERTE) {
                log.debug("Deadline {} deja {}: pas de recompute", existing.id(), existing.statut());
                return existing;
            }
            Deadline updated = new Deadline(
                    existing.id(),
                    existing.workspaceId(),
                    existing.ticketId(),
                    existing.dossierId(),
                    fresh.title(),
                    fresh.description(),
                    fresh.dueAt(),
                    fresh.severity(),
                    DeadlineSource.AUTO,
                    fresh.rule(),
                    DeadlineStatut.OUVERTE,
                    existing.assigneId(),
                    existing.creeParId(),
                    null, null,
                    fresh.metadata(),
                    existing.createdAt(),
                    Instant.now(clock));
            return repository.save(updated);
        }
        return repository.save(fresh);
    }

    @Transactional
    public Deadline createManual(ManualCreateCommand cmd) {
        TenantContext.set(cmd.workspaceId());
        Instant now = Instant.now(clock);
        Deadline d = new Deadline(
                null,
                cmd.workspaceId(),
                cmd.ticketId(),
                cmd.dossierId(),
                cmd.title(),
                cmd.description(),
                cmd.dueAt(),
                cmd.severity() == null ? DeadlineSeverity.INFO : cmd.severity(),
                DeadlineSource.MANUAL,
                null,
                DeadlineStatut.OUVERTE,
                cmd.assigneId(),
                cmd.creeParId(),
                null, null,
                null,
                now, now);
        return repository.save(d);
    }

    @Transactional
    public Deadline markCompleted(UUID workspaceId, UUID deadlineId) {
        TenantContext.set(workspaceId);
        Deadline d = repository.findById(workspaceId, deadlineId)
                .orElseThrow(() -> new NotFoundException("Deadline inconnue"));
        if (d.statut() != DeadlineStatut.OUVERTE) {
            throw new ConflictException("Deadline deja " + d.statut().name().toLowerCase());
        }
        return repository.save(d.asCompleted(Instant.now(clock)));
    }

    @Transactional
    public Deadline dismiss(UUID workspaceId, UUID deadlineId) {
        TenantContext.set(workspaceId);
        Deadline d = repository.findById(workspaceId, deadlineId)
                .orElseThrow(() -> new NotFoundException("Deadline inconnue"));
        if (d.statut() != DeadlineStatut.OUVERTE) {
            throw new ConflictException("Deadline deja " + d.statut().name().toLowerCase());
        }
        return repository.save(d.asDismissed(Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<Deadline> list(UUID workspaceId, DeadlineStatut statut, Instant from, Instant to, int limit) {
        TenantContext.set(workspaceId);
        Instant rangeFrom = from == null ? Instant.now(clock).minusSeconds(86400L * 365) : from;
        Instant rangeTo = to == null ? Instant.now(clock).plusSeconds(86400L * 365) : to;
        return repository.findByWorkspace(workspaceId, statut, rangeFrom, rangeTo, limit);
    }

    @Transactional(readOnly = true)
    public List<Deadline> listForTicket(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        return repository.findByTicket(workspaceId, ticketId);
    }

    @Transactional(readOnly = true)
    public List<Deadline> listOverdue(UUID workspaceId, int limit) {
        TenantContext.set(workspaceId);
        return repository.findOverdue(workspaceId, Instant.now(clock), limit);
    }

    @Transactional(readOnly = true)
    public long countOpen(UUID workspaceId) {
        TenantContext.set(workspaceId);
        return repository.countOpen(workspaceId);
    }
}

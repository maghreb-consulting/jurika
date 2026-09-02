package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.Deadline;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.DeadlineSeverity;
import ma.jurika.ticket.domain.model.DeadlineSource;
import ma.jurika.ticket.domain.model.DeadlineStatut;
import ma.jurika.ticket.domain.port.DeadlineRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeadlineRepositoryAdapter implements DeadlineRepository {

    private final DeadlineJpaRepository jpa;

    public DeadlineRepositoryAdapter(DeadlineJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Deadline save(Deadline d) {
        DeadlineEntity e = (d.id() != null)
                ? jpa.findById(d.id()).orElseGet(DeadlineEntity::new)
                : new DeadlineEntity();
        e.setWorkspaceId(d.workspaceId());
        e.setTicketId(d.ticketId());
        e.setDossierId(d.dossierId());
        e.setTitle(d.title());
        e.setDescription(d.description());
        e.setDueAt(d.dueAt());
        e.setSeverity(d.severity().name());
        e.setSource(d.source().name());
        e.setRuleKey(d.rule() == null ? null : d.rule().name());
        e.setStatut(d.statut().name());
        e.setAssigneId(d.assigneId());
        e.setCreeParId(d.creeParId());
        e.setTermineeAt(d.termineeAt());
        e.setIgnoreeAt(d.ignoreeAt());
        e.setMetadata(d.metadata());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Deadline> findById(UUID workspaceId, UUID id) {
        return jpa.findById(id)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .map(this::toDomain);
    }

    @Override
    public Optional<Deadline> findAutoByTicketAndRule(UUID workspaceId, UUID ticketId, DeadlineRule rule) {
        return jpa.findAutoByTicketAndRule(workspaceId, ticketId, rule.name()).map(this::toDomain);
    }

    @Override
    public List<Deadline> findByWorkspace(UUID workspaceId, DeadlineStatut statut, Instant from, Instant to, int limit) {
        return jpa.findByWorkspaceAndStatut(workspaceId,
                        statut == null ? DeadlineStatut.OUVERTE.name() : statut.name(),
                        from, to, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Deadline> findByTicket(UUID workspaceId, UUID ticketId) {
        return jpa.findByWorkspaceIdAndTicketIdOrderByDueAtAsc(workspaceId, ticketId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Deadline> findByDossier(UUID workspaceId, UUID dossierId) {
        return jpa.findByWorkspaceIdAndDossierIdOrderByDueAtAsc(workspaceId, dossierId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Deadline> findOverdue(UUID workspaceId, Instant now, int limit) {
        return jpa.findOverdue(workspaceId, now, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countOpen(UUID workspaceId) {
        return jpa.countOpen(workspaceId);
    }

    private Deadline toDomain(DeadlineEntity e) {
        return new Deadline(
                e.getId(),
                e.getWorkspaceId(),
                e.getTicketId(),
                e.getDossierId(),
                e.getTitle(),
                e.getDescription(),
                e.getDueAt(),
                DeadlineSeverity.valueOf(e.getSeverity()),
                DeadlineSource.valueOf(e.getSource()),
                e.getRuleKey() == null ? null : DeadlineRule.valueOf(e.getRuleKey()),
                DeadlineStatut.valueOf(e.getStatut()),
                e.getAssigneId(),
                e.getCreeParId(),
                e.getTermineeAt(),
                e.getIgnoreeAt(),
                e.getMetadata(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}

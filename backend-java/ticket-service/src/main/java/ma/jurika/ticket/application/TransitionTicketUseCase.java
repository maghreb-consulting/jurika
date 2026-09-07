package ma.jurika.ticket.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketComment;
import ma.jurika.ticket.domain.model.TicketCommentType;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.state.TicketStateMachine;
import ma.jurika.ticket.domain.state.TicketStatutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TransitionTicketUseCase {

    private final TicketRepository ticketRepository;
    private final TicketStateMachine stateMachine;
    private final CommentRepository commentRepository;
    private final TicketEventPublisher eventPublisher;
    private final TicketDeadlineHooks deadlineHooks;
    private final BusinessMetrics businessMetrics;

    public TransitionTicketUseCase(TicketRepository ticketRepository, TicketStateMachine stateMachine,
                                    CommentRepository commentRepository, TicketEventPublisher eventPublisher,
                                    TicketDeadlineHooks deadlineHooks,
                                    BusinessMetrics businessMetrics) {
        this.ticketRepository = ticketRepository;
        this.stateMachine = stateMachine;
        this.commentRepository = commentRepository;
        this.eventPublisher = eventPublisher;
        this.deadlineHooks = deadlineHooks;
        this.businessMetrics = businessMetrics;
    }

    public record Command(UUID workspaceId, UUID ticketId, TicketStatut target, String comment, UUID actorId) {}

    @Transactional
    @Auditable(action = "TICKET_TRANSITIONED", resourceType = "ticket", resourceIdExpr = "#cmd.ticketId()")
    public Ticket execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());
        Ticket current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));

        // Fix 2026-06-07 (BUG 2) — Idempotence : double-annulation no-op.
        // Si le ticket est deja dans le statut cible (typiquement ANNULE
        // reemis par un retry front), on retourne tel quel SANS rejouer
        // les hooks/events/audit. Empeche aussi un 409 ConflictException
        // remonte par AnnuleHandler (qui refuse "Ticket deja annule").
        if (current.statut() == cmd.target()) {
            return current;
        }

        // Auto-assignation a l'acteur au demarrage de la production (statut 2
        // « Generation des documents ») si le ticket n'a pas encore de responsable.
        if (cmd.target() == TicketStatut.GENERATION_DOCUMENTS && current.assigneId() == null) {
            ticketRepository.updateAssignment(current.id(), cmd.actorId(),
                    current.priorite(), current.deadline(), current.titre(), current.description());
            current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                    .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        }

        stateMachine.assertCanTransition(current, cmd.target(),
                new TicketStatutHandler.TransitionContext(cmd.comment(), cmd.actorId()));

        TicketStatut previous = current.statut();
        Instant now = Instant.now();
        Instant clotureAt = cmd.target() == TicketStatut.CLOTURE_DOSSIER ? now : current.clotureAt();
        Instant annuleAt = cmd.target() == TicketStatut.ANNULE ? now : current.annuleAt();
        String motif = cmd.target() == TicketStatut.ANNULE ? cmd.comment() : current.annulationMotif();

        Ticket updated = ticketRepository.updateStatut(current.id(), cmd.target(), motif, clotureAt, annuleAt);

        TicketCommentType commentType = switch (cmd.target()) {
            case ANNULE -> TicketCommentType.ANNULATION;
            case CLOTURE_DOSSIER -> TicketCommentType.CLOTURE;
            default -> TicketCommentType.TRANSITION_STATUT;
        };
        String contenu = cmd.comment() == null || cmd.comment().isBlank()
                ? "Transition vers " + cmd.target()
                : cmd.comment();
        commentRepository.create(cmd.workspaceId(), current.id(), cmd.actorId(),
                commentType, contenu, Map.of("from", previous.name(), "to", cmd.target().name()));

        eventPublisher.publishTicketStatusChanged(updated, previous);
        deadlineHooks.onTransition(updated, previous, cmd.target());
        businessMetrics.ticketStateTransition(previous.name(), cmd.target().name(), cmd.workspaceId());
        return updated;
    }
}

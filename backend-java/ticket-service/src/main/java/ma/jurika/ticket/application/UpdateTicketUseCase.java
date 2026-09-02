package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateTicketUseCase {

    private final TicketRepository ticketRepository;
    private final TicketEventPublisher eventPublisher;
    private final AuditEventEmitter auditEmitter;
    private final NotificationPublisher notificationPublisher;

    public UpdateTicketUseCase(TicketRepository ticketRepository, TicketEventPublisher eventPublisher,
                               AuditEventEmitter auditEmitter, NotificationPublisher notificationPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.auditEmitter = auditEmitter;
        this.notificationPublisher = notificationPublisher;
    }

    public record Command(UUID workspaceId, UUID ticketId, String titre, String description,
                           TicketPriorite priorite, UUID assigneId, Instant deadline, UUID actorId) {}

    @Transactional
    public Ticket execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());
        Ticket current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        UUID previousAssignee = current.assigneId();
        // 2026-06-24 — ASSIGNATION != PRISE EN CHARGE. Un superviseur peut assigner
        // un ticket a un employe sans le faire passer EN_COURS : ce updateAssignment
        // ne touche JAMAIS le statut (pas d'appel updateStatut). Le ticket reste
        // NOUVEAU jusqu'a la prise en charge explicite (transition EN_COURS) ou la
        // validation de l'etape 1 du workflow.
        Ticket updated = ticketRepository.updateAssignment(current.id(),
                cmd.assigneId(), cmd.priorite(), cmd.deadline(), cmd.titre(), cmd.description());
        if (!java.util.Objects.equals(previousAssignee, cmd.assigneId())) {
            eventPublisher.publishTicketAssigned(updated, previousAssignee);
            // E2 tracabilite — l'assignation via PATCH n'etait pas auditee. On
            // ecrit TICKET_ASSIGNED UNIQUEMENT quand l'assigne change reellement
            // (pas sur une simple edition titre/priorite), avec ancien -> nouveau.
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("ancienAssigne", previousAssignee == null ? null : previousAssignee.toString());
            meta.put("nouvelAssigne", cmd.assigneId() == null ? null : cmd.assigneId().toString());
            auditEmitter.emit(cmd.workspaceId(), cmd.actorId(),
                    "TICKET_ASSIGNED", "ticket", cmd.ticketId(), meta);
            // Notification persistante (cloche) au NOUVEL assigne — jamais a l'acteur
            // lui-meme (un employe qui s'auto-assigne n'a pas besoin d'etre notifie).
            if (cmd.assigneId() != null && !cmd.assigneId().equals(cmd.actorId())) {
                Map<String, Object> notifMeta = new LinkedHashMap<>();
                notifMeta.put("ticketId", cmd.ticketId().toString());
                notifMeta.put("reference", updated.reference());
                notificationPublisher.notifyUser(cmd.assigneId(), cmd.workspaceId(), "TICKET_ASSIGNED",
                        "Nouveau ticket assigne",
                        "Le ticket " + updated.reference() + " vous a ete assigne.",
                        "/tickets", notifMeta);
            }
        }
        // 2026-06-25 — "Journaliser TOUTES les actions du ticket" : l'edition des
        // champs (titre / description / priorite / echeance) etait silencieuse cote
        // audit_log. On ecrit TICKET_UPDATED avec la liste des champs reellement
        // modifies (independamment du changement d'assigne, deja trace ci-dessus).
        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(current.titre(), cmd.titre())) changed.add("titre");
        if (!java.util.Objects.equals(current.description(), cmd.description())) changed.add("description");
        if (!java.util.Objects.equals(current.priorite(), cmd.priorite())) changed.add("priorite");
        if (!java.util.Objects.equals(current.deadline(), cmd.deadline())) changed.add("deadline");
        if (!changed.isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("champs", changed);
            auditEmitter.emit(cmd.workspaceId(), cmd.actorId(),
                    "TICKET_UPDATED", "ticket", cmd.ticketId(), meta);
        }
        return updated;
    }
}

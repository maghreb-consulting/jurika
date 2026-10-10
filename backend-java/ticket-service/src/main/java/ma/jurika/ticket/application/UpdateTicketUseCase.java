package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.port.DossierRepository;
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
    private final DossierRepository dossierRepository;
    private final AuditEventEmitter auditEmitter;

    public UpdateTicketUseCase(TicketRepository ticketRepository, DossierRepository dossierRepository,
                               AuditEventEmitter auditEmitter) {
        this.ticketRepository = ticketRepository;
        this.dossierRepository = dossierRepository;
        this.auditEmitter = auditEmitter;
    }

    public record Command(UUID workspaceId, UUID ticketId, String titre, String description,
                           TicketPriorite priorite, UUID assigneId, Instant deadline, UUID actorId) {}

    @Transactional
    public Ticket execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());
        Ticket current = ticketRepository.findById(cmd.workspaceId(), cmd.ticketId())
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        // Lot L1 (RG-DOS-01) : seul l'employe responsable du dossier edite le ticket ;
        // a defaut de dossier (ticket SUCCURSALE_ETR avant l'etape 1), son assigne ou
        // son createur. Tout autre employe : 404, comme a la lecture.
        UUID responsable = current.dossierId() == null ? null
                : dossierRepository.findById(cmd.workspaceId(), current.dossierId())
                        .map(EntrepriseDossier::responsableId).orElse(null);
        boolean autorise = responsable != null
                ? responsable.equals(cmd.actorId())
                : cmd.actorId().equals(current.assigneId()) || cmd.actorId().equals(current.creeParId());
        if (!autorise) {
            throw new NotFoundException("Ticket inconnu");
        }
        // Lot L1 (RG-TKT-08, RG-DOS-02) : les tickets suivent leur dossier. Un
        // changement d'assigne passe par le transfert du dossier (accepte par le
        // destinataire) ou la reaffectation d'office du superviseur, plus par PATCH.
        if (cmd.assigneId() != null && !cmd.assigneId().equals(current.assigneId())) {
            throw new ValidationException("Le responsable d'un ticket est celui de son dossier : "
                    + "transferer le dossier pour en changer");
        }
        Ticket updated = ticketRepository.updateAssignment(current.id(),
                null, cmd.priorite(), cmd.deadline(), cmd.titre(), cmd.description());
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

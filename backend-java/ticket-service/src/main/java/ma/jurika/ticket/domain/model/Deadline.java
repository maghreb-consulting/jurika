package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Echeance metier rattachee a un ticket et/ou un dossier d'entreprise.
 * <p>
 * Peut etre creee automatiquement par {@link DeadlineRule} ou manuellement par un Employe/Superviseur.
 */
public record Deadline(
        UUID id,
        UUID workspaceId,
        UUID ticketId,
        UUID dossierId,
        String title,
        String description,
        Instant dueAt,
        DeadlineSeverity severity,
        DeadlineSource source,
        DeadlineRule rule,
        DeadlineStatut statut,
        UUID assigneId,
        UUID creeParId,
        Instant termineeAt,
        Instant ignoreeAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean isOpen() {
        return statut == DeadlineStatut.OUVERTE;
    }

    public boolean isOverdue(Instant now) {
        return isOpen() && dueAt.isBefore(now);
    }

    public Deadline asCompleted(Instant when) {
        return new Deadline(id, workspaceId, ticketId, dossierId, title, description, dueAt,
                severity, source, rule, DeadlineStatut.TERMINEE, assigneId, creeParId,
                when, ignoreeAt, metadata, createdAt, when);
    }

    public Deadline asDismissed(Instant when) {
        return new Deadline(id, workspaceId, ticketId, dossierId, title, description, dueAt,
                severity, source, rule, DeadlineStatut.IGNOREE, assigneId, creeParId,
                termineeAt, when, metadata, createdAt, when);
    }
}

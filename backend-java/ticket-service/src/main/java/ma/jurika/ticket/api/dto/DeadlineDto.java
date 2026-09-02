package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.domain.model.Deadline;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeadlineDto(
        UUID id,
        UUID ticketId,
        UUID dossierId,
        String title,
        String description,
        Instant dueAt,
        String severity,
        String source,
        String ruleKey,
        String statut,
        UUID assigneId,
        UUID creeParId,
        Instant termineeAt,
        Instant ignoreeAt,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public static DeadlineDto from(Deadline d) {
        return new DeadlineDto(
                d.id(), d.ticketId(), d.dossierId(),
                d.title(), d.description(), d.dueAt(),
                d.severity().name(), d.source().name(),
                d.rule() == null ? null : d.rule().name(),
                d.statut().name(),
                d.assigneId(), d.creeParId(),
                d.termineeAt(), d.ignoreeAt(),
                d.metadata(), d.createdAt());
    }
}

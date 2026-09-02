package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.jurika.ticket.domain.model.DeadlineSeverity;

import java.time.Instant;
import java.util.UUID;

public record CreateDeadlineRequest(
        UUID ticketId,
        UUID dossierId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull Instant dueAt,
        DeadlineSeverity severity,
        UUID assigneId
) {}

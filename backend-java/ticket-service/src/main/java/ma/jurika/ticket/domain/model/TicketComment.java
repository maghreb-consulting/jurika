package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TicketComment(
        UUID id,
        UUID workspaceId,
        UUID ticketId,
        UUID auteurId,
        TicketCommentType type,
        String contenu,
        Map<String, Object> metadata,
        Instant createdAt
) {}

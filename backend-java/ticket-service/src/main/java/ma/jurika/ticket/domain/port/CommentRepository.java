package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.TicketComment;
import ma.jurika.ticket.domain.model.TicketCommentType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommentRepository {

    List<TicketComment> findByTicket(UUID workspaceId, UUID ticketId);

    TicketComment create(UUID workspaceId, UUID ticketId, UUID auteurId,
                          TicketCommentType type, String contenu, Map<String, Object> metadata);
}

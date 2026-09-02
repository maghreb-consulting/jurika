package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.TicketComment;
import ma.jurika.ticket.domain.model.TicketCommentType;
import ma.jurika.ticket.domain.port.CommentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class CommentRepositoryAdapter implements CommentRepository {

    private final CommentJpaRepository jpa;

    public CommentRepositoryAdapter(CommentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<TicketComment> findByTicket(UUID workspaceId, UUID ticketId) {
        return jpa.findByWorkspaceIdAndTicketIdOrderByCreatedAtDesc(workspaceId, ticketId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public TicketComment create(UUID workspaceId, UUID ticketId, UUID auteurId,
                                 TicketCommentType type, String contenu, Map<String, Object> metadata) {
        TicketCommentEntity e = new TicketCommentEntity();
        e.setWorkspaceId(workspaceId);
        e.setTicketId(ticketId);
        e.setAuteurId(auteurId);
        e.setType(type.name());
        e.setContenu(contenu);
        e.setMetadata(metadata);
        return toDomain(jpa.save(e));
    }

    private TicketComment toDomain(TicketCommentEntity e) {
        return new TicketComment(e.getId(), e.getWorkspaceId(), e.getTicketId(), e.getAuteurId(),
                TicketCommentType.valueOf(e.getType()), e.getContenu(), e.getMetadata(), e.getCreatedAt());
    }
}

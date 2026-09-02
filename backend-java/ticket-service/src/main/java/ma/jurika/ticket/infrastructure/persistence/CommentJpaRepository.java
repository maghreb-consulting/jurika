package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentJpaRepository extends JpaRepository<TicketCommentEntity, UUID> {
    List<TicketCommentEntity> findByWorkspaceIdAndTicketIdOrderByCreatedAtDesc(UUID workspaceId, UUID ticketId);
}

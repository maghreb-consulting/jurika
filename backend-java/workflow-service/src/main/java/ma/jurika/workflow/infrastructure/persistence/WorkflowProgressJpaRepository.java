package ma.jurika.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowProgressJpaRepository extends JpaRepository<WorkflowProgressEntity, UUID> {
    Optional<WorkflowProgressEntity> findByWorkspaceIdAndTicketId(UUID workspaceId, UUID ticketId);
}

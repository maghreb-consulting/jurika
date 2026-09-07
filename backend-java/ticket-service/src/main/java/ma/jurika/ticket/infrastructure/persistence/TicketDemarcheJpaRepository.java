package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketDemarcheJpaRepository extends JpaRepository<TicketDemarcheEntity, UUID> {

    List<TicketDemarcheEntity> findByWorkspaceIdAndTicketId(UUID workspaceId, UUID ticketId);

    Optional<TicketDemarcheEntity> findByWorkspaceIdAndTicketIdAndDemarcheId(
            UUID workspaceId, UUID ticketId, UUID demarcheId);
}

package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

    /** Lot L0 (E21, P9) : lecture filtree explicitement par workspace. */
    java.util.Optional<TicketEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}

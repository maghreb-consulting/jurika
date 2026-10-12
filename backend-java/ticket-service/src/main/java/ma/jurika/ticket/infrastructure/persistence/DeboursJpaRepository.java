package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeboursJpaRepository extends JpaRepository<DeboursEntity, UUID> {

    /** Lot L5 (E6) : ordre stable, chronologique (l'etat des debours est deterministe). */
    List<DeboursEntity> findByWorkspaceIdAndTicketIdOrderByDateEngagementAscCreatedAtAscIdAsc(UUID workspaceId,
                                                                                             UUID ticketId);
}

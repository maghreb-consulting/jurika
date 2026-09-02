package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DeboursJpaRepository extends JpaRepository<DeboursEntity, UUID> {

    List<DeboursEntity> findByWorkspaceIdAndTicketIdOrderByDateEngagementDesc(UUID workspaceId, UUID ticketId);

    @Query("SELECT COALESCE(SUM(d.montantMad), 0) FROM DeboursEntity d WHERE d.workspaceId = :ws AND d.ticketId = :tid")
    BigDecimal sumForTicket(@Param("ws") UUID workspaceId, @Param("tid") UUID ticketId);
}

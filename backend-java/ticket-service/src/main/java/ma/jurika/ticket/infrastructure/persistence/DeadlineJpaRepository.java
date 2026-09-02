package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadlineJpaRepository extends JpaRepository<DeadlineEntity, UUID> {

    @Query("SELECT d FROM DeadlineEntity d " +
            "WHERE d.workspaceId = :ws AND d.ticketId = :tid " +
            "  AND d.source = 'AUTO' AND d.ruleKey = :rule")
    Optional<DeadlineEntity> findAutoByTicketAndRule(@Param("ws") UUID ws,
                                                      @Param("tid") UUID ticketId,
                                                      @Param("rule") String ruleKey);

    @Query("SELECT d FROM DeadlineEntity d " +
            "WHERE d.workspaceId = :ws AND d.statut = :statut " +
            "  AND d.dueAt >= :from AND d.dueAt <= :to " +
            "ORDER BY d.dueAt ASC")
    List<DeadlineEntity> findByWorkspaceAndStatut(@Param("ws") UUID ws,
                                                    @Param("statut") String statut,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to,
                                                    Pageable pageable);

    List<DeadlineEntity> findByWorkspaceIdAndTicketIdOrderByDueAtAsc(UUID ws, UUID ticketId);

    List<DeadlineEntity> findByWorkspaceIdAndDossierIdOrderByDueAtAsc(UUID ws, UUID dossierId);

    @Query("SELECT d FROM DeadlineEntity d " +
            "WHERE d.workspaceId = :ws AND d.statut = 'OUVERTE' AND d.dueAt < :now " +
            "ORDER BY d.dueAt ASC")
    List<DeadlineEntity> findOverdue(@Param("ws") UUID ws, @Param("now") Instant now, Pageable pageable);

    @Query("SELECT COUNT(d) FROM DeadlineEntity d WHERE d.workspaceId = :ws AND d.statut = 'OUVERTE'")
    long countOpen(@Param("ws") UUID ws);
}

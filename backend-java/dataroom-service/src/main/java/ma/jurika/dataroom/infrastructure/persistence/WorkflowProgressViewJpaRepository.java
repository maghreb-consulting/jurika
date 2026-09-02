package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkflowProgressViewJpaRepository
        extends JpaRepository<WorkflowProgressViewEntity, UUID> {

    /**
     * Charge en une passe les workflows lies a un lot de tickets (workspace-scoped).
     * Le jsonb {@code data} est caste en texte pour un parsing applicatif.
     */
    @Query(value = """
        SELECT ticket_id     AS ticketId,
               workflow_type AS workflowType,
               statut        AS statut,
               completed_at  AS completedAt,
               data::text    AS dataJson
          FROM workflow_progress
         WHERE workspace_id = :ws
           AND ticket_id IN (:ticketIds)
        """, nativeQuery = true)
    List<WorkflowProgressRow> findRowsByWorkspaceAndTickets(@Param("ws") UUID ws,
                                                            @Param("ticketIds") Collection<UUID> ticketIds);
}

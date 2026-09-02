package ma.jurika.workflow.domain.port;

import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowProgressRepository {

    Optional<WorkflowProgress> findByTicket(UUID workspaceId, UUID ticketId);

    WorkflowProgress create(UUID workspaceId, UUID ticketId, WorkflowType type,
                             int totalSteps, UUID startedById);

    WorkflowProgress save(UUID id, int currentStep, Map<String, Object> data,
                           WorkflowStatut statut, Instant completedAt);
}

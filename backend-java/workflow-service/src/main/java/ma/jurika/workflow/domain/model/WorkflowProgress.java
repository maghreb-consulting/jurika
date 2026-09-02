package ma.jurika.workflow.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowProgress(
        UUID id,
        UUID workspaceId,
        UUID ticketId,
        WorkflowType type,
        int currentStep,
        int totalSteps,
        Map<String, Object> data,
        WorkflowStatut statut,
        UUID startedById,
        Instant completedAt,
        Instant updatedAt
) {}
